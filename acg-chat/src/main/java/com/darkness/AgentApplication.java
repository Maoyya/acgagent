package com.darkness;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent + 对话服务启动类。
 */
@SpringBootApplication(scanBasePackages = "com.darkness")
@MapperScan("com.darkness.common.mapper")
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
