package com.smartledger.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartledger.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本向量客户端（OpenAI 兼容协议 /embeddings）。
 *
 * <p>用于把「示例库」和「用户输入」映射到同一向量空间，做相似度召回。
 * 与对话模型解耦，换供应商只需改 base-url / embedding-model。</p>
 */
@Slf4j
@Component
public class EmbeddingClient {

    /** 百炼单次最多 10 条文本 */
    private static final int BATCH_SIZE = 10;

    private final RestClient restClient;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    public EmbeddingClient(RagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 批量向量化，返回顺序与入参一致。
     */
    public List<double[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        List<double[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, texts.size());
            vectors.addAll(embedBatch(texts.subList(from, to)));
        }
        return vectors;
    }

    private List<double[]> embedBatch(List<String> batch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getEmbeddingModel());
        body.put("input", batch);
        body.put("encoding_format", "float");

        String responseJson = restClient.post()
                .uri("/embeddings")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        if (!StringUtils.hasText(responseJson)) {
            throw new IllegalStateException("向量服务返回为空");
        }
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != batch.size()) {
                throw new IllegalStateException("向量服务返回条数不匹配: " + data.size());
            }
            // 按 index 还原顺序，避免服务端乱序
            double[][] ordered = new double[batch.size()][];
            for (JsonNode item : data) {
                int index = item.path("index").asInt(0);
                JsonNode embedding = item.path("embedding");
                double[] vector = new double[embedding.size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = embedding.get(i).asDouble();
                }
                if (index >= 0 && index < ordered.length) {
                    ordered[index] = vector;
                }
            }
            List<double[]> result = new ArrayList<>(batch.size());
            for (double[] vector : ordered) {
                if (vector == null) {
                    throw new IllegalStateException("向量服务返回缺少向量");
                }
                result.add(vector);
            }
            return result;
        } catch (Exception e) {
            log.warn("向量化失败: {}", e.getMessage());
            throw new IllegalStateException("向量化失败", e);
        }
    }
}
