package com.smartledger.service.impl;

import com.smartledger.common.util.DateUtils;
import com.smartledger.dto.response.StatMonthDTO;
import com.smartledger.dto.response.StatTrendDTO;
import com.smartledger.mapper.BillMapper;
import com.smartledger.service.DictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 统计口径单测：月度结余、无数据补零、环比基数缺失时不得除零。
 */
@ExtendWith(MockitoExtension.class)
class StatServiceImplTest {

    @Mock
    private BillMapper billMapper;

    @Mock
    private DictService dictService;

    private StatServiceImpl statService;

    @BeforeEach
    void setUp() {
        statService = new StatServiceImpl(billMapper, dictService);
    }

    @Test
    @DisplayName("月度统计：结余 = 收入 - 支出")
    void shouldCalculateBalance() {
        when(billMapper.selectMonthStat("u1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)))
                .thenReturn(Map.of(
                        "income", new BigDecimal("1000.00"),
                        "expense", new BigDecimal("400.00"),
                        "billCount", 3L));

        StatMonthDTO dto = statService.month("u1", "2026-11");

        assertThat(dto.getMonth()).isEqualTo("2026-11");
        assertThat(dto.getIncome()).isEqualByComparingTo("1000.00");
        assertThat(dto.getExpense()).isEqualByComparingTo("400.00");
        assertThat(dto.getBalance()).isEqualByComparingTo("600.00");
        assertThat(dto.getBillCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("当月无账单时补零，不抛异常")
    void shouldReturnZerosWhenNoBills() {
        when(billMapper.selectMonthStat(anyString(), any(), any())).thenReturn(null);

        StatMonthDTO dto = statService.month("u1", null);

        assertThat(dto.getExpense()).isEqualByComparingTo("0");
        assertThat(dto.getIncome()).isEqualByComparingTo("0");
        assertThat(dto.getBalance()).isEqualByComparingTo("0");
        assertThat(dto.getBillCount()).isZero();
    }

    @Test
    @DisplayName("趋势统计：无数据时返回 6 个月补零且同比为空")
    void shouldFillZerosWhenNoTrendData() {
        when(billMapper.selectMonthlyTrend(anyString(), any(), any())).thenReturn(List.of());
        when(billMapper.selectMonthStat(anyString(), any(), any())).thenReturn(null);

        StatTrendDTO dto = statService.trend("u1", 6);

        assertThat(dto.getItems()).hasSize(6);
        assertThat(dto.getItems().get(5).getExpense()).isEqualByComparingTo("0");
        assertThat(dto.getExpenseYoy()).isNull();
        assertThat(dto.getIncomeYoy()).isNull();
    }

    @Test
    @DisplayName("趋势统计：环比 =(本月 - 上月) / 上月")
    void shouldCalculateMonthOverMonth() {
        YearMonth current = YearMonth.now();
        when(billMapper.selectMonthlyTrend(anyString(), any(), any())).thenReturn(List.of(
                Map.<String, Object>of("month", DateUtils.formatMonth(current),
                        "income", BigDecimal.ZERO, "expense", new BigDecimal("500.00")),
                Map.<String, Object>of("month", DateUtils.formatMonth(current.minusMonths(1)),
                        "income", BigDecimal.ZERO, "expense", new BigDecimal("400.00"))));
        when(billMapper.selectMonthStat(anyString(), any(), any()))
                .thenReturn(Map.of("income", BigDecimal.ZERO, "expense", BigDecimal.ZERO));

        StatTrendDTO dto = statService.trend("u1", 6);

        assertThat(dto.getItems().get(5).getExpenseMom()).isEqualByComparingTo("0.25");
        assertThat(dto.getItems().get(4).getExpenseMom()).isNull();
    }
}
