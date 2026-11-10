package com.smartledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信小程序配置。前缀：smartledger.wx
 */
@Data
@ConfigurationProperties(prefix = "smartledger.wx")
public class WxProperties {

    private String appId;

    private String appSecret;

    /** 本地开发模拟登录开关（生产必须为 false） */
    private boolean mockEnabled = false;
}