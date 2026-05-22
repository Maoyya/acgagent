package com.darkness;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * acgAgent 应用程序入口类，Spring Boot 启动引导。
 * <p>
 * 通过 {@code scanBasePackages = "com.darkness"} 自动扫描所有子包下的组件，
 * {@link MapperScan} 自动注册所有 mapper 子包下的 MyBatis Mapper 接口。
 */
@SpringBootApplication
@MapperScan("com.darkness.**.mapper")
public class Application {

    /**
     * 应用程序入口方法。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
