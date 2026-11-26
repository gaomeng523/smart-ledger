package com.smartledger.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartledger.ai.dto.AiRawResponse;
import com.smartledger.ai.dto.DeepSeekResult;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek Chat Completions 客户端：封装 HTTP 调用、超时、重试与异常转换。
 */
@Slf4j
@Component
public class DeepSeekClient {

    /** 解析结果通常不到 150 token；留足余量避免 JSON 被截断 */
    private static final int MAX_TOKENS = 1024;

    private final RestClient restClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(@Qualifier("aiRestClient") RestClient restClient,
                          AiProperties aiProperties,
                          ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用模型并解析为记账解析结构（{@link AiRawResponse}）。
     */
    public DeepSeekResult chat(String systemPrompt, String userText) {
        DeepSeekResult result = chatContent(systemPrompt, userText, MAX_TOKENS);
        try {
            result.setRaw(objectMapper.readValue(result.getContent(), AiRawResponse.class));
            return result;
        } catch (Exception first) {
            // 偶发截断/非 JSON：放宽输出上限重试一次，仍失败才降级
            log.warn("AI 输出非合法 JSON（重试一次）: {}", abbreviate(result.getContent()));
            DeepSeekResult retry = chatContent(systemPrompt, userText, MAX_TOKENS * 2);
            try {
                retry.setRaw(objectMapper.readValue(retry.getContent(), AiRawResponse.class));
                return retry;
            } catch (Exception second) {
                log.warn("AI 输出非合法 JSON: {}", abbreviate(retry.getContent()));
                throw new BizException(ErrorCode.AI_OUTPUT_INVALID, "AI 输出格式非法");
            }
        }
    }

    private String abbreviate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 200 ? content : content.substring(0, 200) + "...";
    }

    /**
     * 通用对话：只保证返回模型文本（调用方自行解析 JSON 结构），供 AI 月报等场景复用。
     *
     * @param maxTokens 本次调用允许的最大输出 token
     */
    public DeepSeekResult chatContent(String systemPrompt, String userText, int maxTokens) {
        if (!StringUtils.hasText(aiProperties.getApiKey())) {
            throw new BizException(ErrorCode.AI_PARSE_FAILED, "未配置 DEEPSEEK_API_KEY");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiProperties.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userText)
        ));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", aiProperties.getTemperature());
        body.put("max_tokens", maxTokens);
        body.put("stream", false);

        String responseJson;
        int attempts = Math.max(1, aiProperties.getMaxRetries() + 1);
        Exception lastError = null;
        for (int i = 0; i < attempts; i++) {
            try {
                responseJson = restClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + aiProperties.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return parseResponse(responseJson);
            } catch (BizException e) {
                // 输出格式非法等业务异常不重试
                throw e;
            } catch (HttpClientErrorException e) {
                // 4xx（密钥错误、模型名错误、参数非法）重试没有意义，直接失败
                log.warn("DeepSeek 请求被拒绝: {} {}", e.getStatusCode(), e.getMessage());
                throw new BizException(ErrorCode.AI_PARSE_FAILED, "AI 服务拒绝请求（请检查密钥与模型配置）");
            } catch (Exception e) {
                lastError = e;
                log.warn("DeepSeek 调用失败（第 {} 次）: {}", i + 1, e.getMessage());
                if (i < attempts - 1) {
                    sleepBackoff(i);
                }
            }
        }
        log.error("DeepSeek 调用最终失败", lastError);
        throw new BizException(ErrorCode.AI_PARSE_FAILED, "AI 解析超时或服务异常，请手动确认");
    }

    private DeepSeekResult parseResponse(String responseJson) {
        if (!StringUtils.hasText(responseJson)) {
            throw new BizException(ErrorCode.AI_PARSE_FAILED, "AI 返回为空");
        }
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            if (root.hasNonNull("error")) {
                throw new BizException(ErrorCode.AI_PARSE_FAILED,
                        "AI 服务返回错误: " + root.path("error").path("message").asText());
            }
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new BizException(ErrorCode.AI_PARSE_FAILED, "AI 返回缺少 content 字段");
            }
            String content = contentNode.asText();

            DeepSeekResult result = new DeepSeekResult();
            result.setContent(content);
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                result.setPromptTokens(usage.path("prompt_tokens").asInt(0));
                result.setCompletionTokens(usage.path("completion_tokens").asInt(0));
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_PARSE_FAILED, "AI 响应解析失败");
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(300L * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}