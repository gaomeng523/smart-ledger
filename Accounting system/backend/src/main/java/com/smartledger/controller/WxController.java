package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.dto.request.WxLoginRequest;
import com.smartledger.dto.response.LoginResponse;
import com.smartledger.service.WxLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信登录接口。此接口无需鉴权。
 */
@RestController
@RequestMapping("/api/wx")
@RequiredArgsConstructor
public class WxController {

    private final WxLoginService wxLoginService;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody WxLoginRequest request) {
        return R.ok(wxLoginService.login(request));
    }
}