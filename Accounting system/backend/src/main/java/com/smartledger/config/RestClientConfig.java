package com.smartledger.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 第三方 HTTP 客户端配置：DeepSeek 与微信开放平台各一个 RestClient 实例。
 */
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final AiProperties aiProperties;

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