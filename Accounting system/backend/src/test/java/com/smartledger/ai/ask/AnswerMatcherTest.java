package com.smartledger.ai.ask;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 答案数值比对单测：评测判定依赖它，格式差异必须容忍、数值差异必须判错。
 */
class AnswerMatcherTest {

    @Test
    @DisplayName("千分位与小数位差异可命中")
    void shouldMatchFormattedNumber() {
        assertThat(AnswerMatcher.containsValue("这个月支出一共 4,366.50 元。", new BigDecimal("4366.5"))).isTrue();
        assertThat(AnswerMatcher.containsValue("一共 4366.5 元", new BigDecimal("4366.50"))).isTrue();
    }

    @Test
    @DisplayName("四舍五入到分位的差异在容差内")
    void shouldTolerateRounding() {
        assertThat(AnswerMatcher.containsValue("平均每笔 363.88 元", new BigDecimal("363.875"))).isTrue();
    }

    @Test
    @DisplayName("数值不同不命中")
    void shouldNotMatchDifferentValue() {
        assertThat(AnswerMatcher.containsValue("一共 4366.50 元", new BigDecimal("4366.66"))).isFalse();
    }

    @Test
    @DisplayName("笔数等整数同样可命中")
    void shouldMatchInteger() {
        assertThat(AnswerMatcher.containsValue("这个月记了 14 笔账", new BigDecimal("14"))).isTrue();
    }

    @Test
    @DisplayName("空值安全")
    void shouldHandleNull() {
        assertThat(AnswerMatcher.containsValue(null, BigDecimal.ONE)).isFalse();
        assertThat(AnswerMatcher.containsValue("没有相关账单", null)).isFalse();
    }
}
