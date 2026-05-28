package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改手机号请求。需提供新手机号和该手机号收到的短信验证码。
 */
@Data
public class ChangePhoneRequest {

    /** 新手机号，必须为合法的 11 位中国大陆手机号 */
    @NotBlank(message = "新手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    private String newPhone;

    /** 短信验证码，6 位数字，5 分钟内有效 */
    @NotBlank(message = "验证码不能为空")
    private String verifyCode;
}
