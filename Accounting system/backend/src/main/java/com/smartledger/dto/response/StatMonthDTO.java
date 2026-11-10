package com.smartledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度收支统计。
 */
@Data
public class StatMonthDTO {

    private String month;

    private BigDecimal income;

    private BigDecimal expense;

    private BigDecimal balance;

    private Long billCount;
}