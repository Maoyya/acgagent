package com.darkness.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一业务响应状态码枚举。
 * 所有接口返回值和业务异常统一使用此枚举，禁止裸数字。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "Bad request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not found"),
    INTERNAL_ERROR(500, "Internal server error");

    private final int code;
    private final String message;
}
