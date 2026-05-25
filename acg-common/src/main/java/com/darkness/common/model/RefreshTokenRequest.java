package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 刷新令牌请求。
 */
@Data
public class RefreshTokenRequest {
    @NotBlank(message = "刷新令牌不能为空")
    @Size(max = 2048, message = "令牌长度不能超过2048个字符")
    private String refreshToken;
}
