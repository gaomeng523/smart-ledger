package com.smartledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收支趋势统计（V3 · F-43）。
 * items 为近 N 月（含当月）升序数据；环比/同比为百分比小数（0.12 = 12%）。
 */
@Data
public class StatTrendDTO {

    /** 6 或 12 */
    private Integer range;

    /** 近 N 月（含当月），按时间升序 */
    private List<Item> items;

    /** 当月支出同比：(当月支出 - 去年同月支出) / 去年同月支出；无去年数据为 null */
    private BigDecimal expenseYoy;

    /** 当月收入同比；无去年数据为 null */
    private BigDecimal incomeYoy;

    @Data
    public static class Item {

        /** yyyy-MM */
        private String month;

        private BigDecimal income;

        private BigDecimal expense;

        private BigDecimal balance;

        /** 支出环比：(本月 - 上月) / 上月；上月无数据或为 0 时为 null */
        private BigDecimal expenseMom;

        /** 收入环比；上月无数据或为 0 时为 null */
        private BigDecimal incomeMom;
    }
}
