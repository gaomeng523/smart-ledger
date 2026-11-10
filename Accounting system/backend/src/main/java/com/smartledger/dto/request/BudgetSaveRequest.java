package com.smartledger.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 设置预算请求（V2）。
 */
@Data
public class BudgetSaveRequest {

    /** yyyy-MM */
    @NotBlank(message = "预算月份不能为空")
    private String budgetMonth;

    /** 空 = 总预算 */
    private String categoryCode;

    @NotNull(message = "预算金额不能为空")
    @DecimalMin(value = "0.01", message = "预算金额必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "预算金额最多 8 位整数、2 位小数")
    private BigDecimal amount;

    /** 预警比例，默认 0.80，取值 0.5~1.0 */
    private BigDecimal warnRatio;
}