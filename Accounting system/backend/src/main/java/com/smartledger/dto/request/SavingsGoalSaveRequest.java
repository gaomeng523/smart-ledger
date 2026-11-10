package com.smartledger.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建 / 编辑心愿储蓄请求（V3）。
 */
@Data
public class SavingsGoalSaveRequest {

    /** 编辑时必填 */
    private Long id;

    @NotBlank(message = "心愿名称不能为空")
    @Size(max = 20, message = "心愿名称最多 20 个字")
    private String title;

    @Size(max = 8, message = "图标过长")
    private String icon;

    @NotNull(message = "目标金额不能为空")
    @DecimalMin(value = "0.01", message = "目标金额必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "目标金额最多 8 位整数、2 位小数")
    private BigDecimal targetAmount;

    /** 计划达成日 yyyy-MM-dd，可空 */
    private String dueDate;

    private String categoryCode;
}