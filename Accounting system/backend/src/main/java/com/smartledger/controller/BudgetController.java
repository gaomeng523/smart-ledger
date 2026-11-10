package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.request.BudgetSaveRequest;
import com.smartledger.dto.response.BudgetDTO;
import com.smartledger.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预算接口（V2）。
 */
@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    /** 设置预算 */
    @PostMapping
    public R<BudgetDTO> save(@Valid @RequestBody BudgetSaveRequest request) {
        return R.ok(budgetService.save(UserContext.requireOpenid(), request));
    }

    /** 查询某月预算与使用情况 */
    @GetMapping
    public R<BudgetDTO> get(@RequestParam(required = false) String month) {
        return R.ok(budgetService.get(UserContext.requireOpenid(), month));
    }

    /** 删除预算 */
    @DeleteMapping
    public R<Void> delete(@RequestParam Long budgetId) {
        budgetService.delete(UserContext.requireOpenid(), budgetId);
        return R.ok();
    }
}