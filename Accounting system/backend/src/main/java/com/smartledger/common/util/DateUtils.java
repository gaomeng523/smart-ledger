package com.smartledger.common.util;

import com.smartledger.common.constant.CommonConstants;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * 日期工具。
 */
public final class DateUtils {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern(CommonConstants.DATE_PATTERN);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern(CommonConstants.MONTH_PATTERN);

    private DateUtils() {
    }

    public static String format(LocalDate date) {
        return date == null ? null : date.format(DATE_FMT);
    }

    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 yyyy-MM，非法则回退到当前月。
     */
    public static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FMT);
        } catch (Exception e) {
            return YearMonth.now();
        }
    }

    public static String formatMonth(YearMonth month) {
        return month.format(MONTH_FMT);
    }
}