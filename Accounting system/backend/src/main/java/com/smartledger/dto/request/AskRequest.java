package com.smartledger.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 智能账单问答请求。
 */
@Data
public class AskRequest {

    /** 用户问题，如「这个月吃饭花了多少」 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 100, message = "问题不能超过 100 字")
    private String question;
}
