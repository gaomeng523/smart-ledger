package com.smartledger.common.util;

import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;

/**
 * 当前登录用户上下文（基于 ThreadLocal，由 JwtAuthInterceptor 写入与清理）。
 */
public final class UserContext {

    private static final ThreadLocal<String> OPENID_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setOpenid(String openid) {
        OPENID_HOLDER.set(openid);
    }

    public static String getOpenid() {
        return OPENID_HOLDER.get();
    }

    /**
     * 获取当前 openid，缺失即视为未登录。
     */
    public static String requireOpenid() {
        String openid = OPENID_HOLDER.get();
        if (openid == null || openid.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return openid;
    }

    public static void clear() {
        OPENID_HOLDER.remove();
    }
}