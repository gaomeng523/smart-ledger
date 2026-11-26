package com.smartledger.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartledger.ai.AiParseService;
import com.smartledger.ai.DeepSeekClient;
import com.smartledger.ai.ParseResultValidator;
import com.smartledger.ai.PromptBuilder;
import com.smartledger.ai.dto.DeepSeekResult;
import com.smartledger.ai.rag.RagExample;
import com.smartledger.ai.rag.RagService;
import com.smartledger.config.AiProperties;
import com.smartledger.dto.response.ParseResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 离线评测器（仅在 smartledger.eval.enabled=true 时启动）。
 *
 * <p>对同一份标注集跑三条链路并输出指标：本地规则、纯大模型、大模型 + RAG 检索。
 * 指标包含金额准确率、分类 Top-1、全字段通过率、平均/P95 延迟与 token 消耗。</p>
 *
 * <p>用法：java -jar app.jar --spring.profiles.active=prod \
 *   --spring.main.web-application-type=none --smartledger.eval.enabled=true ...</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "smartledger.eval", name = "enabled", havingValue = "true")
public class EvalRunner implements ApplicationRunner {

    private final AiParseService aiParseService;
    private final DeepSeekClient deepSeekClient;
    private final PromptBuilder promptBuilder;
    private final ParseResultValidator validator;
    private final RagService ragService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${smartledger.eval.dataset:classpath:ai/eval-test.jsonl}")
    private String datasetPath;

    @org.springframework.beans.factory.annotation.Value("${smartledger.eval.output:/tmp/eval-report.json}")
    private String outputPath;

    public EvalRunner(AiParseService aiParseService,
                      DeepSeekClient deepSeekClient,
                      PromptBuilder promptBuilder,
                      ParseResultValidator validator,
                      RagService ragService,
                      AiProperties aiProperties,
                      ObjectMapper objectMapper) {
        this.aiParseService = aiParseService;
        this.deepSeekClient = deepSeekClient;
        this.promptBuilder = promptBuilder;
        this.validator = validator;
        this.ragService = ragService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<EvalItem> dataset = loadDataset();
        log.info("评测集加载完成: {} 条, 模型 {}", dataset.size(), aiProperties.getModel());

        ModeReport rules = runRules(dataset);

        ModeReport ai = null;
        ModeReport rag = null;
        if (aiProperties.isEnabled()) {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<ModeReport> aiFuture = pool.submit(() -> runModel(dataset, false));
                Future<ModeReport> ragFuture = pool.submit(() -> runModel(dataset, true));
                ai = aiFuture.get();
                rag = ragFuture.get();
            } finally {
                pool.shutdownNow();
            }
        } else {
            log.warn("AI 未开启（smartledger.ai.enabled=false），跳过模型与 RAG 对照组");
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("model", aiProperties.getModel());
        report.put("datasetSize", dataset.size());
        report.put("ragBankSize", ragService.size());
        Map<String, Object> modes = new LinkedHashMap<>();
        modes.put("rules", rules);
        if (ai != null) {
            modes.put("ai", ai);
        }
        if (rag != null) {
            modes.put("rag", rag);
        }
        report.put("modes", modes);

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        Files.writeString(Path.of(outputPath), json, StandardCharsets.UTF_8);
        System.out.println(json);
        System.out.println("EVAL_REPORT=" + outputPath);
        System.exit(0);
    }

    private ModeReport runRules(List<EvalItem> dataset) {
        ModeReport report = new ModeReport("rules");
        for (EvalItem item : dataset) {
            long start = System.currentTimeMillis();
            try {
                ParseResultDTO dto = aiParseService.parseRulesOnly(item.text);
                report.add(item, dto, System.currentTimeMillis() - start, 0, 0);
            } catch (Exception e) {
                report.fail(item, e);
            }
        }
        report.finish();
        return report;
    }

    private ModeReport runModel(List<EvalItem> dataset, boolean useRag) {
        ModeReport report = new ModeReport(useRag ? "rag" : "ai");
        for (EvalItem item : dataset) {
            String prompt;
            try {
                List<RagExample> examples = useRag ? ragService.retrieve(item.text) : List.of();
                prompt = promptBuilder.buildSystemPrompt(examples);
            } catch (Exception e) {
                report.fail(item, e);
                continue;
            }
            long start = System.currentTimeMillis();
            try {
                DeepSeekResult result = deepSeekClient.chat(prompt, item.text);
                ParseResultDTO dto = validator.normalize(result.getRaw());
                report.add(item, dto, System.currentTimeMillis() - start,
                        value(result.getPromptTokens()), value(result.getCompletionTokens()));
            } catch (Exception e) {
                report.fail(item, e);
            }
        }
        report.finish();
        return report;
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private List<EvalItem> loadDataset() throws Exception {
        var resource = datasetPath.startsWith("classpath:")
                ? new ClassPathResource(datasetPath.substring("classpath:".length()))
                : new org.springframework.core.io.FileSystemResource(datasetPath);
        List<EvalItem> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(trimmed);
                EvalItem item = new EvalItem();
                item.text = node.path("text").asText("");
                item.amount = new BigDecimal(node.path("amount").asText("0"));
                item.categoryCode = node.path("categoryCode").asText("");
                if (!item.text.isEmpty() && !item.categoryCode.isEmpty()) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** 单条评测样本 */
    private static class EvalItem {
        private String text;
        private BigDecimal amount;
        private String categoryCode;
    }

    /** 单条链路的评测结果 */
    public static class ModeReport {
        private final String mode;
        private int total;
        private int failures;
        private int amountHit;
        private int categoryHit;
        private int fullHit;
        private long totalLatency;
        private long totalPromptTokens;
        private long totalCompletionTokens;
        private final List<Long> latencies = new ArrayList<>();
        private final List<Map<String, Object>> failureSamples = new ArrayList<>();

        private ModeReport(String mode) {
            this.mode = mode;
        }

        private void add(EvalItem item, ParseResultDTO dto, long latency, int promptTokens, int completionTokens) {
            total++;
            latencies.add(latency);
            totalLatency += latency;
            totalPromptTokens += promptTokens;
            totalCompletionTokens += completionTokens;

            boolean amountOk = dto.getAmount() != null && item.amount.compareTo(dto.getAmount()) == 0;
            boolean categoryOk = item.categoryCode.equals(dto.getCategoryCode());
            if (amountOk) {
                amountHit++;
            }
            if (categoryOk) {
                categoryHit++;
            }
            if (amountOk && categoryOk) {
                fullHit++;
            } else if (failureSamples.size() < 20) {
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("text", item.text);
                sample.put("expectAmount", item.amount);
                sample.put("gotAmount", dto.getAmount());
                sample.put("expectCategory", item.categoryCode);
                sample.put("gotCategory", dto.getCategoryCode());
                failureSamples.add(sample);
            }
        }

        private void fail(EvalItem item, Exception e) {
            total++;
            failures++;
            if (failureSamples.size() < 20) {
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("text", item.text);
                sample.put("error", e.getMessage());
                failureSamples.add(sample);
            }
        }

        private void finish() {
            latencies.sort(Long::compareTo);
        }

        @SuppressWarnings("unused")
        public String getMode() {
            return mode;
        }

        @SuppressWarnings("unused")
        public int getTotal() {
            return total;
        }

        @SuppressWarnings("unused")
        public int getFailures() {
            return failures;
        }

        @SuppressWarnings("unused")
        public double getAmountAccuracy() {
            return ratio(amountHit);
        }

        @SuppressWarnings("unused")
        public double getCategoryAccuracy() {
            return ratio(categoryHit);
        }

        @SuppressWarnings("unused")
        public double getFullPassRate() {
            return ratio(fullHit);
        }

        @SuppressWarnings("unused")
        public double getAvgLatencyMs() {
            return total == 0 ? 0 : round((double) totalLatency / total);
        }

        @SuppressWarnings("unused")
        public long getP95LatencyMs() {
            if (latencies.isEmpty()) {
                return 0;
            }
            int index = (int) Math.ceil(latencies.size() * 0.95) - 1;
            return latencies.get(Math.max(0, Math.min(index, latencies.size() - 1)));
        }

        @SuppressWarnings("unused")
        public long getTotalPromptTokens() {
            return totalPromptTokens;
        }

        @SuppressWarnings("unused")
        public long getTotalCompletionTokens() {
            return totalCompletionTokens;
        }

        @SuppressWarnings("unused")
        public List<Map<String, Object>> getFailureSamples() {
            return failureSamples;
        }

        private double ratio(int hit) {
            return total == 0 ? 0 : round((double) hit / total);
        }

        private double round(double value) {
            return Math.round(value * 10000) / 10000.0;
        }
    }
}
