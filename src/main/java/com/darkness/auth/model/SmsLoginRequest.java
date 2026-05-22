package com.darkness.auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 短信验证码登录请求。
 */
@Data
public class SmsLoginRequest {
    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "Verification code is required")
    private String code;
}
