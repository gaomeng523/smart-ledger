package com.smartledger.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改账单请求（仅传入需要修改的字段）。
 */
@Data
public class BillUpdateRequest {

    @NotNull(message = "billId 不能为空")
    private Long billId;

    private BigDecimal amount;

    private Integer billType;

    private String payChannelCode;

    private String categoryCode;

    private String recordDate;

    private String remark;

    /** 标签（最多 5 个，单个 ≤ 12 字），V2；传空数组表示清空标签 */
    private java.util.List<String> tags;

    /** 账单图片 URL（拍照记账），V3 阶段二；传空字符串表示清空图片 */
    private String imageUrl;
}