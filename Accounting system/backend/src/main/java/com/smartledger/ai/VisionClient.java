package com.smartledger.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartledger.ai.dto.AiRawResponse;
import com.smartledger.ai.dto.DeepSeekResult;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.config.VisionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉模型客户端（OpenAI 兼容协议）：把图片以 base64 内联发给模型，返回结构化 JSON。
 *
 * <p>默认对接阿里云百炼通义千问视觉模型，换供应商只需改配置里的 base-url / model。</p>
 */
@Slf4j
@Component
public class VisionClient {

    private final RestClient restClient;
    private final VisionProperties visionProperties;
    private final ObjectMapper objectMapper;

    public VisionClient(@Qualifier("visionRestClient") RestClient restClient,
                        VisionProperties visionProperties,
                        ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.visionProperties = visionProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 识别图片并解析为记账结构。
     *
     * @param imageBytes 图片原始字节（不做公网暴露，直接内联传输）
     * @param mimeType   如 image/jpeg
     */
    public DeepSeekResult chat(String systemPrompt, byte[] imageBytes, String mimeType) {
        if (!StringUtils.hasText(visionProperties.getApiKey())) {
            throw new BizException(ErrorCode.AI_PARSE_FAILED, "未配置 DASHSCOPE_API_KEY");
        }

        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", visionProperties.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", "识别这张小票/账单截图，按约定的 JSON 输出。"),
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
                ))
        ));
        body.put("temperature", visionProperties.getTemperature());
        body.put("max_tokens", visionProperties.getMaxTokens());
        body.put("stream", false);

        String responseJson;
        int attempts = Math.max(1, visionProperties.getMaxRetries() + 1);
        Exception lastError = null;
        for (int i = 0; i < attempts; i++) {
            try {
                responseJson = restClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + visionProperties.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return parseResponse(responseJson);
            } catch (BizException e) {
                throw e;
            } catch (HttpClientErrorException e) {
                // 4xx（密钥错误、模型无权限、图片格式不支持）不做无谓重试
                log.warn("视觉模型请求被拒绝: {} {}", e.getStatusCode(), e.getMessage());
                throw new BizException(ErrorCode.AI_PARSE_FAILED, "图片识别服务拒绝请求（请检查密钥与模型配置）");
            } catch (Exception e) {
                lastError = e;
                log.warn("视觉模型调用失败（第 {} 次）: {}", i + 1, e.getMessage());
                if (i < attempts - 1) {
                    sleepBackoff(i);
                }
            }
        }
        log.error("视觉模型调用最终失败", lastError);
        throw new BizException(ErrorCode.AI_PARSE_FAILED, "图片识别超时或服务异常，请手动确认");
    }

    private DeepSeekResult parseResponse(String responseJson) {
        if (!StringUtils.hasText(responseJson)) {
            throw new BizException(ErrorCode.AI_PARSE_FAILED, "图片识别返回为空");
        }
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            if (root.hasNonNull("error")) {
                throw new BizException(ErrorCode.AI_PARSE_FAILED,
                        "图片识别服务返回错误: " + root.path("error").path("message").asText());
            }
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new BizException(ErrorCode.AI_PARSE_FAILED, "图片识别返回缺少 content 字段");
            }
            String content = contentNode.asText();

            DeepSeekResult result = new DeepSeekResult();
            result.setContent(content);
            result.setRaw(objectMapper.readValue(extractJson(content), AiRawResponse.class));
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                result.setPromptTokens(usage.path("prompt_tokens").asInt(0));
                result.setCompletionTokens(usage.path("completion_tokens").asInt(0));
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("视觉模型输出非合法 JSON: {}", e.getMessage());
            throw new BizException(ErrorCode.AI_OUTPUT_INVALID, "图片识别结果格式非法，请手动确认");
        }
    }

    /**
     * 兼容模型把 JSON 包在 ```json 代码块里的情况。
     */
    private String extractJson(String content) {
        String text = content == null ? "" : content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(300L * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
