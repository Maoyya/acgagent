package com.darkness.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信开放平台配置属性，读取 wx.open.appId 和 wx.open.appSecret。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wx.open")
public class WxConfig {

    /** 微信开放平台应用 ID（AppID），对应配置项 wx.open.appId */
    private String appId;

    /** 微信开放平台应用密钥（AppSecret），对应配置项 wx.open.appSecret，用于服务端换取 access_token */
    private String appSecret;
}
