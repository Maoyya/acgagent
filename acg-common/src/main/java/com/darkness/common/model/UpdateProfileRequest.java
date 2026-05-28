package com.darkness.common.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人资料请求。所有字段均为可选，仅传递需要修改的字段。
 */
@Data
public class UpdateProfileRequest {

    /** 用户昵称，用于前端展示，最长 64 个字符 */
    @Size(max = 64, message = "昵称长度不能超过64个字符")
    private String nickname;

    /** 邮箱地址，需符合标准邮箱格式，最长 128 个字符 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    private String email;
}
