package com.smartledger.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模型返回的结构化结果（对应系统 Prompt 的输出契约）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRawResponse {

    @JsonProperty("amount")
    private BigDecimal amount;

    /** 0 支出，1 收入 */
    @JsonProperty("billType")
    private Integer billType;

    /** 渠道名称（中文），如「微信」 */
    @JsonProperty("payChannel")
    private String payChannel;

    /** 分类名称（中文），如「买菜餐饮」 */
    @JsonProperty("category")
    private String category;

    /** yyyy-MM-dd */
    @JsonProperty("recordDate")
    private String recordDate;

    @JsonProperty("remark")
    private String remark;

    /** 0~1 */
    @JsonProperty("confidence")
    private BigDecimal confidence;
}