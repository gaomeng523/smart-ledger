package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.request.TemplateSaveRequest;
import com.smartledger.dto.request.TemplateUpdateRequest;
import com.smartledger.dto.response.TemplateDTO;
import com.smartledger.dto.response.TemplateUseResultDTO;
import com.smartledger.service.TemplateService;
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
 * 快捷模板接口（V2）。
 */
@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /** 新增模板 */
    @PostMapping
    public R<TemplateDTO> create(@Valid @RequestBody TemplateSaveRequest request) {
        return R.ok(templateService.create(UserContext.requireOpenid(), request));
    }

    /** 模板列表 */
    @GetMapping
    public R<List<TemplateDTO>> list() {
        return R.ok(templateService.list(UserContext.requireOpenid()));
    }

    /** 修改模板 */
    @PutMapping
    public R<Void> update(@Valid @RequestBody TemplateUpdateRequest request) {
        templateService.update(UserContext.requireOpenid(), request);
        return R.ok();
    }

    /** 删除模板（逻辑删除） */
    @DeleteMapping
    public R<Void> delete(@RequestParam Long templateId) {
        templateService.delete(UserContext.requireOpenid(), templateId);
        return R.ok();
    }

    /** 一键模板记账（跳过 AI） */
    @PostMapping("/use")
    public R<TemplateUseResultDTO> use(@RequestParam Long templateId) {
        return R.ok(templateService.use(UserContext.requireOpenid(), templateId));
    }
}