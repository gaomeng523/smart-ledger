package com.smartledger.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 月报模型输出结构（response_format = json_object）。
 */
@Data
public class ReportAiResponse {

    /** 一段话月度总结 */
    private String summary;

    /** 消费特征 / 亮点，2~4 条 */
    private List<String> highlights;

    /** 改进建议，2~4 条 */
    private List<String> suggestions;
}
