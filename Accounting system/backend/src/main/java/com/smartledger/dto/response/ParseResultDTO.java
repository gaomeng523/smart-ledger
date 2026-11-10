package com.smartledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析结果 DTO（不落库，仅返回给前端确认）。
 */
@Data
public class ParseResultDTO {

    private BigDecimal amount;

    /** 0 支出，1 收入 */
    private Integer billType;

    private String payChannelCode;

    private String payChannelName;

    private String categoryCode;

    private String categoryName;

    /** yyyy-MM-dd */
    private String recordDate;

    private String remark;

    private BigDecimal confidence;

    /** 是否必须用户确认后才可保存（恒为 true） */
    private Boolean needConfirm = true;

    /** 缺失且必须由用户补全的字段列表 */
    private List<String> missingFields = new ArrayList<>();
}