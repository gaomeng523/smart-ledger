package com.smartledger.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改订阅请求（V3）。
 */
@Data
public class SubscriptionUpdateRequest {

    @NotNull(message = "id 不能为空")
    private Long id;

    @NotBlank(message = "订阅名称不能为空")
    private String title;

    @NotNull(message = "参考金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "金额最多 8 位整数、2 位小数")
    private BigDecimal avgAmount;

    @NotNull(message = "周期不能为空")
    private Integer cycleDays;

    @NotBlank(message = "下次扣费日不能为空")
    private String nextDate;

    /** 1 正常 0 暂停 */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
