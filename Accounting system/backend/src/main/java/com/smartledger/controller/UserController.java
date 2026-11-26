package com.smartledger.controller;

import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：数据重置。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 重置当前用户的全部记账数据（保留账号、昵称、头像与全局字典） */
    @PostMapping("/reset")
    public R<Void> reset() {
        userService.reset(UserContext.requireOpenid());
        return R.ok();
    }
}
