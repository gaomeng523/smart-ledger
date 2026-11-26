package com.smartledger.ai.ask;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 答案数值比对工具：供离线评测判断回答里是否出现期望数值。
 *
 * <p>容忍千分位与小数格式差异（如 {@code 4,366.50}、{@code 4366.5}），误差不超过 0.01。</p>
 */
public final class AnswerMatcher {

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    /** 允许的数值误差（覆盖四舍五入到分位的差异） */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.011");

    private AnswerMatcher() {
    }

    public static boolean containsValue(String text, BigDecimal expected) {
        if (text == null || expected == null) {
            return false;
        }
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            BigDecimal value = parse(matcher.group());
            if (value != null && value.subtract(expected).abs().compareTo(TOLERANCE) <= 0) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal parse(String raw) {
        try {
            return new BigDecimal(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
