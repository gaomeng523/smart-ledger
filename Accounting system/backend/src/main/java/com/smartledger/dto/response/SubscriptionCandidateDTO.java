package com.smartledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订阅识别候选（V3）：由历史账单聚类得出，用户确认后才入库。
 */
@Data
public class SubscriptionCandidateDTO {

    private String title;

    private String categoryCode;

    private String categoryName;

    private String payChannelCode;

    private String payChannelName;

    private BigDecimal avgAmount;

    /** 30 / 90 / 365 */
    private Integer cycleDays;

    private String cycleText;

    /** 预计下次扣费日 yyyy-MM-dd */
    private String nextDate;

    /** 历史出现次数 */
    private Integer occurrences;
}
