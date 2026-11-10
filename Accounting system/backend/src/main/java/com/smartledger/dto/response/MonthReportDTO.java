package com.smartledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 月度消费报告（V3 · F-44）。
 * 文字（summary/highlights/suggestions）必须基于 {@link #stats} 真实统计生成；
 * AI 不可用时由后端按固定模板兜底，禁止任何一方编造数字。
 */
@Data
public class MonthReportDTO {

    /** yyyy-MM */
    private String month;

    /** AI 总开关是否开启 */
    private Boolean aiEnabled;

    /** 文字是否由 AI 生成（false 表示规则兜底文案） */
    private Boolean aiGenerated;

    /** 一段话总结 */
    private String summary;

    /** 消费亮点 / 特征 */
    private List<String> highlights;

    /** 改进建议 */
    private List<String> suggestions;

    /** 真实统计数据（SQL 聚合，权威数字） */
    private Stats stats;

    @Data
    public static class Stats {

        private BigDecimal income;

        private BigDecimal expense;

        private BigDecimal balance;

        private Long billCount;

        /** 笔均支出 */
        private BigDecimal avgExpense;

        /** 支出环比（百分比小数），无上月数据为 null */
        private BigDecimal expenseMom;

        /** 支出分类 Top3（按金额倒序） */
        private List<Category> topCategories;
    }

    @Data
    public static class Category {

        private String categoryCode;

        private String categoryName;

        private BigDecimal amount;

        /** 占当月总支出比例（百分比小数） */
        private BigDecimal ratio;
    }
}
