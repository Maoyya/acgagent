package com.darkness.user.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传配置，对应 application.yml 中 file.upload 前缀的配置项。
 * <p>
 * 默认将头像文件保存到 /tmp/acg-avatar 目录，通过 /api/user/profile/avatars 路径提供静态资源访问。
 */
@Data
@Component
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {

    /** 文件保存的本地目录路径，默认 /tmp/acg-avatar */
    private String dir = "/tmp/acg-avatar";

    /** 文件访问的 URL 前缀，对应 WebMvcConfig 中配置的静态资源映射路径 */
    private String urlPrefix = "/api/user/profile/avatars";
}
