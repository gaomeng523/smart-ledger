package com.smartledger.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 第三方 HTTP 客户端配置：DeepSeek、视觉模型与微信开放平台各一个 RestClient 实例。
 */
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final AiProperties aiProperties;
    private final VisionProperties visionProperties;

    /**
     * DeepSeek 客户端：连接 3s / 读取 12s（可在配置中调整）。
     */
    @Bean("aiRestClient")
    public RestClient aiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiProperties.getConnectTimeout());
        factory.setReadTimeout(aiProperties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 视觉模型客户端：连接 5s / 读取 30s（可配置），走 OpenAI 兼容协议。
     */
    @Bean("visionRestClient")
    public RestClient visionRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(visionProperties.getConnectTimeout());
        factory.setReadTimeout(visionProperties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(visionProperties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 微信开放平台客户端。
     */
    @Bean("wxRestClient")
    public RestClient wxRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder()
                .baseUrl("https://api.weixin.qq.com")
                .requestFactory(factory)
                .build();
    }
}