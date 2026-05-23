package com.darkness.common.exception;

import com.darkness.common.result.ResultCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常，携带 HTTP 风格的状态码，用于在业务逻辑中主动抛出可识别的错误。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizException extends RuntimeException {

    /** 错误状态码，采用 HTTP 风格，如 404-未找到、403-无权限、500-内部错误 */
    private Integer code;

    /**
     * 构造业务异常，指定 ResultCode 和自定义错误信息。
     *
     * @param resultCode 错误状态码枚举
     * @param message    错误描述信息
     */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 构造业务异常，指定状态码和错误信息（向后兼容，逐步废弃）。
     *
     * @param code    错误状态码
     * @param message 错误描述信息
     */
    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造业务异常，使用默认 500 状态码。
     *
     * @param message 错误描述信息
     */
    public BizException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_ERROR.getCode();
    }
}
