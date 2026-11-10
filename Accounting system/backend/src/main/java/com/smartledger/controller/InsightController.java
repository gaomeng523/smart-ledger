package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.response.InsightDTO;
import com.smartledger.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消费洞察接口（V3）。
 */
@RestController
@RequestMapping("/api/insight")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    /** 记账后的即时洞察 */
    @GetMapping("/after-save")
    public R<List<InsightDTO>> afterSave(@RequestParam Long billId) {
        return R.ok(insightService.afterSave(UserContext.requireOpenid(), billId));
    }

    /** 月度洞察（统计页卡片），month 为空默认当月 */
    @GetMapping("/month")
    public R<List<InsightDTO>> month(@RequestParam(required = false) String month) {
        return R.ok(insightService.monthInsights(UserContext.requireOpenid(), month));
    }
}
