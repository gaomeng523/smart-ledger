package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.response.MonthReportDTO;
import com.smartledger.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据报告接口（V3）。
 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** AI 月度消费报告（SQL 聚合真实数据 + 模型润色，失败走规则兜底） */
    @GetMapping("/month")
    public R<MonthReportDTO> month(@RequestParam(required = false) String month) {
        return R.ok(reportService.month(UserContext.requireOpenid(), month));
    }
}
