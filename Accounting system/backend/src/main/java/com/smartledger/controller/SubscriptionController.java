package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.request.SubscriptionSaveRequest;
import com.smartledger.dto.request.SubscriptionUpdateRequest;
import com.smartledger.dto.response.SubscriptionCandidateDTO;
import com.smartledger.dto.response.SubscriptionDTO;
import com.smartledger.service.SubscriptionService;
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
 * 周期订阅接口（V3）。
 */
@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /** 我的订阅 */
    @GetMapping
    public R<List<SubscriptionDTO>> list() {
        return R.ok(subscriptionService.list(UserContext.requireOpenid()));
    }

    /** 扫描历史账单，返回待确认的订阅候选 */
    @GetMapping("/suggest")
    public R<List<SubscriptionCandidateDTO>> suggest() {
        return R.ok(subscriptionService.suggest(UserContext.requireOpenid()));
    }

    /** 确认候选 / 手动新建 */
    @PostMapping
    public R<SubscriptionDTO> create(@Valid @RequestBody SubscriptionSaveRequest request) {
        return R.ok(subscriptionService.create(UserContext.requireOpenid(), request));
    }

    /** 编辑 / 暂停 / 恢复 */
    @PutMapping
    public R<SubscriptionDTO> update(@Valid @RequestBody SubscriptionUpdateRequest request) {
        return R.ok(subscriptionService.update(UserContext.requireOpenid(), request));
    }

    /** 删除 */
    @DeleteMapping
    public R<Void> delete(@RequestParam Long id) {
        subscriptionService.delete(UserContext.requireOpenid(), id);
        return R.ok();
    }
}
