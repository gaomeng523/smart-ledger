package com.smartledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分类占比统计。
 */
@Data
public class StatCategoryDTO {

    private String month;

    private Integer billType;

    /** 下钻时返回的一级分类编码；按一级汇总时为 null */
    private String parentCode;

    private BigDecimal total;

    private List<Item> items;

    @Data
    public static class Item {

        private String categoryCode;

        private String categoryName;

        private BigDecimal amount;

        /** 占比，保留 4 位小数 */
        private BigDecimal ratio;
    }
}