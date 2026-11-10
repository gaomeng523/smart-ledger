package com.smartledger.common.constant;

/**
 * 全局常量。
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 鉴权请求头 */
    public static final String AUTH_HEADER = "Authorization";
    /** Bearer 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";
    /** JWT 中存放 openid 的声明名 */
    public static final String CLAIM_OPENID = "openid";

    /** 默认账本 ID（个人账本） */
    public static final long DEFAULT_BOOK_ID = 0L;

    /** 时间格式 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String MONTH_PATTERN = "yyyy-MM";
    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
}