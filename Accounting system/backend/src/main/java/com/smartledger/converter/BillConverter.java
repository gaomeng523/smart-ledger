package com.smartledger.converter;

import com.smartledger.common.util.DateUtils;
import com.smartledger.common.util.TagUtils;
import com.smartledger.dto.response.BillDTO;
import com.smartledger.entity.Bill;
import com.smartledger.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 账单实体与 DTO 转换。
 */
@Component
@RequiredArgsConstructor
public class BillConverter {

    private final DictService dictService;

    /**
     * @param withDetail 是否携带原始输入文本（仅详情接口需要）
     */
    public BillDTO toDTO(Bill bill, boolean withDetail) {
        if (bill == null) {
            return null;
        }
        Map<String, String> channelNames = dictService.channelNameMap();
        Map<String, String> categoryNames = dictService.categoryNameMap();

        BillDTO dto = new BillDTO();
        dto.setBillId(bill.getId());
        dto.setAmount(bill.getAmount());
        dto.setBillType(bill.getBillType());
        dto.setPayChannelCode(bill.getPayChannelCode());
        dto.setPayChannelName(channelNames.getOrDefault(bill.getPayChannelCode(), bill.getPayChannelCode()));
        dto.setCategoryCode(bill.getCategoryCode());
        dto.setCategoryName(categoryNames.getOrDefault(bill.getCategoryCode(), bill.getCategoryCode()));
        dto.setRecordDate(DateUtils.format(bill.getRecordDate()));
        dto.setRemark(bill.getRemark());
        dto.setSource(bill.getSource());
        dto.setConfidence(bill.getConfidence());
        dto.setTags(TagUtils.split(bill.getTags()));
        dto.setImageUrl(bill.getImageUrl());
        dto.setCreateTime(bill.getCreateTime());
        dto.setUpdateTime(bill.getUpdateTime());
        if (withDetail) {
            dto.setRecordText(bill.getRecordText());
        }
        return dto;
    }
}