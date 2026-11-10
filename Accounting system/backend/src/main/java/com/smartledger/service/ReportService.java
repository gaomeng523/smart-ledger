package com.smartledger.service;

import com.smartledger.dto.response.MonthReportDTO;

/**
 * AI 月度报告服务（V3 · F-44）。
 */
public interface ReportService {

    /**
     * 生成指定月份的消费报告：先 SQL 聚合真实统计，再交模型润色文字；
     * AI 不可用或失败时返回规则兜底文案，接口始终可用。
     *
     * @param month yyyy-MM，为空取当前月
     */
    MonthReportDTO month(String openid, String month);
}
