package com.smartledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索增强（RAG）配置。前缀：smartledger.ai.rag
 *
 * <p>用历史记账示例做 few-shot 检索：解析前先按语义相似度召回 Top-K 条，
 * 拼进 Prompt，帮助模型统一分类口径。默认关闭，按需开启。</p>
 */
@Data
@ConfigurationProperties(prefix = "smartledger.ai.rag")
public class RagProperties {

    /** 总开关 */
    private boolean enabled = false;

    /** 向量服务（OpenAI 兼容协议），默认阿里云百炼 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String apiKey;

    /** 向量模型 */
    private String embeddingModel = "text-embedding-v4";

    /** 召回条数 */
    private int topK = 5;

    /** 相似度下限，低于该值不注入，避免噪声 */
    private double minScore = 0.45;

    /** 示例库文件（classpath 或绝对路径） */
    private String bankPath = "classpath:ai/rag-bank.jsonl";

    private int connectTimeout = 3000;

    private int readTimeout = 8000;
}
