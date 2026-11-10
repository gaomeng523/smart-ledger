package com.smartledger.service.impl;

import com.smartledger.common.util.DateUtils;
import com.smartledger.dto.response.StatCategoryDTO;
import com.smartledger.dto.response.StatMonthDTO;
import com.smartledger.dto.response.StatTrendDTO;
import com.smartledger.mapper.BillMapper;
import com.smartledger.service.DictService;
import com.smartledger.service.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务实现。统计口径统一为 record_date，且仅统计 deleted = 0。
 */
@Service
@RequiredArgsConstructor
public class StatServiceImpl implements StatService {

    private final BillMapper billMapper;
    private final DictService dictService;

    @Override
    public StatMonthDTO month(String openid, String month) {
        YearMonth yearMonth = DateUtils.parseMonth(month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = start.plusMonths(1);

        Map<String, Object> row = billMapper.selectMonthStat(openid, start, end);
        BigDecimal income = toDecimal(row == null ? null : row.get("income"));
        BigDecimal expense = toDecimal(row == null ? null : row.get("expense"));
        long billCount = toLong(row == null ? null : row.get("billCount"));

        StatMonthDTO dto = new StatMonthDTO();
        dto.setMonth(DateUtils.formatMonth(yearMonth));
        dto.setIncome(income);
        dto.setExpense(expense);
        dto.setBalance(income.subtract(expense));
        dto.setBillCount(billCount);
        return dto;
    }

    @Override
    public StatCategoryDTO category(String openid, String month, Integer billType, String parentCode) {
        YearMonth yearMonth = DateUtils.parseMonth(month);
        int type = billType != null && billType == 1 ? 1 : 0;
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = start.plusMonths(1);

        List<Map<String, Object>> rows = billMapper.selectCategoryStat(openid, type, start, end);

        Map<String, String> categoryNames = dictService.categoryNameMap();
        Map<String, String> parentOf = dictService.categoryParentMap();
        boolean drill = parentCode != null && !parentCode.isBlank();

        // 默认按一级分类汇总；传入 parentCode 时只保留该一级下的明细
        Map<String, BigDecimal> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String code = String.valueOf(row.get("categoryCode"));
            String parent = parentOf.getOrDefault(code, code);
            String key;
            if (drill) {
                if (!parentCode.equals(parent)) {
                    continue;
                }
                key = code;
            } else {
                key = parent;
            }
            grouped.merge(key, toDecimal(row.get("amount")), BigDecimal::add);
        }

        BigDecimal total = grouped.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StatCategoryDTO.Item> items = new ArrayList<>();
        grouped.forEach((code, amount) -> {
            StatCategoryDTO.Item item = new StatCategoryDTO.Item();
            item.setCategoryCode(code);
            String name = categoryNames.getOrDefault(code, code);
            // 下钻时，历史上一级分类的账单没有二级归属，单独标为「未细分」
            if (drill && code.equals(parentCode)) {
                name = name + "（未细分）";
            }
            item.setCategoryName(name);
            item.setAmount(amount);
            item.setRatio(total.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(total, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            items.add(item);
        });

        StatCategoryDTO dto = new StatCategoryDTO();
        dto.setMonth(DateUtils.formatMonth(yearMonth));
        dto.setBillType(type);
        dto.setParentCode(drill ? parentCode : null);
        dto.setTotal(total);
        dto.setItems(items);
        return dto;
    }

    @Override
    public StatTrendDTO trend(String openid, Integer range) {
        int months = (range != null && range == 12) ? 12 : 6;
        YearMonth current = YearMonth.now();
        YearMonth first = current.minusMonths(months - 1L);

        // 多取起始月的「上一个月」，用于第一条数据的环比
        LocalDate queryStart = first.minusMonths(1).atDay(1);
        LocalDate queryEnd = current.plusMonths(1).atDay(1);
        List<Map<String, Object>> rows = billMapper.selectMonthlyTrend(openid, queryStart, queryEnd);

        Map<String, BigDecimal> incomeMap = new HashMap<>();
        Map<String, BigDecimal> expenseMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String monthKey = String.valueOf(row.get("month"));
            incomeMap.put(monthKey, toDecimal(row.get("income")));
            expenseMap.put(monthKey, toDecimal(row.get("expense")));
        }

        List<StatTrendDTO.Item> items = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth yearMonth = first.plusMonths(i);
            String key = DateUtils.formatMonth(yearMonth);
            String prevKey = DateUtils.formatMonth(yearMonth.minusMonths(1));

            BigDecimal income = incomeMap.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal expense = expenseMap.getOrDefault(key, BigDecimal.ZERO);

            StatTrendDTO.Item item = new StatTrendDTO.Item();
            item.setMonth(key);
            item.setIncome(income);
            item.setExpense(expense);
            item.setBalance(income.subtract(expense));
            item.setExpenseMom(changeRatio(expense, expenseMap.get(prevKey)));
            item.setIncomeMom(changeRatio(income, incomeMap.get(prevKey)));
            items.add(item);
        }

        // 当月同比：去年同月
        YearMonth lastYear = current.minusYears(1);
        Map<String, Object> lastYearRow = billMapper.selectMonthStat(
                openid, lastYear.atDay(1), lastYear.plusMonths(1).atDay(1));

        StatTrendDTO dto = new StatTrendDTO();
        dto.setRange(months);
        dto.setItems(items);
        dto.setExpenseYoy(changeRatio(
                items.get(months - 1).getExpense(),
                toDecimal(lastYearRow == null ? null : lastYearRow.get("expense"))));
        dto.setIncomeYoy(changeRatio(
                items.get(months - 1).getIncome(),
                toDecimal(lastYearRow == null ? null : lastYearRow.get("income"))));
        // 去年同月完全无账单（收入支出均为 0）时视为无对比基数
        if (lastYearRow == null
                || (toDecimal(lastYearRow.get("expense")).compareTo(BigDecimal.ZERO) == 0
                    && toDecimal(lastYearRow.get("income")).compareTo(BigDecimal.ZERO) == 0)) {
            dto.setExpenseYoy(null);
            dto.setIncomeYoy(null);
        }
        return dto;
    }

    /**
     * 变动比例：(current - base) / base，保留 4 位小数。
     * base 为 null 或 0 时无对比意义，返回 null（F-43 要求口径明确可校验，禁止除零）。
     */
    private BigDecimal changeRatio(BigDecimal current, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(base)
                .divide(base, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}