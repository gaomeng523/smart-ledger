package com.smartledger.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 心愿储蓄 存入 / 取出 请求（V3）。
 */
@Data
public class SavingsAmountRequest {

    @NotNull(message = "心愿 id 不能为空")
    private Long id;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "金额最多 8 位整数、2 位小数")
    private BigDecimal amount;

    /** true=取出，false/空=存入 */
    private Boolean withdraw;
}