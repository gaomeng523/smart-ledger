package com.smartledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订阅列表项（V3）。
 */
@Data
public class SubscriptionDTO {

    private Long id;

    private String title;

    private String categoryCode;

    private String categoryName;

    private String payChannelCode;

    private String payChannelName;

    private BigDecimal avgAmount;

    /** 30 / 90 / 365 */
    private Integer cycleDays;

    /** 月付 / 季付 / 年付 */
    private String cycleText;

    /** yyyy-MM-dd */
    private String nextDate;

    /** 距下次扣费天数：0=今天，负数=已逾期 */
    private Long daysToNext;

    /** overdue 已逾期 / today 今天扣费 / soon 3 天内 / normal 常规 */
    private String dueState;

    /** 1 正常 0 暂停 */
    private Integer status;

    private Integer sampleCount;
}
