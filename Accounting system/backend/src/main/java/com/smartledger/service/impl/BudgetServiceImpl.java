package com.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.util.DateUtils;
import com.smartledger.dto.request.BudgetSaveRequest;
import com.smartledger.dto.response.BudgetDTO;
import com.smartledger.entity.Budget;
import com.smartledger.mapper.BillMapper;
import com.smartledger.mapper.BudgetMapper;
import com.smartledger.service.BudgetService;
import com.smartledger.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预算应用服务实现。已用金额按「record_date 落在本月、bill_type=0」聚合。
 */
@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {

    private static final BigDecimal DEFAULT_WARN_RATIO = new BigDecimal("0.80");
    private static final BigDecimal MIN_WARN_RATIO = new BigDecimal("0.50");
    private static final BigDecimal MAX_WARN_RATIO = new BigDecimal("1.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String TOTAL_CATEGORY = "";

    private final BudgetMapper budgetMapper;
    private final BillMapper billMapper;
    private final DictService dictService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetDTO save(String openid, BudgetSaveRequest request) {
        String month = normalizeMonth(request.getBudgetMonth());
        String categoryCode = request.getCategoryCode() == null ? TOTAL_CATEGORY : request.getCategoryCode().trim();
        if (StringUtils.hasText(categoryCode) && dictService.getCategory(categoryCode) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类不存在: " + categoryCode);
        }
        BigDecimal warnRatio = normalizeWarnRatio(request.getWarnRatio());

        Budget budget = budgetMapper.selectOne(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getOpenid, openid)
                .eq(Budget::getBudgetMonth, month)
                .eq(Budget::getCategoryCode, categoryCode));
        if (budget == null) {
            budget = new Budget();
            budget.setOpenid(openid);
            budget.setBudgetMonth(month);
            budget.setCategoryCode(categoryCode);
            budget.setStatus(1);
        }
        budget.setAmount(request.getAmount());
        budget.setWarnRatio(warnRatio);
        if (budget.getId() == null) {
            budgetMapper.insert(budget);
        } else {
            budgetMapper.updateById(budget);
        }
        return get(openid, month);
    }

    @Override
    public BudgetDTO get(String openid, String month) {
        String targetMonth = normalizeMonth(month);
        YearMonth yearMonth = DateUtils.parseMonth(targetMonth);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = start.plusMonths(1);

        List<Budget> budgets = budgetMapper.selectList(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getOpenid, openid)
                .eq(Budget::getBudgetMonth, targetMonth)
                .eq(Budget::getStatus, 1));

        BigDecimal totalBudget = BigDecimal.ZERO;
        for (Budget budget : budgets) {
            if (TOTAL_CATEGORY.equals(budget.getCategoryCode())) {
                totalBudget = budget.getAmount();
            }
        }

        // 已用：按分类聚合的支出
        Map<String, BigDecimal> usedByCategory = new HashMap<>();
        BigDecimal totalUsed = BigDecimal.ZERO;
        for (Map<String, Object> row : billMapper.selectCategoryStat(openid, 0, start, end)) {
            String code = String.valueOf(row.get("categoryCode"));
            BigDecimal amount = toDecimal(row.get("amount"));
            usedByCategory.put(code, amount);
            totalUsed = totalUsed.add(amount);
        }

        BudgetDTO dto = new BudgetDTO();
        dto.setMonth(targetMonth);
        dto.setTotalBudget(totalBudget);
        dto.setTotalUsed(totalUsed);
        dto.setTotalRemain(totalBudget.subtract(totalUsed));
        dto.setUsedRatio(ratio(totalUsed, totalBudget));

        for (Budget budget : budgets) {
            String code = budget.getCategoryCode();
            if (TOTAL_CATEGORY.equals(code)) {
                continue;
            }
            BigDecimal used = usedByCategory.getOrDefault(code, BigDecimal.ZERO);
            BudgetDTO.Item item = new BudgetDTO.Item();
            item.setBudgetId(budget.getId());
            item.setCategoryCode(code);
            item.setCategoryName(dictService.categoryName(code));
            item.setAmount(budget.getAmount());
            item.setUsed(used);
            item.setRemain(budget.getAmount().subtract(used));
            item.setUsedRatio(ratio(used, budget.getAmount()));
            item.setWarnRatio(budget.getWarnRatio());
            dto.getItems().add(item);
        }
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String openid, Long budgetId) {
        Budget budget = budgetMapper.selectById(budgetId);
        if (budget == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "预算不存在");
        }
        if (!openid.equals(budget.getOpenid())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该预算");
        }
        budgetMapper.deleteById(budgetId);
    }

    private String normalizeMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return DateUtils.formatMonth(YearMonth.now());
        }
        String trimmed = month.trim();
        try {
            YearMonth.parse(trimmed);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
        return trimmed;
    }

    private BigDecimal normalizeWarnRatio(BigDecimal warnRatio) {
        if (warnRatio == null) {
            return DEFAULT_WARN_RATIO;
        }
        if (warnRatio.compareTo(MIN_WARN_RATIO) < 0) {
            return MIN_WARN_RATIO;
        }
        if (warnRatio.compareTo(MAX_WARN_RATIO) > 0) {
            return MAX_WARN_RATIO;
        }
        return warnRatio;
    }

    private BigDecimal ratio(BigDecimal used, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return used.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP)
                .divide(HUNDRED, 4, RoundingMode.HALF_UP);
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
}