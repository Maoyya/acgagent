package com.darkness.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证令牌响应体，包含 accessToken、refreshToken 和过期时间。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO {
    /** 访问令牌，用于接口鉴权，默认 2 小时有效 */
    private String accessToken;

    /** 刷新令牌，用于续期 accessToken，默认 7 天有效 */
    private String refreshToken;

    /** accessToken 的剩余有效时间，单位为秒 */
    private Long expiresIn;
}
