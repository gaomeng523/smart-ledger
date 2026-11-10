package com.smartledger.service;

import com.smartledger.entity.AiParseLog;
import com.smartledger.mapper.AiParseLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 解析日志服务。记录失败不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiParseLogService {

    private static final int MAX_INPUT_LEN = 500;

    private final AiParseLogMapper aiParseLogMapper;

    public void record(String openid,
                       String inputText,
                       String outputJson,
                       String model,
                       boolean success,
                       String errorCode,
                       String errorMsg,
                       long costMs,
                       Integer promptTokens,
                       Integer completionTokens) {
        try {
            AiParseLog entity = new AiParseLog();
            entity.setOpenid(openid);
            entity.setInputText(truncate(inputText, MAX_INPUT_LEN));
            entity.setOutputJson(outputJson);
            entity.setModel(model);
            entity.setSuccess(success ? 1 : 0);
            entity.setErrorCode(errorCode);
            entity.setErrorMsg(truncate(errorMsg, 255));
            entity.setCostMs((int) costMs);
            entity.setPromptTokens(promptTokens);
            entity.setCompletionTokens(completionTokens);
            aiParseLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写入 AI 解析日志失败: {}", e.getMessage());
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}