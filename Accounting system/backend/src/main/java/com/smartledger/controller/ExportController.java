package com.smartledger.controller;

import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.util.DateUtils;
import com.smartledger.common.util.UserContext;
import com.smartledger.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 数据导出接口（V3 · F-91）。
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /**
     * 导出账单 CSV（流式、UTF-8 BOM）。
     *
     * @param startDate yyyy-MM-dd，不传默认近一年
     * @param endDate   yyyy-MM-dd，不传默认今天，且不允许晚于今天
     */
    @GetMapping("/bill")
    public void exportBill(@RequestParam(required = false) String startDate,
                           @RequestParam(required = false) String endDate,
                           HttpServletResponse response) throws IOException {
        LocalDate end = DateUtils.parseDate(endDate);
        LocalDate today = LocalDate.now();
        if (end == null || end.isAfter(today)) {
            end = today;
        }
        LocalDate start = DateUtils.parseDate(startDate);
        if (start == null) {
            start = end.minusYears(1);
        }
        if (start.isAfter(end)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }

        String fileName = "bill_" + start.toString().replace("-", "")
                + "_" + end.toString().replace("-", "") + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // filename 兜底 ASCII，filename* 提供 UTF-8 中文名，避免浏览器文件名乱码
        response.setHeader("Content-Disposition",
                "attachment; filename=\"bills.csv\"; filename*=UTF-8''"
                        + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));

        exportService.writeBillCsv(UserContext.requireOpenid(), start, end, response.getOutputStream());
    }
}
