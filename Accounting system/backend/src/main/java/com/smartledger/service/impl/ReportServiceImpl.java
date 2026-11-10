package com.smartledger.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartledger.ai.DeepSeekClient;
import com.smartledger.ai.dto.DeepSeekResult;
import com.smartledger.ai.dto.ReportAiResponse;
import com.smartledger.common.util.DateUtils;
import com.smartledger.config.AiProperties;
import com.smartledger.dto.response.MonthReportDTO;
import com.smartledger.mapper.BillMapper;
import com.smartledger.service.AiParseLogService;
import com.smartledger.service.DictService;
import com.smartledger.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 月报服务实现。
 *
 * <p>铁律（F-44）：数字一律来自 SQL 聚合，模型只负责把「统计数据」写成自然语言，
 * 严禁让模型读明细自行算数；AI 异常时使用只含真实数字的规则兜底文案。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    /** 月报输出上限：比记账解析（256）宽松，允许完整的段落与建议 */
    private static final int REPORT_MAX_TOKENS = 900;
    /** 取支出分类前 N 名 */
    private static final int TOP_CATEGORY_LIMIT = 3;
    /** 单一分类占比超过该值时，兜底文案给出集中度提醒 */
    private static final BigDecimal CATEGORY_CONCENTRATION = new BigDecimal("0.40");
    /** 支出环比超过该值时，兜底文案给出上升提醒 */
    private static final BigDecimal EXPENSE_RISE = new BigDecimal("0.10");

    private final BillMapper billMapper;
    private final DictService dictService;
    private final DeepSeekClient deepSeekClient;
    private final AiProperties aiProperties;
    private final AiParseLogService aiParseLogService;
    private final ObjectMapper objectMapper;

    @Override
    public MonthReportDTO month(String openid, String month) {
        YearMonth yearMonth = DateUtils.parseMonth(month);
        MonthReportDTO.Stats stats = aggregateStats(openid, yearMonth);

        MonthReportDTO dto = new MonthReportDTO();
        dto.setMonth(DateUtils.formatMonth(yearMonth));
        dto.setAiEnabled(aiProperties.isEnabled());
        dto.setStats(stats);

        if (aiProperties.isEnabled()) {
            fillByAi(openid, dto, stats);
        } else {
            applyFallback(dto, stats);
        }
        return dto;
    }

    /* ---------------- 真实统计聚合 ---------------- */

    private MonthReportDTO.Stats aggregateStats(String openid, YearMonth yearMonth) {
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = start.plusMonths(1);

        Map<String, Object> row = billMapper.selectMonthStat(openid, start, end);
        BigDecimal income = toDecimal(row == null ? null : row.get("income"));
        BigDecimal expense = toDecimal(row == null ? null : row.get("expense"));
        long billCount = toLong(row == null ? null : row.get("billCount"));

        // 支出分类 Top3
        List<Map<String, Object>> categoryRows =
                billMapper.selectCategoryStat(openid, 0, start, end);
        Map<String, String> categoryNames = dictService.categoryNameMap();
        List<MonthReportDTO.Category> topCategories = new ArrayList<>();
        for (int i = 0; i < categoryRows.size() && i < TOP_CATEGORY_LIMIT; i++) {
            Map<String, Object> categoryRow = categoryRows.get(i);
            BigDecimal amount = toDecimal(categoryRow.get("amount"));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String code = String.valueOf(categoryRow.get("categoryCode"));
            MonthReportDTO.Category category = new MonthReportDTO.Category();
            category.setCategoryCode(code);
            category.setCategoryName(categoryNames.getOrDefault(code, code));
            category.setAmount(amount);
            category.setRatio(expense.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(expense, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            topCategories.add(category);
        }

        // 上月支出（环比基数）
        YearMonth prev = yearMonth.minusMonths(1);
        Map<String, Object> prevRow = billMapper.selectMonthStat(
                openid, prev.atDay(1), prev.plusMonths(1).atDay(1));
        BigDecimal prevExpense = toDecimal(prevRow == null ? null : prevRow.get("expense"));

        MonthReportDTO.Stats stats = new MonthReportDTO.Stats();
        stats.setIncome(income);
        stats.setExpense(expense);
        stats.setBalance(income.subtract(expense));
        stats.setBillCount(billCount);
        stats.setAvgExpense(billCount > 0
                ? expense.divide(BigDecimal.valueOf(billCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        stats.setExpenseMom(prevExpense.compareTo(BigDecimal.ZERO) > 0
                ? expense.subtract(prevExpense).divide(prevExpense, 4, RoundingMode.HALF_UP)
                : null);
        stats.setTopCategories(topCategories);
        return stats;
    }

    /* ---------------- AI 文字生成 ---------------- */

    private void fillByAi(String openid, MonthReportDTO dto, MonthReportDTO.Stats stats) {
        String systemPrompt = "你是个人记账助手「萌萌」，风格温暖、简洁、像朋友聊天。"
                + "请根据用户给出的【当月真实统计数据 JSON】生成月度财务报告。"
                + "硬性要求：1) 只能使用 JSON 中已有的数字，禁止编造任何金额、笔数或比例；"
                + "2) 比例字段（ratio/expenseMom）是小数，需乘以 100 后按百分比表述，保留 1 位小数；"
                + "3) 当月没有收支数据时，温柔地提示用户先记一笔，不要硬写结论；"
                + "4) 只输出一个 JSON 对象，格式为："
                + "{\"summary\":\"40到80字的一段话总结\","
                + "\"highlights\":[\"2到4条消费特征，每条不超过30字\"],"
                + "\"suggestions\":[\"2到4条具体可执行的省钱或记账建议，每条不超过30字\"]}。";

        String userText = null;
        long start = System.currentTimeMillis();
        try {
            userText = buildStatsPayload(dto.getMonth(), stats);
            DeepSeekResult result = deepSeekClient.chatContent(systemPrompt, userText, REPORT_MAX_TOKENS);
            ReportAiResponse ai = objectMapper.readValue(result.getContent(), ReportAiResponse.class);

            boolean valid = StringUtils.hasText(ai.getSummary())
                    && ai.getHighlights() != null && !ai.getHighlights().isEmpty()
                    && ai.getSuggestions() != null && !ai.getSuggestions().isEmpty();
            if (!valid) {
                throw new IllegalStateException("月报 JSON 缺少必要字段");
            }
            dto.setSummary(ai.getSummary().trim());
            dto.setHighlights(trimList(ai.getHighlights()));
            dto.setSuggestions(trimList(ai.getSuggestions()));
            dto.setAiGenerated(true);

            aiParseLogService.record(openid, "[REPORT] " + userText, result.getContent(),
                    aiProperties.getModel(), true, null, null,
                    System.currentTimeMillis() - start,
                    result.getPromptTokens(), result.getCompletionTokens());
        } catch (Exception e) {
            log.warn("AI 月报生成失败，使用兜底文案: {}", e.getMessage());
            applyFallback(dto, stats);
            aiParseLogService.record(openid, "[REPORT] " + (userText == null ? dto.getMonth() : userText),
                    null, aiProperties.getModel(), false,
                    "REPORT_FALLBACK", e.getMessage(), System.currentTimeMillis() - start, null, null);
        }
    }

    /** 组装给模型的真实统计数据（只含权威数字，不放明细） */
    private String buildStatsPayload(String month, MonthReportDTO.Stats stats) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("month", month);
        payload.put("income", stats.getIncome());
        payload.put("expense", stats.getExpense());
        payload.put("balance", stats.getBalance());
        payload.put("billCount", stats.getBillCount());
        payload.put("avgExpense", stats.getAvgExpense());
        payload.put("expenseMom", stats.getExpenseMom());

        List<Map<String, Object>> top = new ArrayList<>();
        for (MonthReportDTO.Category category : stats.getTopCategories()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("categoryName", category.getCategoryName());
            item.put("amount", category.getAmount());
            item.put("ratio", category.getRatio());
            top.add(item);
        }
        payload.put("topExpenseCategories", top);
        return objectMapper.writeValueAsString(payload);
    }

    /* ---------------- 规则兜底文案（数字同样真实） ---------------- */

    private void applyFallback(MonthReportDTO dto, MonthReportDTO.Stats stats) {
        dto.setAiGenerated(false);

        if (stats.getBillCount() == 0) {
            dto.setSummary("这个月还没有记账记录，先记一笔，萌萌才能帮你做月度分析哦。");
            dto.setHighlights(List.of("本月暂无账单数据"));
            dto.setSuggestions(List.of("从今天的一顿饭开始记下第一笔", "晚上花一分钟回顾当天的花销"));
            return;
        }

        List<String> highlights = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        highlights.add("本月共记账 " + stats.getBillCount() + " 笔，支出 ¥" + money(stats.getExpense()));

        String topText;
        if (!stats.getTopCategories().isEmpty()) {
            MonthReportDTO.Category top = stats.getTopCategories().get(0);
            highlights.add("支出最多的是「" + top.getCategoryName() + "」，花了 ¥"
                    + money(top.getAmount()) + "，占总支出 " + percent(top.getRatio()));
            if (top.getRatio().compareTo(CATEGORY_CONCENTRATION) >= 0) {
                suggestions.add(top.getCategoryName() + " 占比偏高，下个月试着设个分类预算");
            }
            topText = top.getCategoryName();
        } else {
            topText = "";
        }

        if (stats.getExpenseMom() != null) {
            String trend = stats.getExpenseMom().compareTo(BigDecimal.ZERO) >= 0 ? "上升" : "下降";
            highlights.add("支出环比" + trend + " " + percent(stats.getExpenseMom().abs()));
            if (stats.getExpenseMom().compareTo(EXPENSE_RISE) >= 0) {
                suggestions.add("本月支出环比上涨较快，留意大额非必要消费");
            }
        }

        if (stats.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            suggestions.add("本月结余为负，建议先压缩弹性开销再安排娱乐");
        } else {
            suggestions.add("保持记账习惯，月底复盘会更清楚钱花在哪");
        }
        if (!"".equals(topText) && suggestions.size() < 2) {
            suggestions.add("可以用预算功能给「" + topText + "」设一条月度上限");
        }

        dto.setSummary("本月收入 ¥" + money(stats.getIncome())
                + "，支出 ¥" + money(stats.getExpense())
                + "，结余 ¥" + money(stats.getBalance())
                + "。" + (stats.getExpenseMom() != null
                    ? "支出环比" + (stats.getExpenseMom().compareTo(BigDecimal.ZERO) >= 0 ? "上升 " : "下降 ")
                        + percent(stats.getExpenseMom().abs()) + "。"
                    : ""));
        dto.setHighlights(highlights);
        dto.setSuggestions(suggestions);
    }

    private List<String> trimList(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String item : source) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 0.1234 → "12.3%" */
    private String percent(BigDecimal ratio) {
        return ratio.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
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
