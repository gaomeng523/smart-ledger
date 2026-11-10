package com.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.util.DateUtils;
import com.smartledger.dto.request.SubscriptionSaveRequest;
import com.smartledger.dto.request.SubscriptionUpdateRequest;
import com.smartledger.dto.response.SubscriptionCandidateDTO;
import com.smartledger.dto.response.SubscriptionDTO;
import com.smartledger.entity.Bill;
import com.smartledger.entity.Subscription;
import com.smartledger.mapper.BillMapper;
import com.smartledger.mapper.SubscriptionMapper;
import com.smartledger.service.DictService;
import com.smartledger.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 周期订阅服务实现（V3）。
 *
 * <p>识别策略：近 180 天支出账单按「分类 + 渠道 + 整数金额桶」聚类，
 * 组内相邻扣费间隔命中 月 [24,40] / 季 [80,100] / 年 [340,390] 天即判定为周期订阅。
 * 候选只做提示，用户确认后才入库。</p>
 */
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    /** 扫描历史账单的天数 */
    private static final int SCAN_DAYS = 180;
    /** 候选最大返回数 */
    private static final int CANDIDATE_LIMIT = 10;
    /** 候选组最少笔数 */
    private static final int MIN_OCCURRENCES = 2;

    private static final int CYCLE_MONTH = 30;
    private static final int CYCLE_QUARTER = 90;
    private static final int CYCLE_YEAR = 365;

    private final SubscriptionMapper subscriptionMapper;
    private final BillMapper billMapper;
    private final DictService dictService;

    /* ============================================================
     * 列表
     * ============================================================ */

    @Override
    public List<SubscriptionDTO> list(String openid) {
        List<Subscription> list = subscriptionMapper.selectList(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getOpenid, openid)
                .orderByAsc(Subscription::getNextDate));
        List<SubscriptionDTO> result = new ArrayList<>();
        for (Subscription sub : list) {
            result.add(toDTO(sub));
        }
        return result;
    }

    /* ============================================================
     * 识别候选
     * ============================================================ */

    @Override
    public List<SubscriptionCandidateDTO> suggest(String openid) {
        LocalDate today = LocalDate.now();
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getOpenid, openid)
                .eq(Bill::getBillType, 0)
                .ge(Bill::getRecordDate, today.minusDays(SCAN_DAYS))
                .orderByAsc(Bill::getRecordDate));

        // 分组：分类 | 渠道 | 整数金额桶
        Map<String, List<Bill>> groups = new LinkedHashMap<>();
        for (Bill bill : bills) {
            String key = bill.getCategoryCode() + "|" + bill.getPayChannelCode() + "|"
                    + bill.getAmount().setScale(0, RoundingMode.HALF_UP).toPlainString();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(bill);
        }

        List<Subscription> existed = subscriptionMapper.selectList(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getOpenid, openid));

        List<SubscriptionCandidateDTO> candidates = new ArrayList<>();
        for (List<Bill> group : groups.values()) {
            if (group.size() < MIN_OCCURRENCES) {
                continue;
            }
            Integer cycle = detectCycle(group);
            if (cycle == null) {
                continue;
            }
            BigDecimal avgAmount = averageAmount(group);
            Bill first = group.get(0);
            if (matchesExisted(existed, first.getCategoryCode(), first.getPayChannelCode(), avgAmount)) {
                continue;
            }

            SubscriptionCandidateDTO dto = new SubscriptionCandidateDTO();
            dto.setTitle(pickTitle(group, first.getCategoryCode()));
            dto.setCategoryCode(first.getCategoryCode());
            dto.setCategoryName(dictService.categoryName(first.getCategoryCode()));
            dto.setPayChannelCode(first.getPayChannelCode());
            dto.setPayChannelName(dictService.channelName(first.getPayChannelCode()));
            dto.setAvgAmount(avgAmount);
            dto.setCycleDays(cycle);
            dto.setCycleText(cycleText(cycle));
            dto.setNextDate(DateUtils.format(group.get(group.size() - 1).getRecordDate().plusDays(cycle)));
            dto.setOccurrences(group.size());
            candidates.add(dto);
        }

        // 即将/已到扣费日的候选排前面
        candidates.sort(Comparator.comparing(SubscriptionCandidateDTO::getNextDate));
        return candidates.size() > CANDIDATE_LIMIT ? candidates.subList(0, CANDIDATE_LIMIT) : candidates;
    }

    /**
     * 根据相邻扣费间隔判定周期：统计月/季/年窗口的命中次数，取命中最多者；
     * 同票时优先月付（短周期对用户更有提醒价值）。
     */
    private Integer detectCycle(List<Bill> group) {
        int monthHits = 0;
        int quarterHits = 0;
        int yearHits = 0;
        for (int i = 1; i < group.size(); i++) {
            long gap = ChronoUnit.DAYS.between(group.get(i - 1).getRecordDate(), group.get(i).getRecordDate());
            if (gap >= 24 && gap <= 40) {
                monthHits++;
            } else if (gap >= 80 && gap <= 100) {
                quarterHits++;
            } else if (gap >= 340 && gap <= 390) {
                yearHits++;
            }
        }
        if (monthHits == 0 && quarterHits == 0 && yearHits == 0) {
            return null;
        }
        if (monthHits >= quarterHits && monthHits >= yearHits) {
            return CYCLE_MONTH;
        }
        if (quarterHits >= yearHits) {
            return CYCLE_QUARTER;
        }
        return CYCLE_YEAR;
    }

    private BigDecimal averageAmount(List<Bill> group) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Bill bill : group) {
            sum = sum.add(bill.getAmount());
        }
        return sum.divide(BigDecimal.valueOf(group.size()), 2, RoundingMode.HALF_UP);
    }

    /** 候选名称：组内出现次数最多的非空备注；没有则用「分类 + 订阅」 */
    private String pickTitle(List<Bill> group, String categoryCode) {
        Map<String, Integer> freq = new HashMap<>();
        for (Bill bill : group) {
            String remark = bill.getRemark();
            if (!StringUtils.hasText(remark)) {
                remark = bill.getRecordText();
            }
            if (StringUtils.hasText(remark)) {
                String key = remark.trim();
                if (key.length() > 12) {
                    key = key.substring(0, 12);
                }
                freq.merge(key, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(dictService.categoryName(categoryCode) + "订阅");
    }

    private boolean matchesExisted(List<Subscription> existed, String categoryCode,
                                   String channelCode, BigDecimal amount) {
        for (Subscription sub : existed) {
            if (!sub.getCategoryCode().equals(categoryCode)
                    || !sub.getPayChannelCode().equals(channelCode)) {
                continue;
            }
            BigDecimal tolerance = sub.getAvgAmount().multiply(new BigDecimal("0.20"))
                    .max(new BigDecimal("3"));
            if (sub.getAvgAmount().subtract(amount).abs().compareTo(tolerance) <= 0) {
                return true;
            }
        }
        return false;
    }

    /* ============================================================
     * 新增 / 修改 / 删除
     * ============================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionDTO create(String openid, SubscriptionSaveRequest request) {
        validateRefs(request.getCategoryCode(), request.getPayChannelCode());
        int cycleDays = normalizeCycleDays(request.getCycleDays());
        LocalDate nextDate = StringUtils.hasText(request.getNextDate())
                ? parseDateOrThrow(request.getNextDate())
                : LocalDate.now().plusDays(cycleDays);

        Subscription sub = new Subscription();
        sub.setOpenid(openid);
        sub.setTitle(request.getTitle().trim());
        sub.setCategoryCode(request.getCategoryCode());
        sub.setPayChannelCode(request.getPayChannelCode());
        sub.setAvgAmount(request.getAvgAmount());
        sub.setCycleDays(cycleDays);
        sub.setNextDate(nextDate);
        sub.setSampleCount(0);
        sub.setStatus(1);
        sub.setDeleted(0);
        subscriptionMapper.insert(sub);
        return toDTO(sub);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionDTO update(String openid, SubscriptionUpdateRequest request) {
        Subscription sub = requireOwned(openid, request.getId());
        int cycleDays = normalizeCycleDays(request.getCycleDays());
        LocalDate nextDate = parseDateOrThrow(request.getNextDate());
        int status = request.getStatus() == 0 ? 0 : 1;

        sub.setTitle(request.getTitle().trim());
        sub.setAvgAmount(request.getAvgAmount());
        sub.setCycleDays(cycleDays);
        sub.setNextDate(nextDate);
        sub.setStatus(status);
        subscriptionMapper.updateById(sub);
        return toDTO(sub);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String openid, Long id) {
        requireOwned(openid, id);
        subscriptionMapper.deleteById(id);
    }

    /* ============================================================
     * 记账后滚动扣费日
     * ============================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Subscription consumeBill(String openid, Bill bill) {
        if (bill == null || (bill.getBillType() != null && bill.getBillType() == 1)) {
            return null;
        }
        List<Subscription> subs = subscriptionMapper.selectList(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getOpenid, openid)
                .eq(Subscription::getStatus, 1)
                .eq(Subscription::getCategoryCode, bill.getCategoryCode())
                .eq(Subscription::getPayChannelCode, bill.getPayChannelCode()));

        Subscription matched = null;
        for (Subscription sub : subs) {
            BigDecimal tolerance = sub.getAvgAmount().multiply(new BigDecimal("0.15"))
                    .max(new BigDecimal("2"));
            if (sub.getAvgAmount().subtract(bill.getAmount()).abs().compareTo(tolerance) > 0) {
                continue;
            }
            if (matched == null || Math.abs(ChronoUnit.DAYS.between(sub.getNextDate(), bill.getRecordDate()))
                    < Math.abs(ChronoUnit.DAYS.between(matched.getNextDate(), bill.getRecordDate()))) {
                matched = sub;
            }
        }
        if (matched == null) {
            return null;
        }

        // 账单日期已接近或跨过扣费日（宽限 10 天），顺延一个周期；过早的账单不顺延
        if (!bill.getRecordDate().isBefore(matched.getNextDate().minusDays(10))) {
            BigDecimal smoothed = matched.getAvgAmount().multiply(new BigDecimal("0.7"))
                    .add(bill.getAmount().multiply(new BigDecimal("0.3")))
                    .setScale(2, RoundingMode.HALF_UP);
            matched.setAvgAmount(smoothed);
            matched.setNextDate(bill.getRecordDate().plusDays(matched.getCycleDays()));
            matched.setSampleCount((matched.getSampleCount() == null ? 0 : matched.getSampleCount()) + 1);
            matched.setLastBillId(bill.getId());
            subscriptionMapper.updateById(matched);
        }
        return matched;
    }

    /* ============================================================
     * 辅助
     * ============================================================ */

    private Subscription requireOwned(String openid, Long id) {
        Subscription sub = subscriptionMapper.selectById(id);
        if (sub == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订阅不存在");
        }
        if (!openid.equals(sub.getOpenid())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该订阅");
        }
        return sub;
    }

    private void validateRefs(String categoryCode, String channelCode) {
        if (dictService.getCategory(categoryCode) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类不存在: " + categoryCode);
        }
        if (dictService.getChannel(channelCode) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付渠道不存在: " + channelCode);
        }
    }

    private int normalizeCycleDays(Integer cycleDays) {
        if (cycleDays == null) {
            return CYCLE_MONTH;
        }
        if (cycleDays == CYCLE_QUARTER || cycleDays == CYCLE_YEAR) {
            return cycleDays;
        }
        return CYCLE_MONTH;
    }

    private LocalDate parseDateOrThrow(String text) {
        LocalDate date = DateUtils.parseDate(text);
        if (date == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
        return date;
    }

    private String cycleText(int cycleDays) {
        if (cycleDays == CYCLE_QUARTER) {
            return "季付";
        }
        if (cycleDays == CYCLE_YEAR) {
            return "年付";
        }
        return "月付";
    }

    private SubscriptionDTO toDTO(Subscription sub) {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setId(sub.getId());
        dto.setTitle(sub.getTitle());
        dto.setCategoryCode(sub.getCategoryCode());
        dto.setCategoryName(dictService.categoryName(sub.getCategoryCode()));
        dto.setPayChannelCode(sub.getPayChannelCode());
        dto.setPayChannelName(dictService.channelName(sub.getPayChannelCode()));
        dto.setAvgAmount(sub.getAvgAmount());
        dto.setCycleDays(sub.getCycleDays());
        dto.setCycleText(cycleText(sub.getCycleDays()));
        dto.setNextDate(DateUtils.format(sub.getNextDate()));
        long days = ChronoUnit.DAYS.between(LocalDate.now(), sub.getNextDate());
        dto.setDaysToNext(days);
        if (sub.getStatus() != null && sub.getStatus() == 0) {
            dto.setDueState("paused");
        } else if (days < 0) {
            dto.setDueState("overdue");
        } else if (days == 0) {
            dto.setDueState("today");
        } else if (days <= 3) {
            dto.setDueState("soon");
        } else {
            dto.setDueState("normal");
        }
        dto.setStatus(sub.getStatus());
        dto.setSampleCount(sub.getSampleCount());
        return dto;
    }
}
