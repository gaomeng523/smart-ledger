package com.smartledger.dto.request;

import lombok.Data;

/**
 * 账单列表查询条件。
 */
@Data
public class BillListQuery {

    private Integer page = 1;

    private Integer size = 20;

    /** yyyy-MM */
    private String month;

    /** 0 支出，1 收入，不传为全部 */
    private Integer billType;

    private String categoryCode;

    private String payChannelCode;

    /** 关键字（匹配备注 / 原始文本） */
    private String keyword;

    // ===== V2 搜索筛选扩展参数（见《04》§4.6） =====

    /** 金额下限（含），单位元 */
    private java.math.BigDecimal amountMin;

    /** 金额上限（含），单位元 */
    private java.math.BigDecimal amountMax;

    /** 记账日期起 yyyy-MM-dd（与 month 互斥，同时传以日期区间为准） */
    private String dateStart;

    /** 记账日期止 yyyy-MM-dd */
    private String dateEnd;

    /** 标签（逗号分隔，多个按 AND 处理） */
    private String tag;

    /** 来源：1 文本 2 语音 3 模板 4 导入 5 OCR 6 手动 */
    private Integer source;
}