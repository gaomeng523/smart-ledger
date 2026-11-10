package com.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartledger.ai.DeepSeekClient;
import com.smartledger.ai.dto.DeepSeekResult;
import com.smartledger.common.util.DateUtils;
import com.smartledger.config.AiProperties;
import com.smartledger.dto.response.BudgetDTO;
import com.smartledger.dto.response.InsightDTO;
import com.smartledger.dto.response.SubscriptionDTO;
import com.smartledger.entity.Bill;
import com.smartledger.entity.Subscription;
import com.smartledger.mapper.BillMapper;
import com.smartledger.service.BudgetService;
import com.smartledger.service.DictService;
import com.smartledger.service.InsightService;
import com.smartledger.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 消费洞察服务实现（V3）。
 *
 * <p>铁律同月报：所有数字来自 SQL/账单聚合，模型只允许改写措辞；
 * AI 不可用时降级为规则模板文案。记账后洞察不调用 AI，保证速度与稳定。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightServiceImpl implements InsightService {

    private static final int AFTER_SAVE_LIMIT = 2;
    private static final int MONTH_LIMIT = 6;
    private static final int HISTORY_DAYS = 90;
    private static final int OUTLIER_MIN_SAMPLES = 4;

    private static final String LV_DANGER = "danger";
    private static final String LV_WARNING = "warning";
    private static final String LV_INFO = "info";

    private static final Pattern MONEY_PATTERN = Pattern.compile("¥\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)%");

    private final BillMapper billMapper;
    private final BudgetService budgetService;
    private final SubscriptionService subscriptionService;
    private final DictService dictService;
    private final DeepSeekClient deepSeekClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    /* ============================================================
     * 记账后即时洞察
     * ============================================================ */

    @Override
    public List<InsightDTO> afterSave(String openid, Long billId) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null || !openid.equals(bill.getOpenid())
                || bill.getBillType() == null || bill.getBillType() == 1) {
            return List.of();
        }

        List<InsightDTO> insights = new ArrayList<>();

        // 1. 订阅续费匹配（内部完成扣费日顺延）
        try {
            Subscription matched = subscriptionService.consumeBill(openid, bill);
            if (matched != null) {
                insights.add(new InsightDTO("subscriptionMatched", LV_INFO,
                        "识别为续费扣费",
                        "这笔像是「" + matched.getTitle() + "」的续费，下次预计 "
                                + DateUtils.format(matched.getNextDate()) + " 扣费 ¥"
                                + money(matched.getAvgAmount())));
            }
        } catch (Exception e) {
            log.warn("订阅续费匹配失败 billId={}: {}", billId, e.getMessage());
        }

        // 2. 单笔离群：本笔 > 同分类历史中位数 * 2.5
        BigDecimal median = categoryMedianBefore(openid, bill.getCategoryCode(), bill.getRecordDate(), billId);
        if (median != null && median.compareTo(BigDecimal.ZERO) > 0
                && bill.getAmount().compareTo(median.multiply(new BigDecimal("2.5"))) > 0) {
            insights.add(new InsightDTO("outlier", LV_WARNING,
                    "这笔比平时贵不少",
                    "你平时「" + dictService.categoryName(bill.getCategoryCode()) + "」的中位数约 ¥"
                            + money(median) + "，这笔花了 ¥" + money(bill.getAmount())));
        }

        // 3. 本周该分类支出环比上周
        InsightDTO weekSurge = weekSurgeInsight(openid, bill);
        if (weekSurge != null) {
            insights.add(weekSurge);
        }

        // 4. 预算预警 / 超支（保存前 = 当前已用 - 本笔）
        String month = bill.getRecordDate().getYear() + "-"
                + String.format("%02d", bill.getRecordDate().getMonthValue());
        appendBudgetInsights(insights, openid, month, bill, true);

        // 5. 同日同分类重复（含本笔达到 3 笔）
        Long sameDayCount = billMapper.selectCount(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getOpenid, openid)
                .eq(Bill::getBillType, 0)
                .eq(Bill::getCategoryCode, bill.getCategoryCode())
                .eq(Bill::getRecordDate, bill.getRecordDate()));
        if (sameDayCount != null && sameDayCount >= 3) {
            insights.add(new InsightDTO("repeat", LV_INFO,
                    "今天「" + dictService.categoryName(bill.getCategoryCode()) + "」已记 " + sameDayCount + " 笔",
                    "确认一下有没有重复记账哦"));
        }

        insights.sort(Comparator.comparingInt(this::levelRank));
        return insights.size() > AFTER_SAVE_LIMIT ? insights.subList(0, AFTER_SAVE_LIMIT) : insights;
    }

    /** 同分类近 90 天（不含本笔）金额中位数；样本不足返回 null */
    private BigDecimal categoryMedianBefore(String openid, String categoryCode,
                                            LocalDate date, Long excludeBillId) {
        List<Bill> history = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getOpenid, openid)
                .eq(Bill::getBillType, 0)
                .eq(Bill::getCategoryCode, categoryCode)
                .ge(Bill::getRecordDate, date.minusDays(HISTORY_DAYS))
                .lt(Bill::getRecordDate, date));
        List<BigDecimal> amounts = history.stream()
                .filter(b -> !b.getId().equals(excludeBillId))
                .map(Bill::getAmount)
                .sorted()
                .collect(Collectors.toList());
        if (amounts.size() < OUTLIER_MIN_SAMPLES) {
            return null;
        }
        int mid = amounts.size() / 2;
        return amounts.size() % 2 == 1
                ? amounts.get(mid)
                : amounts.get(mid - 1).add(amounts.get(mid)).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    /** 本周（周一起）同分类含本笔的支出，明显超过上周整周时提示 */
    private InsightDTO weekSurgeInsight(String openid, Bill bill) {
        LocalDate monday = bill.getRecordDate().with(DayOfWeek.MONDAY);
        LocalDate nextMonday = monday.plusWeeks(1);
        LocalDate prevMonday = monday.minusWeeks(1);

        BigDecimal thisWeek = sumCategory(openid, bill.getCategoryCode(), monday, nextMonday);
        BigDecimal lastWeek = sumCategory(openid, bill.getCategoryCode(), prevMonday, monday);
        if (lastWeek.compareTo(new BigDecimal("20")) <= 0) {
            return null;
        }
        BigDecimal diff = thisWeek.subtract(lastWeek);
        if (thisWeek.compareTo(lastWeek.multiply(new BigDecimal("1.5"))) >= 0
                && diff.compareTo(new BigDecimal("20")) >= 0) {
            BigDecimal ratio = diff.divide(lastWeek, 4, RoundingMode.HALF_UP);
            return new InsightDTO("weekSurge", LV_WARNING,
                    "本周「" + dictService.categoryName(bill.getCategoryCode()) + "」花得有点快",
                    "本周已花 ¥" + money(thisWeek) + "，上周整周 ¥" + money(lastWeek)
                            + "，多了 " + percent(ratio));
        }
        return null;
    }

    private BigDecimal sumCategory(String openid, String categoryCode, LocalDate start, LocalDate end) {
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .select(Bill::getAmount)
                .eq(Bill::getOpenid, openid)
                .eq(Bill::getBillType, 0)
                .eq(Bill::getCategoryCode, categoryCode)
                .ge(Bill::getRecordDate, start)
                .lt(Bill::getRecordDate, end));
        return bills.stream().map(Bill::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /* ============================================================
     * 月度洞察
     * ============================================================ */

    @Override
    public List<InsightDTO> monthInsights(String openid, String month) {
        YearMonth yearMonth = DateUtils.parseMonth(month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = start.plusMonths(1);
        LocalDate today = LocalDate.now();
        // 当月只统计到今天；历史月整月
        LocalDate endCap = end.isAfter(today.plusDays(1)) ? today.plusDays(1) : end;

        List<Bill> monthBills = listExpense(openid, start, endCap);
        List<InsightDTO> insights = new ArrayList<>();

        // 1. 预算
        appendBudgetInsights(insights, openid, DateUtils.formatMonth(yearMonth), null, false);

        // 2. 分类支出 vs 上月同期
        LocalDate prevStart = start.minusMonths(1);
        LocalDate prevEndCap = endCap.minusMonths(1);
        Map<String, BigDecimal> currByCategory = sumByCategory(monthBills);
        Map<String, BigDecimal> prevByCategory =
                sumByCategory(listExpense(openid, prevStart, prevEndCap));
        InsightDTO compare = strongestCategoryRise(currByCategory, prevByCategory);
        if (compare != null) {
            insights.add(compare);
        }

        // 3. 高频小额
        Map<String, List<Bill>> byCategory = monthBills.stream()
                .collect(Collectors.groupingBy(Bill::getCategoryCode));
        byCategory.forEach((code, bills) -> {
            long count = bills.size();
            if (count < 8) {
                return;
            }
            BigDecimal avg = bills.stream().map(Bill::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            if (avg.compareTo(new BigDecimal("30")) <= 0) {
                insights.add(new InsightDTO("highFreq", LV_INFO,
                        "「" + dictService.categoryName(code) + "」小钱花了 " + count + " 笔",
                        "本月共 " + count + " 笔、合计 ¥" + money(currByCategory.get(code))
                                + "，小额高频最容易被忽略"));
            }
        });

        // 4. 本月最大单笔离群
        appendBigBill(openid, monthBills, insights);

        // 5. 本月订阅预计支出
        appendSubscriptionForecast(openid, start, end, insights);

        insights.sort(Comparator.comparingInt(this::levelRank));
        List<InsightDTO> limited = insights.size() > MONTH_LIMIT
                ? insights.subList(0, MONTH_LIMIT) : insights;

        if (aiProperties.isEnabled() && !limited.isEmpty()) {
            return polishByAi(limited);
        }
        return limited;
    }

    private List<Bill> listExpense(String openid, LocalDate start, LocalDate end) {
        if (!end.isAfter(start)) {
            return List.of();
        }
        return billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getOpenid, openid)
                .eq(Bill::getBillType, 0)
                .ge(Bill::getRecordDate, start)
                .lt(Bill::getRecordDate, end)
                .orderByAsc(Bill::getRecordDate));
    }

    private Map<String, BigDecimal> sumByCategory(List<Bill> bills) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Bill bill : bills) {
            map.merge(bill.getCategoryCode(), bill.getAmount(), BigDecimal::add);
        }
        return map;
    }

    private InsightDTO strongestCategoryRise(Map<String, BigDecimal> curr, Map<String, BigDecimal> prev) {
        String bestCode = null;
        BigDecimal bestDiff = null;
        BigDecimal bestRatio = null;
        boolean isNew = false;
        for (Map.Entry<String, BigDecimal> entry : curr.entrySet()) {
            BigDecimal currAmount = entry.getValue();
            BigDecimal prevAmount = prev.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal diff = currAmount.subtract(prevAmount);
            if (prevAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = diff.divide(prevAmount, 4, RoundingMode.HALF_UP);
                if (diff.compareTo(new BigDecimal("50")) >= 0
                        && ratio.compareTo(new BigDecimal("0.5")) >= 0
                        && (bestDiff == null || diff.compareTo(bestDiff) > 0)) {
                    bestCode = entry.getKey();
                    bestDiff = diff;
                    bestRatio = ratio;
                    isNew = false;
                }
            } else if (currAmount.compareTo(new BigDecimal("200")) >= 0
                    && (bestDiff == null || currAmount.compareTo(bestDiff) > 0)) {
                bestCode = entry.getKey();
                bestDiff = currAmount;
                bestRatio = null;
                isNew = true;
            }
        }
        if (bestCode == null) {
            return null;
        }
        String name = dictService.categoryName(bestCode);
        if (isNew) {
            return new InsightDTO("monthCompare", LV_WARNING,
                    "本月新增了「" + name + "」开支",
                    "上月同期没有这项支出，本月已花 ¥" + money(bestDiff));
        }
        return new InsightDTO("monthCompare", LV_WARNING,
                "「" + name + "」比上月同期花得多",
                "多花 ¥" + money(bestDiff) + "（+" + percent(bestRatio) + "），留意是不是本月特殊情况");
    }

    private void appendBigBill(String openid, List<Bill> monthBills, List<InsightDTO> insights) {
        // 近 90 天各分类中位数
        LocalDate today = LocalDate.now();
        List<Bill> history = listExpense(openid, today.minusDays(HISTORY_DAYS), today.plusDays(1));
        Map<String, List<BigDecimal>> amountsByCategory = new HashMap<>();
        for (Bill bill : history) {
            amountsByCategory.computeIfAbsent(bill.getCategoryCode(), k -> new ArrayList<>()).add(bill.getAmount());
        }

        Bill biggest = null;
        for (Bill bill : monthBills) {
            List<BigDecimal> samples = amountsByCategory.get(bill.getCategoryCode());
            if (samples == null || samples.size() < OUTLIER_MIN_SAMPLES) {
                continue;
            }
            BigDecimal median = median(samples);
            if (median.compareTo(BigDecimal.ZERO) > 0
                    && bill.getAmount().compareTo(median.multiply(new BigDecimal("3"))) > 0) {
                if (biggest == null || bill.getAmount().compareTo(biggest.getAmount()) > 0) {
                    biggest = bill;
                }
            }
        }
        if (biggest != null) {
            insights.add(new InsightDTO("bigBill", LV_WARNING,
                    "本月有一笔不寻常的大额支出",
                    DateUtils.format(biggest.getRecordDate()) + "「"
                            + dictService.categoryName(biggest.getCategoryCode()) + "」¥"
                            + money(biggest.getAmount()) + "，明显高于你平时的水平"));
        }
    }

    private BigDecimal median(List<BigDecimal> source) {
        List<BigDecimal> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.naturalOrder());
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(mid)
                : sorted.get(mid - 1).add(sorted.get(mid)).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    private void appendSubscriptionForecast(String openid, LocalDate start, LocalDate end,
                                            List<InsightDTO> insights) {
        List<SubscriptionDTO> subs;
        try {
            subs = subscriptionService.list(openid);
        } catch (Exception e) {
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        long count = 0;
        for (SubscriptionDTO sub : subs) {
            if (sub.getStatus() == null || sub.getStatus() != 1) {
                continue;
            }
            LocalDate next = DateUtils.parseDate(sub.getNextDate());
            if (next == null) {
                continue;
            }
            // 月付按月计一次；季付/年付仅当扣费日落在本月
            if (sub.getCycleDays() != null && sub.getCycleDays() == 30) {
                total = total.add(sub.getAvgAmount());
                count++;
            } else if (!next.isBefore(start) && next.isBefore(end)) {
                total = total.add(sub.getAvgAmount());
                count++;
            }
        }
        if (count > 0) {
            insights.add(new InsightDTO("subscription", LV_INFO,
                    "本月预计 " + count + " 笔订阅扣费",
                    "会员/订阅类支出预计约 ¥" + money(total) + "，可在订阅中心管理"));
        }
    }

    /* ============================================================
     * 预算洞察（afterSave 与 month 复用）
     * ============================================================ */

    private void appendBudgetInsights(List<InsightDTO> insights, String openid, String month,
                                      Bill justSaved, boolean crossOnly) {
        BudgetDTO budget;
        try {
            budget = budgetService.get(openid, month);
        } catch (Exception e) {
            return;
        }
        if (budget == null) {
            return;
        }

        // 总预算
        if (budget.getTotalBudget() != null && budget.getTotalBudget().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal after = budget.getTotalUsed();
            BigDecimal before = justSaved == null ? after : after.subtract(justSaved.getAmount());
            addBudgetInsight(insights, "本月总预算", budget.getTotalBudget(), after, before,
                    new BigDecimal("0.80"), crossOnly);
        }

        // 分类预算：只挑最严重的一条
        InsightDTO worst = null;
        for (BudgetDTO.Item item : budget.getItems()) {
            if (justSaved != null && !item.getCategoryCode().equals(justSaved.getCategoryCode())) {
                continue;
            }
            BigDecimal after = item.getUsed();
            BigDecimal before = justSaved == null ? after : after.subtract(justSaved.getAmount());
            List<InsightDTO> tmp = new ArrayList<>();
            addBudgetInsight(tmp, "「" + item.getCategoryName() + "」预算",
                    item.getAmount(), after, before, item.getWarnRatio(), crossOnly);
            if (!tmp.isEmpty() && (worst == null || levelRank(tmp.get(0)) < levelRank(worst))) {
                worst = tmp.get(0);
            }
        }
        if (worst != null) {
            insights.add(worst);
        }
    }

    /**
     * 生成预算提示。
     *
     * @param crossOnly afterSave 场景只在"保存动作导致跨阈值"时提示，避免每笔都打扰
     */
    private void addBudgetInsight(List<InsightDTO> insights, String label,
                                  BigDecimal budgetAmount, BigDecimal after, BigDecimal before,
                                  BigDecimal warnRatio, boolean crossOnly) {
        if (budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal afterRatio = after.divide(budgetAmount, 4, RoundingMode.HALF_UP);
        BigDecimal beforeRatio = before.divide(budgetAmount, 4, RoundingMode.HALF_UP);
        BigDecimal warn = warnRatio == null ? new BigDecimal("0.80") : warnRatio;

        if (afterRatio.compareTo(BigDecimal.ONE) >= 0
                && (!crossOnly || beforeRatio.compareTo(BigDecimal.ONE) < 0)) {
            insights.add(new InsightDTO("budgetOver", LV_DANGER,
                    label + "已超支",
                    "目前已用 ¥" + money(after) + "，超出预算 ¥" + money(after.subtract(budgetAmount))));
        } else if (afterRatio.compareTo(warn) >= 0
                && (!crossOnly || beforeRatio.compareTo(warn) < 0)) {
            insights.add(new InsightDTO("budgetWarn", LV_WARNING,
                    label + "快到上限了",
                    "已用 " + percent(afterRatio) + "（¥" + money(after) + " / ¥"
                            + money(budgetAmount) + "）"));
        }
    }

    /* ============================================================
     * AI 措辞润色（月度，失败自动降级）
     * ============================================================ */

    private List<InsightDTO> polishByAi(List<InsightDTO> originals) {
        String systemPrompt = "你是个人记账助手「萌萌」，风格温暖、简洁、像朋友提醒。"
                + "用户会给出若干条消费洞察 JSON（含 level 与 body）。"
                + "请在不改变含义的前提下把每条改写得更自然温柔。"
                + "硬性要求：1) body 中所有 ¥ 金额、百分比、笔数必须原样保留，禁止新增或修改任何数字；"
                + "2) 条目数量与顺序必须与输入一致，通过 index 对应；"
                + "3) title 不超过 16 字，body 不超过 45 字；"
                + "4) 只输出 JSON：{\"items\":[{\"index\":0,\"title\":\"\",\"body\":\"\"}]}";
        try {
            List<Map<String, Object>> payloadItems = new ArrayList<>();
            for (int i = 0; i < originals.size(); i++) {
                InsightDTO item = originals.get(i);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("index", i);
                map.put("level", item.getLevel());
                map.put("title", item.getTitle());
                map.put("body", item.getBody());
                payloadItems.add(map);
            }
            DeepSeekResult result = deepSeekClient.chatContent(
                    systemPrompt, objectMapper.writeValueAsString(payloadItems), 700);
            JsonNode root = objectMapper.readTree(result.getContent());
            JsonNode itemsNode = root.path("items");
            if (!itemsNode.isArray() || itemsNode.size() != originals.size()) {
                return originals;
            }
            List<InsightDTO> polished = new ArrayList<>();
            for (int i = 0; i < originals.size(); i++) {
                JsonNode node = itemsNode.get(i);
                String title = node.path("title").asText("").trim();
                String body = node.path("body").asText("").trim();
                InsightDTO origin = originals.get(i);
                if (!StringUtils.hasText(title) || !StringUtils.hasText(body)
                        || !moneyNumbersKept(origin.getBody(), body)) {
                    return originals;
                }
                polished.add(new InsightDTO(origin.getType(), origin.getLevel(), title, body));
            }
            return polished;
        } catch (Exception e) {
            log.warn("洞察 AI 润色失败，使用规则原文: {}", e.getMessage());
            return originals;
        }
    }

    /** 校验原文案中所有 ¥ 金额、百分比数字都原样出现在新文案中（允许去尾零，如 30.00→30） */
    private boolean moneyNumbersKept(String originBody, String newBody) {
        if (!allNumbersKept(MONEY_PATTERN, originBody, newBody)) {
            return false;
        }
        return allNumbersKept(PERCENT_PATTERN, originBody, newBody);
    }

    private boolean allNumbersKept(Pattern pattern, String originBody, String newBody) {
        Matcher matcher = pattern.matcher(originBody);
        while (matcher.find()) {
            String raw = matcher.group(1);
            String normalized = new BigDecimal(raw).stripTrailingZeros().toPlainString();
            if (!newBody.contains(raw) && !newBody.contains(normalized)) {
                return false;
            }
        }
        return true;
    }

    /* ============================================================
     * 辅助
     * ============================================================ */

    private int levelRank(InsightDTO dto) {
        return switch (dto.getLevel()) {
            case LV_DANGER -> 0;
            case LV_WARNING -> 1;
            default -> 2;
        };
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String percent(BigDecimal ratio) {
        return ratio.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
    }
}
