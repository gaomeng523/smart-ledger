package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.request.AliasSaveRequest;
import com.smartledger.dto.request.AliasUpdateRequest;
import com.smartledger.dto.response.AliasDTO;
import com.smartledger.service.AliasService;
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
 * 别名词典接口（V2）。
 */
@RestController
@RequestMapping("/api/alias")
@RequiredArgsConstructor
public class AliasController {

    private final AliasService aliasService;

    @PostMapping
    public R<AliasDTO> create(@Valid @RequestBody AliasSaveRequest request) {
        return R.ok(aliasService.create(UserContext.requireOpenid(), request));
    }

    @GetMapping
    public R<List<AliasDTO>> list() {
        return R.ok(aliasService.list(UserContext.requireOpenid()));
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody AliasUpdateRequest request) {
        aliasService.update(UserContext.requireOpenid(), request);
        return R.ok();
    }

    @DeleteMapping
    public R<Void> delete(@RequestParam Long aliasId) {
        aliasService.delete(UserContext.requireOpenid(), aliasId);
        return R.ok();
    }
}