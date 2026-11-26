package com.smartledger.controller;

import com.smartledger.ai.ask.AskService;
import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.request.AskRequest;
import com.smartledger.dto.response.AskAnswerDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能账单问答接口：自然语言问账本，返回中文回答与命中的只读 SQL。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AskController {

    private final AskService askService;

    @PostMapping("/ask")
    public R<AskAnswerDTO> ask(@Valid @RequestBody AskRequest request) {
        return R.ok(askService.ask(UserContext.requireOpenid(), request.getQuestion()));
    }
}
