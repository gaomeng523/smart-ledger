package com.smartledger.ai.dto;

import lombok.Data;

/**
 * DeepSeek 单次调用结果：结构化结果 + 原始文本 + token 消耗。
 */
@Data
public class DeepSeekResult {

    private AiRawResponse raw;

    /** 模型返回的原始 JSON 文本 */
    private String content;

    private Integer promptTokens;

    private Integer completionTokens;
}