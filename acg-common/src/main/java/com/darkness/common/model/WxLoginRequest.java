package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信扫码登录回调请求。
 */
@Data
public class WxLoginRequest {
    @NotBlank(message = "Authorization code is required")
    private String code;
}
