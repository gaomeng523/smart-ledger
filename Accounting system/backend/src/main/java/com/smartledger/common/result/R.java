package com.smartledger.common.result;

import lombok.Data;

/**
 * 统一响应结构。
 *
 * <pre>
 * {
 *   "code": 0,
 *   "message": "success",
 *   "data": { },
 *   "timestamp": 1791123571000
 * }
 * </pre>
 */
@Data
public class R<T> {

    /** 0 表示成功，非 0 为错误码 */
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public R() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(0);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> R<T> error(int code, String message) {
        return error(code, message, null);
    }

    public static <T> R<T> error(int code, String message, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        return r;
    }
}