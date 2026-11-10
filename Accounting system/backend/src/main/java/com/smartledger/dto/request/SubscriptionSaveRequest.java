package com.smartledger.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增/确认订阅请求（V3）。
 */
@Data
public class SubscriptionSaveRequest {

    @NotBlank(message = "订阅名称不能为空")
    private String title;

    @NotBlank(message = "分类不能为空")
    private String categoryCode;

    @NotBlank(message = "支付渠道不能为空")
    private String payChannelCode;

    @NotNull(message = "参考金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "金额最多 8 位整数、2 位小数")
    private BigDecimal avgAmount;

    /** 30 / 90 / 365，默认 30 */
    private Integer cycleDays;

    /** 下次扣费日 yyyy-MM-dd，默认按周期从今天推算 */
    private String nextDate;
}
