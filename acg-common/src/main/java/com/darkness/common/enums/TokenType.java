package com.darkness.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * JWT Token 类型枚举，用于 JwtUtil 中区分 access/refresh 令牌。
 */
@Getter
@AllArgsConstructor
public enum TokenType {

    ACCESS("access"),
    REFRESH("refresh");

    private final String value;
}
