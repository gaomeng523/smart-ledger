package com.smartledger;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 智能记账小程序 · 后端启动类
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.smartledger.mapper")
@EnableScheduling
public class SmartLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartLedgerApplication.class, args);
    }
}