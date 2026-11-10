package com.smartledger.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增别名请求（V2）。
 */
@Data
public class AliasSaveRequest {

    @NotBlank(message = "关键词不能为空")
    @Size(max = 20, message = "关键词不能超过 20 个字符")
    private String keyword;

    /** 1 分类 2 渠道 */
    @NotNull(message = "映射目标类型不能为空")
    private Integer targetType;

    @NotBlank(message = "映射目标不能为空")
    private String targetCode;
}