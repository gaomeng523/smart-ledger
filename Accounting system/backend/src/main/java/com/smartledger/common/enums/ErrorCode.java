package com.smartledger.common.enums;

import lombok.Getter;

/**
 * 错误码定义。
 *
 * <p>0 成功；4xx 客户端错误；5xx 服务端错误；1xxx 业务错误。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "越权访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    SYSTEM_ERROR(500, "系统异常，请稍后重试"),

    WX_LOGIN_FAILED(1001, "微信登录失败"),
    AI_PARSE_FAILED(1002, "AI 解析失败，请手动确认"),
    AI_OUTPUT_INVALID(1003, "AI 输出格式非法"),
    AMOUNT_UNRECOGNIZED(1004, "金额无法识别，请手动补充"),
    DUPLICATE_BILL(1005, "疑似重复记账"),
    IMPORT_FORMAT_ERROR(1006, "导入文件格式错误"),
    PARSE_TOO_FREQUENT(1007, "解析过于频繁，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}