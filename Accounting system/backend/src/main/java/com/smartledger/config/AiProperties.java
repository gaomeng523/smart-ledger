package com.smartledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 解析配置。前缀：smartledger.ai
 */
@Data
@ConfigurationProperties(prefix = "smartledger.ai")
public class AiProperties {

    /** 总开关 */
    private boolean enabled = true;

    private String baseUrl = "https://api.deepseek.com";

    private String apiKey;

    /** 模型名，禁止硬编码 */
    private String model = "deepseek-flash";

    /** 连接超时（毫秒） */
    private int connectTimeout = 3000;

    /** 读取超时（毫秒） */
    private int readTimeout = 12000;

    /** 超时/5xx 重试次数 */
    private int maxRetries = 1;

    private double temperature = 0.2;

    /** 解析结果缓存秒数（防连点） */
    private int cacheSeconds = 60;

    /** 单次输入最大字符数 */
    private int maxInputLength = 200;

    /** 单用户每分钟最大解析次数 */
    private int rateLimitPerMinute = 20;
}