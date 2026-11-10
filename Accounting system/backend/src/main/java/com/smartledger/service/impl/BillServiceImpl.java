package com.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartledger.common.constant.CommonConstants;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.result.PageResult;
import com.smartledger.common.util.DateUtils;
import com.smartledger.common.util.TagUtils;
import com.smartledger.converter.BillConverter;
import com.smartledger.dto.request.BillListQuery;
import com.smartledger.dto.request.BillSaveRequest;
import com.smartledger.dto.request.BillUpdateRequest;
import com.smartledger.dto.response.BillDTO;
import com.smartledger.dto.response.DictItemDTO;
import com.smartledger.dto.response.DuplicateResultDTO;
import com.smartledger.dto.response.InsightDTO;
import com.smartledger.dto.response.SaveResultDTO;
import com.smartledger.entity.Bill;
import com.smartledger.mapper.BillMapper;
import com.smartledger.service.BillService;
import com.smartledger.service.DictService;
import com.smartledger.service.InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 账单应用服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillService {

    /** 疑似重复识别窗口：记账日期相差 ≤ 3 天（见《04》§4.6） */
    private static final int DUPLICATE_WINDOW_DAYS = 3;
    private static final int DUPLICATE_CANDIDATE_LIMIT = 5;
    private static final int TAG_SUGGEST_LIMIT = 20;

    private final BillMapper billMapper;
    private final DictService dictService;
    private final BillConverter billConverter;
    private final InsightService insightService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaveResultDTO save(String openid, BillSaveRequest request) {
        // 1. 幂等：requestId 命中即返回既有账单
        if (StringUtils.hasText(request.getRequestId())) {
            Bill existed = billMapper.selectOne(new LambdaQueryWrapper<Bill>()
                    .eq(Bill::getRequestId, request.getRequestId()));
            if (existed != null) {
                return new SaveResultDTO(existed.getId(), true, List.of());
            }
        }

        // 2. 字典校验
        DictItemDTO channel = dictService.getChannel(request.getPayChannelCode());
        if (channel == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付渠道不存在: " + request.getPayChannelCode());
        }
        int billType = request.getBillType() != null && request.getBillType() == 1 ? 1 : 0;
        DictItemDTO category = dictService.getCategory(request.getCategoryCode());
        if (category == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类不存在: " + request.getCategoryCode());
        }
        if (!Objects.equals(category.getBillType(), billType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类与收支类型不匹配");
        }

        // 3. 日期与标签
        LocalDate recordDate = DateUtils.parseDate(request.getRecordDate());
        if (recordDate == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "记账日期格式应为 yyyy-MM-dd");
        }
        String tags = TagUtils.normalizeToStorage(request.getTags());

        // 4. 疑似重复校验（前端二次确认后 force=true 跳过）
        if (!Boolean.TRUE.equals(request.getForce())) {
            List<Bill> candidates = findDuplicates(openid, request, recordDate);
            if (!candidates.isEmpty()) {
                throw new BizException(ErrorCode.DUPLICATE_BILL,
                        "存在疑似重复的账单，确认是否继续保存", buildDuplicateResult(candidates));
            }
        }

        // 5. 落库
        Bill bill = new Bill();
        bill.setOpenid(openid);
        bill.setBookId(CommonConstants.DEFAULT_BOOK_ID);
        bill.setAmount(request.getAmount());
        bill.setBillType(billType);
        bill.setPayChannelCode(request.getPayChannelCode());
        bill.setCategoryCode(request.getCategoryCode());
        bill.setRecordText(request.getRecordText());
        bill.setRemark(request.getRemark());
        bill.setRecordDate(recordDate);
        bill.setSource(request.getSource() == null ? 1 : request.getSource());
        bill.setConfidence(request.getConfidence());
        bill.setTags(tags);
        bill.setImageUrl(request.getImageUrl());
        bill.setRequestId(request.getRequestId());
        bill.setDeleted(0);
        billMapper.insert(bill);

        // 6. 后置洞察：订阅续费匹配、离群/周环比/预算提示。任何异常都不影响记账主流程
        List<InsightDTO> insights = List.of();
        try {
            insights = insightService.afterSave(openid, bill.getId());
        } catch (Exception e) {
            log.warn("记账后洞察计算失败 billId={}: {}", bill.getId(), e.getMessage());
        }
        return new SaveResultDTO(bill.getId(), false, insights);
    }

    @Override
    public PageResult<BillDTO> page(String openid, BillListQuery query) {
        long pageNo = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        long size = query.getSize() == null || query.getSize() < 1 ? 20 : Math.min(query.getSize(), 100);

        if (query.getAmountMin() != null && query.getAmountMax() != null
                && query.getAmountMin().compareTo(query.getAmountMax()) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "金额下限不能大于上限");
        }
        LocalDate dateStart = DateUtils.parseDate(query.getDateStart());
        LocalDate dateEnd = DateUtils.parseDate(query.getDateEnd());
        if (StringUtils.hasText(query.getDateStart()) && dateStart == null
                || StringUtils.hasText(query.getDateEnd()) && dateEnd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期区间格式应为 yyyy-MM-dd");
        }

        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getOpenid, openid);

        // 日期区间优先级高于 month（见《04》§4.6）
        if (dateStart != null || dateEnd != null) {
            if (dateStart != null) {
                wrapper.ge(Bill::getRecordDate, dateStart);
            }
            if (dateEnd != null) {
                wrapper.le(Bill::getRecordDate, dateEnd);
            }
        } else if (StringUtils.hasText(query.getMonth())) {
            LocalDate start = DateUtils.parseMonth(query.getMonth()).atDay(1);
            wrapper.ge(Bill::getRecordDate, start).lt(Bill::getRecordDate, start.plusMonths(1));
        }
        if (query.getBillType() != null) {
            wrapper.eq(Bill::getBillType, query.getBillType());
        }
        if (StringUtils.hasText(query.getCategoryCode())) {
            wrapper.eq(Bill::getCategoryCode, query.getCategoryCode());
        }
        if (StringUtils.hasText(query.getPayChannelCode())) {
            wrapper.eq(Bill::getPayChannelCode, query.getPayChannelCode());
        }
        if (query.getSource() != null) {
            wrapper.eq(Bill::getSource, query.getSource());
        }
        if (query.getAmountMin() != null) {
            wrapper.ge(Bill::getAmount, query.getAmountMin());
        }
        if (query.getAmountMax() != null) {
            wrapper.le(Bill::getAmount, query.getAmountMax());
        }
        if (StringUtils.hasText(query.getTag())) {
            for (String tag : TagUtils.split(query.getTag())) {
                wrapper.like(Bill::getTags, tag);
            }
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Bill::getRemark, keyword).or().like(Bill::getRecordText, keyword));
        }
        wrapper.orderByDesc(Bill::getRecordDate).orderByDesc(Bill::getId);

        Page<Bill> page = billMapper.selectPage(new Page<>(pageNo, size), wrapper);
        List<BillDTO> records = page.getRecords().stream()
                .map(bill -> billConverter.toDTO(bill, false))
                .toList();
        return PageResult.of(page, records);
    }

    @Override
    public BillDTO detail(String openid, Long billId) {
        Bill bill = requireOwnedBill(openid, billId);
        return billConverter.toDTO(bill, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String openid, BillUpdateRequest request) {
        Bill bill = requireOwnedBill(openid, request.getBillId());

        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "金额必须大于 0");
            }
            bill.setAmount(request.getAmount());
        }
        if (request.getBillType() != null) {
            bill.setBillType(request.getBillType() == 1 ? 1 : 0);
        }
        if (StringUtils.hasText(request.getPayChannelCode())) {
            if (dictService.getChannel(request.getPayChannelCode()) == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "支付渠道不存在");
            }
            bill.setPayChannelCode(request.getPayChannelCode());
        }
        if (StringUtils.hasText(request.getCategoryCode())) {
            DictItemDTO category = dictService.getCategory(request.getCategoryCode());
            if (category == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "分类不存在");
            }
            if (!Objects.equals(category.getBillType(), bill.getBillType())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "分类与收支类型不匹配");
            }
            bill.setCategoryCode(request.getCategoryCode());
        }
        if (StringUtils.hasText(request.getRecordDate())) {
            LocalDate recordDate = DateUtils.parseDate(request.getRecordDate());
            if (recordDate == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "记账日期格式应为 yyyy-MM-dd");
            }
            bill.setRecordDate(recordDate);
        }
        if (request.getRemark() != null) {
            bill.setRemark(request.getRemark());
        }
        if (request.getTags() != null) {
            bill.setTags(TagUtils.normalizeToStorage(request.getTags()));
        }
        if (request.getImageUrl() != null) {
            bill.setImageUrl(request.getImageUrl().isBlank() ? null : request.getImageUrl().trim());
        }
        billMapper.updateById(bill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String openid, Long billId) {
        requireOwnedBill(openid, billId);
        billMapper.deleteById(billId);
    }

    @Override
    public List<String> tagSuggest(String openid) {
        Map<String, Integer> frequency = new HashMap<>();
        for (String stored : billMapper.selectTagStrings(openid)) {
            for (String tag : TagUtils.split(stored)) {
                frequency.merge(tag, 1, Integer::sum);
            }
        }
        return frequency.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(TAG_SUGGEST_LIMIT)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Bill requireOwnedBill(String openid, Long billId) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "账单不存在");
        }
        if (!openid.equals(bill.getOpenid())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该账单");
        }
        return bill;
    }

    /**
     * 疑似重复：同一用户下、记账日期相差 ≤ 3 天、金额相同、渠道与分类均相同的未删除账单。
     */
    private List<Bill> findDuplicates(String openid, BillSaveRequest request, LocalDate recordDate) {
        return billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getOpenid, openid)
                .eq(Bill::getAmount, request.getAmount())
                .eq(Bill::getPayChannelCode, request.getPayChannelCode())
                .eq(Bill::getCategoryCode, request.getCategoryCode())
                .ge(Bill::getRecordDate, recordDate.minusDays(DUPLICATE_WINDOW_DAYS))
                .le(Bill::getRecordDate, recordDate.plusDays(DUPLICATE_WINDOW_DAYS))
                .orderByDesc(Bill::getRecordDate)
                .orderByDesc(Bill::getId)
                .last("LIMIT " + DUPLICATE_CANDIDATE_LIMIT));
    }

    private DuplicateResultDTO buildDuplicateResult(List<Bill> candidates) {
        DuplicateResultDTO result = new DuplicateResultDTO();
        List<DuplicateResultDTO.Candidate> items = new ArrayList<>();
        for (Bill bill : candidates) {
            items.add(new DuplicateResultDTO.Candidate(
                    bill.getId(),
                    DateUtils.format(bill.getRecordDate()),
                    bill.getAmount(),
                    dictService.categoryName(bill.getCategoryCode())));
        }
        result.setCandidates(items);
        return result;
    }
}