package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 微信扫码登录回调请求。
 */
@Data
public class WxLoginRequest {
    @NotBlank(message = "授权码不能为空")
    @Size(max = 128, message = "授权码长度不能超过128个字符")
    private String code;
}
