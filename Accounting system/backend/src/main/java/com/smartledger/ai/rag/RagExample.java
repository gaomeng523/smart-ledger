package com.smartledger.ai.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 检索示例：一条已标注的历史记账文本。
 */
@Data
@AllArgsConstructor
public class RagExample {

    /** 历史文本，如「瑞幸生椰拿铁 19」 */
    private String text;

    /** 该笔金额 */
    private BigDecimal amount;

    /** 人工/历史确认过的分类编码 */
    private String categoryCode;

    /** 0=支出 1=收入 */
    private Integer billType;
}
