package com.smartledger.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smartledger.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 示例检索服务：把用户输入向量化，从示例库召回语义最接近的历史记账，
 * 供 Prompt 作为 few-shot 参考。首次调用时懒加载并缓存向量，失败即降级为空结果。
 */
@Slf4j
@Service
public class RagService {

    private final RagProperties properties;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    private final List<RagExample> examples = new ArrayList<>();
    private final List<double[]> vectors = new ArrayList<>();
    private volatile boolean attempted;
    private volatile boolean ready;

    /** 查询向量缓存：同一句话短时间内重复解析不重复计费 */
    private final Cache<String, double[]> queryCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(500)
            .build();

    public RagService(RagProperties properties, EmbeddingClient embeddingClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.isEnabled() && StringUtils.hasText(properties.getApiKey());
    }

    /**
     * 召回与输入最相似的 Top-K 示例；未开启、初始化失败或无命中时返回空列表。
     */
    public List<RagExample> retrieve(String query) {
        if (!isEnabled() || !StringUtils.hasText(query)) {
            return new ArrayList<>();
        }
        ensureReady();
        if (!ready) {
            return new ArrayList<>();
        }

        double[] queryVector;
        try {
            queryVector = queryCache.get(query, this::embedOne);
        } catch (Exception e) {
            log.warn("查询向量化失败，本次跳过检索: {}", e.getMessage());
            return new ArrayList<>();
        }

        List<Scored> scored = new ArrayList<>(examples.size());
        for (int i = 0; i < examples.size(); i++) {
            scored.add(new Scored(examples.get(i), cosine(queryVector, vectors.get(i))));
        }
        return scored.stream()
                .filter(item -> item.score >= properties.getMinScore())
                .sorted(Comparator.comparingDouble((Scored item) -> item.score).reversed())
                .limit(Math.max(1, properties.getTopK()))
                .map(item -> item.example)
                .collect(Collectors.toList());
    }

    /** 示例库规模，供日志与评测使用 */
    public int size() {
        return ready ? examples.size() : 0;
    }

    private double[] embedOne(String text) {
        return embeddingClient.embed(List.of(text)).get(0);
    }

    private void ensureReady() {
        if (attempted) {
            return;
        }
        synchronized (this) {
            if (attempted) {
                return;
            }
            attempted = true;
            try {
                loadExamples();
                if (examples.isEmpty()) {
                    log.warn("RAG 示例库为空，检索能力关闭");
                    return;
                }
                List<String> texts = examples.stream().map(RagExample::getText).collect(Collectors.toList());
                vectors.addAll(embeddingClient.embed(texts));
                ready = vectors.size() == examples.size();
                log.info("RAG 示例库就绪: {} 条, 模型 {}", examples.size(), properties.getEmbeddingModel());
            } catch (Exception e) {
                log.warn("RAG 示例库初始化失败，自动降级为不检索: {}", e.getMessage());
                examples.clear();
                vectors.clear();
                ready = false;
            }
        }
    }

    private void loadExamples() throws Exception {
        var resource = properties.getBankPath() != null && properties.getBankPath().startsWith("classpath:")
                ? new ClassPathResource(properties.getBankPath().substring("classpath:".length()))
                : new FileSystemResource(properties.getBankPath());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(trimmed);
                String text = node.path("text").asText("");
                String categoryCode = node.path("categoryCode").asText("");
                if (!StringUtils.hasText(text) || !StringUtils.hasText(categoryCode)) {
                    continue;
                }
                examples.add(new RagExample(
                        text,
                        new BigDecimal(node.path("amount").asText("0")),
                        categoryCode,
                        node.path("billType").asInt(0)));
            }
        }
    }

    private double cosine(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static class Scored {
        private final RagExample example;
        private final double score;

        private Scored(RagExample example, double score) {
            this.example = example;
            this.score = score;
        }
    }
}
