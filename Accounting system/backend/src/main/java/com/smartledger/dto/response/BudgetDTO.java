package com.smartledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 预算查询结果（V2）。
 */
@Data
public class BudgetDTO {

    private String month;

    private BigDecimal totalBudget;

    private BigDecimal totalUsed;

    private BigDecimal totalRemain;

    private BigDecimal usedRatio;

    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {

        private Long budgetId;

        private String categoryCode;

        private String categoryName;

        private BigDecimal amount;

        private BigDecimal used;

        private BigDecimal remain;

        private BigDecimal usedRatio;

        private BigDecimal warnRatio;
    }
}