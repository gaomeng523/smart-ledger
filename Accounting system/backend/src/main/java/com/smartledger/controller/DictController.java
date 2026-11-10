package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.dto.response.DictResponse;
import com.smartledger.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典接口。
 */
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/all")
    public R<DictResponse> all() {
        return R.ok(dictService.getAll());
    }
}