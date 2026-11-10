package com.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.util.DateUtils;
import com.smartledger.dto.request.SavingsAmountRequest;
import com.smartledger.dto.request.SavingsGoalSaveRequest;
import com.smartledger.dto.response.SavingsGoalDTO;
import com.smartledger.entity.SavingsGoal;
import com.smartledger.mapper.SavingsGoalMapper;
import com.smartledger.service.DictService;
import com.smartledger.service.SavingsService;
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
import java.util.List;

/**
 * 心愿储蓄服务实现（V3）。
 *
 * <p>纯本地计算：进度、剩余、天数均由 SQL/Java 聚合，不依赖 AI。</p>
 */
@Service
@RequiredArgsConstructor
public class SavingsServiceImpl implements SavingsService {

    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_GIVE_UP = 2;

    private final SavingsGoalMapper savingsGoalMapper;
    private final DictService dictService;

    /* ============================================================
     * 列表
     * ============================================================ */

    @Override
    public List<SavingsGoalDTO> list(String openid) {
        List<SavingsGoal> list = savingsGoalMapper.selectList(new LambdaQueryWrapper<SavingsGoal>()
                .eq(SavingsGoal::getOpenid, openid));
        List<SavingsGoalDTO> result = new ArrayList<>();
        for (SavingsGoal goal : list) {
            result.add(toDTO(goal));
        }
        // 进行中优先，其次按创建时间倒序
        result.sort(Comparator
                .comparingInt((SavingsGoalDTO d) -> d.getStatus() == null ? 3 : d.getStatus())
                .thenComparing(SavingsGoalDTO::getId, Comparator.reverseOrder()));
        return result;
    }

    /* ============================================================
     * 新建 / 编辑
     * ============================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SavingsGoalDTO create(String openid, SavingsGoalSaveRequest request) {
        validateRefs(request.getCategoryCode());
        SavingsGoal goal = new SavingsGoal();
        goal.setOpenid(openid);
        goal.setTitle(request.getTitle().trim());
        goal.setIcon(normalizeIcon(request.getIcon()));
        goal.setTargetAmount(request.getTargetAmount());
        goal.setSavedAmount(BigDecimal.ZERO);
        goal.setDueDate(parseDueDate(request.getDueDate()));
        goal.setCategoryCode(StringUtils.hasText(request.getCategoryCode())
                ? request.getCategoryCode() : null);
        goal.setStatus(STATUS_ACTIVE);
        goal.setDeleted(0);
        savingsGoalMapper.insert(goal);
        return toDTO(goal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SavingsGoalDTO update(String openid, SavingsGoalSaveRequest request) {
        SavingsGoal goal = requireOwnedGoal(openid, request.getId());
        validateRefs(request.getCategoryCode());
        goal.setTitle(request.getTitle().trim());
        goal.setIcon(normalizeIcon(request.getIcon()));
        goal.setTargetAmount(request.getTargetAmount());
        goal.setDueDate(parseDueDate(request.getDueDate()));
        goal.setCategoryCode(StringUtils.hasText(request.getCategoryCode())
                ? request.getCategoryCode() : null);
        // 目标金额调小后可能已达标，重算状态（不覆盖"已放弃"）
        if (goal.getStatus() == null || goal.getStatus() != STATUS_GIVE_UP) {
            goal.setStatus(goal.getSavedAmount().compareTo(goal.getTargetAmount()) >= 0
                    ? STATUS_DONE : STATUS_ACTIVE);
        }
        savingsGoalMapper.updateById(goal);
        return toDTO(goal);
    }

    /* ============================================================
     * 存入 / 取出
     * ============================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SavingsGoalDTO changeAmount(String openid, SavingsAmountRequest request) {
        SavingsGoal goal = requireOwnedGoal(openid, request.getId());
        if (goal.getStatus() != null && goal.getStatus() == STATUS_GIVE_UP) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已放弃的心愿无法操作");
        }
        boolean withdraw = Boolean.TRUE.equals(request.getWithdraw());
        BigDecimal saved = goal.getSavedAmount() == null ? BigDecimal.ZERO : goal.getSavedAmount();
        BigDecimal after = withdraw ? saved.subtract(request.getAmount()) : saved.add(request.getAmount());
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "取出的金额超过已存金额");
        }
        goal.setSavedAmount(after);
        goal.setStatus(after.compareTo(goal.getTargetAmount()) >= 0 ? STATUS_DONE : STATUS_ACTIVE);
        savingsGoalMapper.updateById(goal);
        return toDTO(goal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String openid, Long id) {
        requireOwnedGoal(openid, id);
        savingsGoalMapper.deleteById(id);
    }

    /* ============================================================
     * 辅助
     * ============================================================ */

    private SavingsGoal requireOwnedGoal(String openid, Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "心愿 id 不能为空");
        }
        SavingsGoal goal = savingsGoalMapper.selectById(id);
        if (goal == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "心愿不存在");
        }
        if (!openid.equals(goal.getOpenid())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该心愿");
        }
        return goal;
    }

    private void validateRefs(String categoryCode) {
        if (StringUtils.hasText(categoryCode) && dictService.getCategory(categoryCode) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类不存在: " + categoryCode);
        }
    }

    private LocalDate parseDueDate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        LocalDate date = DateUtils.parseDate(text);
        if (date == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "计划达成日格式应为 yyyy-MM-dd");
        }
        return date;
    }

    private String normalizeIcon(String icon) {
        return StringUtils.hasText(icon) ? icon.trim() : "🎯";
    }

    private SavingsGoalDTO toDTO(SavingsGoal goal) {
        SavingsGoalDTO dto = new SavingsGoalDTO();
        dto.setId(goal.getId());
        dto.setTitle(goal.getTitle());
        dto.setIcon(goal.getIcon());
        dto.setTargetAmount(goal.getTargetAmount());
        BigDecimal saved = goal.getSavedAmount() == null ? BigDecimal.ZERO : goal.getSavedAmount();
        dto.setSavedAmount(saved);
        BigDecimal remain = goal.getTargetAmount().subtract(saved);
        dto.setRemainAmount(remain.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remain);

        int percent = 0;
        if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
            percent = saved.multiply(BigDecimal.valueOf(100))
                    .divide(goal.getTargetAmount(), 0, RoundingMode.DOWN)
                    .intValue();
        }
        dto.setProgressPercent(Math.min(100, Math.max(0, percent)));

        dto.setDueDate(DateUtils.format(goal.getDueDate()));
        dto.setDaysLeft(goal.getDueDate() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), goal.getDueDate()));
        dto.setCategoryCode(goal.getCategoryCode());
        dto.setCategoryName(StringUtils.hasText(goal.getCategoryCode())
                ? dictService.categoryName(goal.getCategoryCode()) : null);
        dto.setStatus(goal.getStatus());
        dto.setStatusText(statusText(goal.getStatus()));
        return dto;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "进行中";
        }
        return switch (status) {
            case STATUS_DONE -> "已达成";
            case STATUS_GIVE_UP -> "已放弃";
            default -> "进行中";
        };
    }
}