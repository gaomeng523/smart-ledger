package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.request.SavingsAmountRequest;
import com.smartledger.dto.request.SavingsGoalSaveRequest;
import com.smartledger.dto.response.SavingsGoalDTO;
import com.smartledger.service.SavingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 心愿储蓄接口（V3）。
 */
@RestController
@RequestMapping("/api/savings")
@RequiredArgsConstructor
public class SavingsController {

    private final SavingsService savingsService;

    /** 我的心愿 */
    @GetMapping
    public R<List<SavingsGoalDTO>> list() {
        return R.ok(savingsService.list(UserContext.requireOpenid()));
    }

    /** 新建心愿 */
    @PostMapping
    public R<SavingsGoalDTO> create(@Valid @RequestBody SavingsGoalSaveRequest request) {
        return R.ok(savingsService.create(UserContext.requireOpenid(), request));
    }

    /** 编辑心愿 */
    @PutMapping
    public R<SavingsGoalDTO> update(@Valid @RequestBody SavingsGoalSaveRequest request) {
        return R.ok(savingsService.update(UserContext.requireOpenid(), request));
    }

    /** 存入 / 取出 */
    @PostMapping("/amount")
    public R<SavingsGoalDTO> changeAmount(@Valid @RequestBody SavingsAmountRequest request) {
        return R.ok(savingsService.changeAmount(UserContext.requireOpenid(), request));
    }

    /** 放弃 / 删除心愿 */
    @DeleteMapping
    public R<Void> delete(@RequestParam Long id) {
        savingsService.delete(UserContext.requireOpenid(), id);
        return R.ok();
    }
}