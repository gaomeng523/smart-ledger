package com.smartledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图片识别（多模态）配置。前缀：smartledger.vision
 *
 * <p>走 OpenAI 兼容协议，默认对接阿里云百炼的通义千问视觉模型。</p>
 */
@Data
@ConfigurationProperties(prefix = "smartledger.vision")
public class VisionProperties {

    /** 总开关（个人主体版本默认关闭，避免深度合成类能力上架） */
    private boolean enabled = false;

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String apiKey;

    /** 视觉模型名，禁止硬编码 */
    private String model = "qwen3-vl-plus";

    /** 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** 读取超时（毫秒）：图片识别比纯文本慢，给足时间 */
    private int readTimeout = 30000;

    /** 超时/5xx 重试次数 */
    private int maxRetries = 1;

    private double temperature = 0.1;

    private int maxTokens = 512;
}
