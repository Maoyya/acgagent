package com.darkness.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * API 网关启动类，负责 JWT 鉴权、路由转发和限流。
 * 注意：Gateway 基于 WebFlux，不扫描 Mapper 和 MVC 组件。
 */
@SpringBootApplication(scanBasePackages = "com.darkness.gateway", exclude = {DataSourceAutoConfiguration.class})
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
