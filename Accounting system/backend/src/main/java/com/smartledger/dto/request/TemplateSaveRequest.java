package com.smartledger.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增模板请求（V2）。
 */
@Data
public class TemplateSaveRequest {

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 12, message = "模板名称不能超过 12 个字符")
    private String title;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "金额最多 8 位整数、2 位小数")
    private BigDecimal amount;

    @NotNull(message = "收支类型不能为空")
    private Integer billType;

    @NotBlank(message = "支付渠道不能为空")
    private String payChannelCode;

    @NotBlank(message = "分类不能为空")
    private String categoryCode;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}