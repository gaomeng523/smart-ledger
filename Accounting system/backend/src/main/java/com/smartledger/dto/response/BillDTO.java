package com.smartledger.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartledger.common.constant.CommonConstants;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账单 DTO。
 */
@Data
public class BillDTO {

    private Long billId;

    private BigDecimal amount;

    private Integer billType;

    private String payChannelCode;

    private String payChannelName;

    private String categoryCode;

    private String categoryName;

    private String recordDate;

    private String remark;

    /** 原始输入（仅详情返回） */
    private String recordText;

    private Integer source;

    private BigDecimal confidence;

    /** 标签（后端按逗号拆分后返回，无标签时为 []） */
    private java.util.List<String> tags;

    /** 账单图片 URL（拍照记账），V3 阶段二 */
    private String imageUrl;

    @JsonFormat(pattern = CommonConstants.DATETIME_PATTERN)
    private LocalDateTime createTime;

    @JsonFormat(pattern = CommonConstants.DATETIME_PATTERN)
    private LocalDateTime updateTime;
}