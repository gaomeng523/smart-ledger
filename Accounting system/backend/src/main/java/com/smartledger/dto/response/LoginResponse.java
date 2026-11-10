package com.smartledger.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 登录响应。
 */
@Data
public class LoginResponse {

    private String token;

    @JsonProperty("isNewUser")
    private Boolean isNewUser;

    /** token 有效期（秒） */
    private long expiresIn;
}