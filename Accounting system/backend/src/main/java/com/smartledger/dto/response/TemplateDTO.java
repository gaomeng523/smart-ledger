package com.smartledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 模板 DTO（V2）。
 */
@Data
public class TemplateDTO {

    private Long templateId;

    private String title;

    private BigDecimal amount;

    private Integer billType;

    private String payChannelCode;

    private String payChannelName;

    private String categoryCode;

    private String categoryName;

    private String remark;

    private Integer useCount;

    private Integer sortNo;
}