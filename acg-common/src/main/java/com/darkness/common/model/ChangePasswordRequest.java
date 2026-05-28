package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求。需提供旧密码验证身份，新密码需两次输入确认一致。
 */
@Data
public class ChangePasswordRequest {

    /** 当前使用的旧密码 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码，长度 6-256 个字符 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 256, message = "新密码长度必须在6-256个字符之间")
    private String newPassword;

    /** 确认新密码，需与 newPassword 一致 */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
