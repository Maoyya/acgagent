package com.darkness.user.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Spring MVC 静态资源映射配置。
 * 将头像上传目录映射为可通过 HTTP 访问的静态资源路径，
 * 使前端能通过 /api/user/profile/avatars/** URL 直接访问上传的头像文件。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FileUploadConfig fileUploadConfig;

    /**
     * 注册静态资源处理器：将 urlPrefix 路径映射到本地文件上传目录。
     * 例如 /api/user/profile/avatars/** → file:///tmp/acg-avatar/
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(fileUploadConfig.getDir()).toUri().toString();
        registry.addResourceHandler(fileUploadConfig.getUrlPrefix() + "/**")
                .addResourceLocations(location);
    }
}
