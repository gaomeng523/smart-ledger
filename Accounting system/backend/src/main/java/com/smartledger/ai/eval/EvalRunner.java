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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 要跑的链路，逗号分隔：rules / ai / rag；多组时按顺序执行，避免并行抢占导致延迟失真 */
    @org.springframework.beans.factory.annotation.Value("${smartledger.eval.modes:rules,ai,rag}")
    private String modeList;

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
        List<String> selected = Arrays.stream(modeList.split(","))
                .map(String::trim)
                .filter(mode -> !mode.isEmpty())
                .collect(Collectors.toList());
        log.info("评测集加载完成: {} 条, 模型 {}, 模式 {}（顺序执行）",
                dataset.size(), aiProperties.getModel(), selected);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("model", aiProperties.getModel());
        report.put("datasetSize", dataset.size());
        report.put("execution", "sequential");

        Map<String, Object> modes = new LinkedHashMap<>();
        for (String mode : selected) {
            switch (mode) {
                case "rules" -> modes.put("rules", runRules(dataset));
                case "ai", "rag" -> {
                    if (!aiProperties.isEnabled()) {
                        log.warn("AI 未开启（smartledger.ai.enabled=false），跳过 {} 组", mode);
                        continue;
                    }
                    modes.put(mode, runModel(dataset, "rag".equals(mode)));
                }
                default -> log.warn("未知评测模式，已跳过: {}", mode);
            }
        }
        report.put("modes", modes);
        // 示例库为懒加载，跑完检索后再取规模才有值
        report.put("ragBankSize", ragService.size());

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
            long retrieveStart = System.currentTimeMillis();
            try {
                List<RagExample> examples = useRag ? ragService.retrieve(item.text) : List.of();
                if (useRag) {
                    report.addRetrieval(System.currentTimeMillis() - retrieveStart);
                }
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
        private long totalRetrieval;
        private final List<Long> latencies = new ArrayList<>();
        private final List<Long> retrievalLatencies = new ArrayList<>();
        private final List<Map<String, Object>> failureSamples = new ArrayList<>();

        private ModeReport(String mode) {
            this.mode = mode;
        }

        /** 记录单条样本的检索耗时（仅 RAG 链路） */
        private void addRetrieval(long retrievalMs) {
            retrievalLatencies.add(retrievalMs);
            totalRetrieval += retrievalMs;
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
            retrievalLatencies.sort(Long::compareTo);
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
            return p95(latencies);
        }

        /** 检索阶段平均耗时（不含模型调用） */
        @SuppressWarnings("unused")
        public double getRetrievalAvgMs() {
            return retrievalLatencies.isEmpty() ? 0 : round((double) totalRetrieval / retrievalLatencies.size());
        }

        @SuppressWarnings("unused")
        public long getRetrievalP95Ms() {
            return p95(retrievalLatencies);
        }

        /** 平均 prompt token：用于对比 few-shot 条数变化带来的 Prompt 体积 */
        @SuppressWarnings("unused")
        public double getAvgPromptTokens() {
            return total == 0 ? 0 : round((double) totalPromptTokens / total);
        }

        private long p95(List<Long> values) {
            if (values.isEmpty()) {
                return 0;
            }
            int index = (int) Math.ceil(values.size() * 0.95) - 1;
            return values.get(Math.max(0, Math.min(index, values.size() - 1)));
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
