package com.smartledger.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自然语言解析请求。
 */
@Data
public class BillParseRequest {

    @NotBlank(message = "输入文本不能为空")
    @Size(max = 200, message = "输入文本长度不能超过 200 字符")
    private String text;
}