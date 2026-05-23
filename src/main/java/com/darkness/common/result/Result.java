package com.darkness.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 JSON 响应体，封装 {code, message, data} 格式的接口返回结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 响应状态码，200 表示成功，其他值表示各类错误 */
    private Integer code;

    /** 响应描述信息，成功时为 "success"，失败时为具体错误描述 */
    private String message;

    /** 响应数据体，成功时携带业务数据，失败时为 null（JSON 序列化时自动忽略） */
    private T data;

    /**
     * 构建成功响应，携带数据。
     *
     * @param data 业务数据
     * @return code=200, message="success" 的成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构建成功响应，无数据体。
     *
     * @return code=200, message="success" 的成功响应，data 为 null
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构建错误响应，指定 ResultCode 和自定义错误信息。
     *
     * @param resultCode 错误状态码枚举
     * @param message     错误描述信息
     * @return 携带错误信息的 Result，data 为 null
     */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /**
     * 构建错误响应，指定状态码和错误信息（向后兼容，逐步废弃）。
     *
     * @param code    错误状态码
     * @param message 错误描述信息
     * @return 携带错误信息的 Result，data 为 null
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 构建错误响应，使用默认 500 状态码。
     *
     * @param message 错误描述信息
     * @return code=500 的错误响应，data 为 null
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ResultCode.INTERNAL_ERROR.getCode(), message, null);
    }
}
