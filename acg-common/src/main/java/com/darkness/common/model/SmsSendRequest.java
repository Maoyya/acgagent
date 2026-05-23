package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送短信验证码请求。
 */
@Data
public class SmsSendRequest {
    @NotBlank(message = "Phone number is required")
    private String phone;
}
