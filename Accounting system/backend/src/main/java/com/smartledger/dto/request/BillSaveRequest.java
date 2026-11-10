package com.smartledger.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 保存账单请求。
 */
@Data
public class BillSaveRequest {

    @NotBlank(message = "requestId 不能为空")
    @Size(max = 64, message = "requestId 长度不能超过 64")
    private String requestId;

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

    @NotBlank(message = "记账日期不能为空")
    private String recordDate;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    @Size(max = 500, message = "原始文本长度不能超过 500")
    private String recordText;

    /** 1 文本 2 语音 3 模板 4 导入 5 OCR 6 手动 */
    private Integer source;

    private BigDecimal confidence;

    /** 标签（最多 5 个，单个 ≤ 12 字），V2 */
    private java.util.List<String> tags;

    /** 账单图片 URL（拍照记账），V3 阶段二 */
    @Size(max = 255, message = "图片地址长度不能超过 255")
    private String imageUrl;

    /** 命中疑似重复时，前端二次确认后置为 true 再提交 */
    private Boolean force;
}