package com.smartledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置。前缀：smartledger.jwt
 */
@Data
@ConfigurationProperties(prefix = "smartledger.jwt")
public class JwtProperties {

    /** 签名密钥（HS256 要求至少 32 字节） */
    private String secret;

    /** 有效期（天） */
    private int expireDays = 7;
}