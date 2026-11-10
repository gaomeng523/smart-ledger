package com.smartledger.common.exception;

import com.smartledger.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。可携带 data，用于「失败但仍返回可用字段」的场景（如 AI 解析失败返回已识别字段）。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    /** 失败时仍可返回给前端的业务数据 */
    private final transient Object data;

    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode.getCode(), message, null);
    }

    public BizException(ErrorCode errorCode, String message, Object data) {
        this(errorCode.getCode(), message, data);
    }

    public BizException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }
}