package com.smartledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 心愿储蓄列表项（V3）。
 */
@Data
public class SavingsGoalDTO {

    private Long id;

    private String title;

    private String icon;

    private BigDecimal targetAmount;

    /** 已存金额 */
    private BigDecimal savedAmount;

    /** 还差多少（已达成时为 0） */
    private BigDecimal remainAmount;

    /** 进度百分比（0~100，保留整数） */
    private Integer progressPercent;

    /** 计划达成日 yyyy-MM-dd，可空 */
    private String dueDate;

    /** 距计划达成日天数：负数=已过计划日；无计划日为 null */
    private Long daysLeft;

    private String categoryCode;

    private String categoryName;

    /** 0 进行中 1 已达成 2 已放弃 */
    private Integer status;

    /** 进行中 / 已达成 / 已放弃 */
    private String statusText;

    public boolean isAchieved() {
        return status != null && status == 1;
    }
}