package com.smartledger.ai;

import com.smartledger.ai.dto.AiRawResponse;
import com.smartledger.common.util.DateUtils;
import com.smartledger.dto.response.DictItemDTO;
import com.smartledger.dto.response.ParseResultDTO;
import com.smartledger.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 解析结果校验与归一化：字典命中、日期补全、数值范围、备注截断。
 */
@Component
@RequiredArgsConstructor
public class ParseResultValidator {

    private static final int MAX_REMARK_LEN = 100;
    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.6");
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_CHANNEL = "payChannelCode";
    private static final String FIELD_CATEGORY = "categoryCode";

    private final DictService dictService;

    public ParseResultDTO normalize(AiRawResponse raw) {
        ParseResultDTO dto = new ParseResultDTO();
        if (raw == null) {
            dto.setAmount(BigDecimal.ZERO);
            dto.setBillType(0);
            dto.setRecordDate(DateUtils.format(LocalDate.now()));
            dto.setConfidence(BigDecimal.ZERO);
            dto.getMissingFields().add(FIELD_AMOUNT);
            dto.getMissingFields().add(FIELD_CHANNEL);
            dto.getMissingFields().add(FIELD_CATEGORY);
            return dto;
        }

        // 收支类型：非法按支出处理
        int billType = Integer.valueOf(1).equals(raw.getBillType()) ? 1 : 0;
        dto.setBillType(billType);

        // 金额：非数字或 <= 0 归 0 并标记缺失
        BigDecimal amount = raw.getAmount() == null
                ? BigDecimal.ZERO
                : raw.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = BigDecimal.ZERO;
            dto.getMissingFields().add(FIELD_AMOUNT);
        }
        dto.setAmount(amount);

        // 渠道：未命中映射为「其他」
        DictItemDTO channel = dictService.matchChannel(raw.getPayChannel());
        if (channel == null) {
            channel = dictService.getChannel("OTHER");
            dto.getMissingFields().add(FIELD_CHANNEL);
        }
        if (channel != null) {
            dto.setPayChannelCode(channel.getCode());
            dto.setPayChannelName(channel.getName());
        }

        // 分类：未命中按收支类型映射为「其他支出 / 其他收入」
        DictItemDTO category = dictService.matchCategory(raw.getCategory(), billType);
        if (category == null) {
            category = dictService.getCategory(billType == 1 ? "INCOME_OTHER" : "EXPENSE_OTHER");
            dto.getMissingFields().add(FIELD_CATEGORY);
        }
        if (category != null) {
            dto.setCategoryCode(category.getCode());
            dto.setCategoryName(category.getName());
        }

        // 日期：非法或晚于今天+1 天回退为今天
        LocalDate parsedDate = DateUtils.parseDate(raw.getRecordDate());
        if (parsedDate == null || parsedDate.isAfter(LocalDate.now().plusDays(1))) {
            parsedDate = LocalDate.now();
        }
        dto.setRecordDate(DateUtils.format(parsedDate));

        // 备注截断
        dto.setRemark(truncate(raw.getRemark(), MAX_REMARK_LEN));

        // 置信度
        BigDecimal confidence = raw.getConfidence() == null ? DEFAULT_CONFIDENCE : raw.getConfidence();
        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            confidence = BigDecimal.ZERO;
        } else if (confidence.compareTo(BigDecimal.ONE) > 0) {
            confidence = BigDecimal.ONE;
        }
        dto.setConfidence(confidence);

        dto.setNeedConfirm(true);
        return dto;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}