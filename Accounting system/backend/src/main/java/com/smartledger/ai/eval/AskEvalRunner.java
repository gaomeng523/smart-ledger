package com.smartledger.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartledger.ai.ask.AnswerMatcher;
import com.smartledger.ai.ask.AskService;
import com.smartledger.common.util.DateUtils;
import com.smartledger.config.AiProperties;
import com.smartledger.dto.response.AskAnswerDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能问答离线评测器（仅在 smartledger.eval.ask-enabled=true 时启动）。
 *
 * <p>流程：写入固定种子账单 → 逐条提问 → 统计两个指标：
 * <ul>
 *   <li>执行准确率：SQL 通过安全校验并成功执行，且结果集中出现期望数值；</li>
 *   <li>答案准确率：最终中文回答里出现期望数值（容差 0.01）。</li>
 * </ul>
 * 同时输出平均耗时与 P95 延迟。
 *
 * <p>用法：java -jar app.jar --spring.profiles.active=prod \
 *   --spring.main.web-application-type=none --smartledger.eval.ask-enabled=true \
 *   --smartledger.ai.enabled=true --smartledger.ai.rate-limit-per-minute=0</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "smartledger.eval", name = "ask-enabled", havingValue = "true")
public class AskEvalRunner implements ApplicationRunner {

    private final AskService askService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    @Value("${smartledger.eval.ask-dataset:classpath:ai/ask-eval.jsonl}")
    private String datasetPath;

    @Value("${smartledger.eval.ask-seed:classpath:ai/ask-eval-seed.jsonl}")
    private String seedPath;

    @Value("${smartledger.eval.ask-output:/tmp/ask-eval-report.json}")
    private String outputPath;

    @Value("${smartledger.eval.ask-openid:eval_ask_user}")
    private String openid;

    @Value("${smartledger.eval.ask-seed-enabled:true}")
    private boolean seedEnabled;

    public AskEvalRunner(AskService askService,
                         JdbcTemplate jdbcTemplate,
                         ObjectMapper objectMapper,
                         AiProperties aiProperties) {
        this.askService = askService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int seeded = seedEnabled ? seedBills() : 0;
        List<AskItem> dataset = loadDataset();
        log.info("问答评测集加载完成: {} 条，种子账单 {} 条，模型 {}", dataset.size(), seeded, aiProperties.getModel());

        List<Long> latencies = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        int execHit = 0;
        int answerHit = 0;
        long totalLatency = 0;

        for (AskItem item : dataset) {
            long start = System.currentTimeMillis();
            try {
                AskAnswerDTO dto = askService.answer(openid, item.question);
                long latency = System.currentTimeMillis() - start;
                latencies.add(latency);
                totalLatency += latency;

                boolean execOk = containsValue(dto.getRows(), item.expect);
                boolean answerOk = AnswerMatcher.containsValue(dto.getAnswer(), item.expect);
                if (execOk) {
                    execHit++;
                }
                if (answerOk) {
                    answerHit++;
                }
                if (!execOk || !answerOk) {
                    addFailure(failures, item, dto, execOk, answerOk, null);
                }
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                latencies.add(latency);
                totalLatency += latency;
                addFailure(failures, item, null, false, false, e.getMessage());
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mode", "ask");
        report.put("model", aiProperties.getModel());
        report.put("seedBills", seeded);
        report.put("datasetSize", dataset.size());
        report.put("executionAccuracy", ratio(execHit, dataset.size()));
        report.put("answerAccuracy", ratio(answerHit, dataset.size()));
        report.put("avgLatencyMs", dataset.isEmpty() ? 0 : totalLatency / dataset.size());
        report.put("p95LatencyMs", percentile95(latencies));
        report.put("failures", failures);

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        Files.writeString(Path.of(outputPath), json, StandardCharsets.UTF_8);
        System.out.println(json);
        System.out.println("ASK_EVAL_REPORT=" + outputPath);
        System.exit(0);
    }

    /**
     * 写入固定种子账单：openid 为评测专用账号，按「相对当前月」的偏移换算真实日期。
     */
    private int seedBills() throws Exception {
        jdbcTemplate.update("DELETE FROM bill WHERE openid = ?", openid);
        List<JsonNode> seeds = loadJsonLines(seedPath);
        int count = 0;
        for (JsonNode node : seeds) {
            YearMonth month = YearMonth.now().plusMonths(node.path("monthOffset").asInt());
            int day = Math.min(Math.max(1, node.path("day").asInt(1)), month.lengthOfMonth());
            jdbcTemplate.update(
                    "INSERT INTO bill (openid, book_id, amount, bill_type, pay_channel_code, category_code,"
                            + " record_text, remark, record_date, source, confidence, request_id, deleted,"
                            + " create_time, update_time)"
                            + " VALUES (?, 0, ?, ?, ?, ?, ?, ?, ?, 6, 1.00, ?, 0, NOW(), NOW())",
                    openid,
                    new BigDecimal(node.path("amount").asText("0")),
                    node.path("billType").asInt(0),
                    node.path("payChannelCode").asText(""),
                    node.path("categoryCode").asText(""),
                    node.path("remark").asText("评测账单"),
                    node.path("remark").asText("评测账单"),
                    DateUtils.format(month.atDay(day)),
                    node.path("requestId").asText("ask-eval-" + count));
            count++;
        }
        log.info("已写入评测种子账单 {} 条，openid={}", count, openid);
        return count;
    }

    private List<AskItem> loadDataset() throws Exception {
        List<AskItem> items = new ArrayList<>();
        for (JsonNode node : loadJsonLines(datasetPath)) {
            AskItem item = new AskItem();
            item.question = node.path("question").asText("");
            item.expect = new BigDecimal(node.path("expect").asText("0"));
            if (!item.question.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private List<JsonNode> loadJsonLines(String location) throws Exception {
        Resource resource = location.startsWith("classpath:")
                ? new ClassPathResource(location.substring("classpath:".length()))
                : new FileSystemResource(location);
        List<JsonNode> nodes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                nodes.add(objectMapper.readTree(trimmed));
            }
        }
        return nodes;
    }

    private boolean containsValue(List<Map<String, Object>> rows, BigDecimal expected) {
        if (rows == null) {
            return false;
        }
        for (Map<String, Object> row : rows) {
            for (Object value : row.values()) {
                if (value instanceof BigDecimal decimal
                        && decimal.subtract(expected).abs().compareTo(new BigDecimal("0.011")) <= 0) {
                    return true;
                }
                if (value instanceof Number number
                        && new BigDecimal(number.toString()).subtract(expected).abs()
                        .compareTo(new BigDecimal("0.011")) <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addFailure(List<Map<String, Object>> failures, AskItem item, AskAnswerDTO dto,
                            boolean execOk, boolean answerOk, String error) {
        if (failures.size() >= 20) {
            return;
        }
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("question", item.question);
        sample.put("expect", item.expect);
        sample.put("execOk", execOk);
        sample.put("answerOk", answerOk);
        if (dto != null) {
            sample.put("sql", dto.getSql());
            sample.put("answer", dto.getAnswer());
            sample.put("rows", dto.getRows());
        }
        if (error != null) {
            sample.put("error", error);
        }
        failures.add(sample);
    }

    private double ratio(int hit, int total) {
        return total == 0 ? 0 : Math.round(hit * 10000.0 / total) / 100.0;
    }

    private long percentile95(List<Long> latencies) {
        if (latencies.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(latencies);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    /** 单条问答评测样本 */
    private static class AskItem {
        private String question;
        private BigDecimal expect;
    }
}
