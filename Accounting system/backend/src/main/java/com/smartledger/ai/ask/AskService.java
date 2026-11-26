package com.smartledger.ai.ask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smartledger.ai.DeepSeekClient;
import com.smartledger.ai.dto.DeepSeekResult;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.config.AiProperties;
import com.smartledger.dto.response.AskAnswerDTO;
import com.smartledger.service.AiParseLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 智能账单问答：自然语言 → 查询 SQL → 只读执行 → 中文回答。
 *
 * <p>两段式调用：第一段让模型产出 SQL（受 {@link SqlGuard} 硬约束），
 * 第二段把结果集转成口语化回答；回答生成失败时用结果集模板兜底，保证接口可用。</p>
 */
@Slf4j
@Service
public class AskService {

    private static final int SQL_MAX_TOKENS = 512;

    private static final int ANSWER_MAX_TOKENS = 256;

    /** 喂给回答模型的明细行数上限，避免长结果把 Prompt 撑爆 */
    private static final int ANSWER_ROW_LIMIT = 10;

    private final DeepSeekClient deepSeekClient;
    private final AskPromptBuilder askPromptBuilder;
    private final BillQueryExecutor billQueryExecutor;
    private final AiParseLogService aiParseLogService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    private final Cache<String, AtomicInteger> rateLimitCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10000)
            .build();

    public AskService(DeepSeekClient deepSeekClient,
                      AskPromptBuilder askPromptBuilder,
                      BillQueryExecutor billQueryExecutor,
                      AiParseLogService aiParseLogService,
                      AiProperties aiProperties,
                      ObjectMapper objectMapper) {
        this.deepSeekClient = deepSeekClient;
        this.askPromptBuilder = askPromptBuilder;
        this.billQueryExecutor = billQueryExecutor;
        this.aiParseLogService = aiParseLogService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 面向接口的问答入口：带单用户限流。
     */
    public AskAnswerDTO ask(String openid, String question) {
        checkRateLimit(openid);
        return answer(openid, question);
    }

    /**
     * 不带限流的问答（供离线评测批量调用）。
     */
    public AskAnswerDTO answer(String openid, String question) {
        if (!aiProperties.isEnabled()) {
            throw new BizException(ErrorCode.AI_PARSE_FAILED, "智能问答暂未开启");
        }
        long start = System.currentTimeMillis();
        try {
            DeepSeekResult plan = deepSeekClient.chatContent(
                    askPromptBuilder.buildSqlPrompt(), question, SQL_MAX_TOKENS);
            String safeSql = normalizeSql(question, plan.getContent());
            List<Map<String, Object>> rows = billQueryExecutor.execute(openid, safeSql);
            String answer = buildAnswer(question, rows);
            long cost = System.currentTimeMillis() - start;

            aiParseLogService.record(openid, "[问答]" + question, plan.getContent(),
                    aiProperties.getModel(), true, null, null, cost,
                    plan.getPromptTokens(), plan.getCompletionTokens());

            AskAnswerDTO dto = new AskAnswerDTO();
            dto.setAnswer(answer);
            dto.setSql(safeSql);
            dto.setRows(rows);
            dto.setLatencyMs(cost);
            return dto;
        } catch (BizException e) {
            aiParseLogService.record(openid, "[问答]" + question, null,
                    aiProperties.getModel(), false, String.valueOf(e.getCode()), e.getMessage(),
                    System.currentTimeMillis() - start, null, null);
            throw e;
        }
    }

    /**
     * 校验模型产出的 SQL；被拦截时把原始输出打进日志，便于定位是模型跑偏还是守卫过严。
     */
    private String normalizeSql(String question, String content) {
        String rawSql = extractSql(content);
        try {
            return SqlGuard.normalize(rawSql);
        } catch (BizException e) {
            log.warn("问答 SQL 被拦截: question={}, raw={}", question, abbreviate(rawSql));
            throw e;
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    /**
     * 从模型输出里取 SQL：优先解析 JSON 的 sql 字段，其次把整段文本当 SQL 交给守卫校验。
     */
    private String extractSql(String content) {
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.AI_OUTPUT_INVALID, "模型未返回查询语句");
        }
        try {
            JsonNode node = objectMapper.readTree(content);
            String sql = node.path("sql").asText("");
            if (!sql.isBlank()) {
                return sql;
            }
        } catch (Exception ignored) {
            // 非 JSON 输出：按原始 SQL 处理，交给 SqlGuard 判定
        }
        return content.trim();
    }

    private String buildAnswer(String question, List<Map<String, Object>> rows) {
        String rowsJson = serializeRows(rows);
        try {
            DeepSeekResult result = deepSeekClient.chatContent(
                    askPromptBuilder.buildAnswerPrompt(rowsJson), question, ANSWER_MAX_TOKENS);
            JsonNode node = objectMapper.readTree(result.getContent());
            String answer = node.path("answer").asText("");
            if (!answer.isBlank()) {
                return answer.trim();
            }
        } catch (Exception e) {
            log.warn("问答回答生成失败，使用模板兜底: {}", e.getMessage());
        }
        return fallbackAnswer(rows);
    }

    private String serializeRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> limited = rows.size() <= ANSWER_ROW_LIMIT
                ? rows
                : rows.subList(0, ANSWER_ROW_LIMIT);
        try {
            return objectMapper.writeValueAsString(limited);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 回答模型不可用时的兜底：直接用结果集拼一句中文，保证用户始终能看到数字。
     */
    private String fallbackAnswer(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "没有查到符合条件的账单记录。";
        }
        Map<String, Object> row = rows.get(0);
        List<String> parts = new ArrayList<>();
        row.forEach((column, value) -> parts.add(column + " " + formatCell(column, value)));
        return "查询结果：" + String.join("，", parts) + "。";
    }

    private String formatCell(String column, Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof BigDecimal decimal) {
            return stripZeros(decimal) + unitOf(column);
        }
        if (value instanceof Number number) {
            return number + unitOf(column);
        }
        return value.toString();
    }

    private String stripZeros(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 2 ? stripped.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                : stripped.toPlainString();
    }

    private String unitOf(String column) {
        String lower = column.toLowerCase(Locale.ROOT);
        return lower.contains("count") || lower.contains("num") || column.contains("笔") ? " 笔" : " 元";
    }

    private void checkRateLimit(String openid) {
        int limit = aiProperties.getRateLimitPerMinute();
        if (limit <= 0) {
            return;
        }
        AtomicInteger counter = rateLimitCache.get(openid, key -> new AtomicInteger());
        if (counter.incrementAndGet() > limit) {
            throw new BizException(ErrorCode.PARSE_TOO_FREQUENT);
        }
    }
}
