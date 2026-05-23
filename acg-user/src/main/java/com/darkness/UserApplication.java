package com.darkness;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户 + 认证服务启动类。
 */
@SpringBootApplication(scanBasePackages = "com.darkness")
@MapperScan("com.darkness.common.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
