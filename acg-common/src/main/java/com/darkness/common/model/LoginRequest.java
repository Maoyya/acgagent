package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账号密码登录请求。
 */
@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 5, max = 50, message = "用户名长度必须在5-50个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 256, message = "密码长度必须在6-256个字符之间")
    private String password;
}
