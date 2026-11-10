package com.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartledger.common.constant.CommonConstants;
import com.smartledger.common.enums.BillSourceEnum;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.dto.request.TemplateSaveRequest;
import com.smartledger.dto.request.TemplateUpdateRequest;
import com.smartledger.dto.response.DictItemDTO;
import com.smartledger.dto.response.TemplateDTO;
import com.smartledger.dto.response.TemplateUseResultDTO;
import com.smartledger.entity.Bill;
import com.smartledger.entity.BillTemplate;
import com.smartledger.mapper.BillMapper;
import com.smartledger.mapper.BillTemplateMapper;
import com.smartledger.service.DictService;
import com.smartledger.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 快捷模板应用服务实现。
 */
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final BillTemplateMapper templateMapper;
    private final BillMapper billMapper;
    private final DictService dictService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateDTO create(String openid, TemplateSaveRequest request) {
        int billType = validateDict(request.getBillType(), request.getPayChannelCode(), request.getCategoryCode());

        BillTemplate template = new BillTemplate();
        template.setOpenid(openid);
        template.setTitle(request.getTitle().trim());
        template.setAmount(request.getAmount());
        template.setBillType(billType);
        template.setPayChannelCode(request.getPayChannelCode());
        template.setCategoryCode(request.getCategoryCode());
        template.setRemark(request.getRemark());
        template.setUseCount(0);
        template.setSortNo(0);
        template.setStatus(1);
        templateMapper.insert(template);
        return toDTO(template);
    }

    @Override
    public List<TemplateDTO> list(String openid) {
        List<BillTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<BillTemplate>()
                .eq(BillTemplate::getOpenid, openid)
                .eq(BillTemplate::getStatus, 1)
                .orderByDesc(BillTemplate::getUseCount)
                .orderByAsc(BillTemplate::getSortNo)
                .orderByDesc(BillTemplate::getId));
        return templates.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String openid, TemplateUpdateRequest request) {
        BillTemplate template = requireOwned(openid, request.getTemplateId());
        int billType = validateDict(request.getBillType(), request.getPayChannelCode(), request.getCategoryCode());

        template.setTitle(request.getTitle().trim());
        template.setAmount(request.getAmount());
        template.setBillType(billType);
        template.setPayChannelCode(request.getPayChannelCode());
        template.setCategoryCode(request.getCategoryCode());
        template.setRemark(request.getRemark());
        templateMapper.updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String openid, Long templateId) {
        requireOwned(openid, templateId);
        templateMapper.deleteById(templateId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateUseResultDTO use(String openid, Long templateId) {
        BillTemplate template = requireOwned(openid, templateId);

        Bill bill = new Bill();
        bill.setOpenid(openid);
        bill.setBookId(CommonConstants.DEFAULT_BOOK_ID);
        bill.setAmount(template.getAmount());
        bill.setBillType(template.getBillType());
        bill.setPayChannelCode(template.getPayChannelCode());
        bill.setCategoryCode(template.getCategoryCode());
        bill.setRemark(template.getRemark());
        bill.setRecordText(template.getTitle());
        bill.setRecordDate(LocalDate.now());
        bill.setSource(BillSourceEnum.TEMPLATE.getCode());
        bill.setRequestId("tpl-" + UUID.randomUUID());
        bill.setDeleted(0);
        billMapper.insert(bill);

        template.setUseCount(template.getUseCount() == null ? 1 : template.getUseCount() + 1);
        templateMapper.updateById(template);
        return new TemplateUseResultDTO(bill.getId());
    }

    private int validateDict(Integer billType, String payChannelCode, String categoryCode) {
        if (dictService.getChannel(payChannelCode) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付渠道不存在: " + payChannelCode);
        }
        int type = billType != null && billType == 1 ? 1 : 0;
        DictItemDTO category = dictService.getCategory(categoryCode);
        if (category == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类不存在: " + categoryCode);
        }
        if (!Objects.equals(category.getBillType(), type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分类与收支类型不匹配");
        }
        return type;
    }

    private BillTemplate requireOwned(String openid, Long templateId) {
        BillTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        if (!openid.equals(template.getOpenid())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该模板");
        }
        return template;
    }

    private TemplateDTO toDTO(BillTemplate template) {
        TemplateDTO dto = new TemplateDTO();
        dto.setTemplateId(template.getId());
        dto.setTitle(template.getTitle());
        dto.setAmount(template.getAmount());
        dto.setBillType(template.getBillType());
        dto.setPayChannelCode(template.getPayChannelCode());
        dto.setPayChannelName(dictService.channelName(template.getPayChannelCode()));
        dto.setCategoryCode(template.getCategoryCode());
        dto.setCategoryName(dictService.categoryName(template.getCategoryCode()));
        dto.setRemark(template.getRemark());
        dto.setUseCount(template.getUseCount());
        dto.setSortNo(template.getSortNo());
        return dto;
    }
}