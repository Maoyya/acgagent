package com.darkness.common.exception;

import com.darkness.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，捕获 BizException 和未处理异常，统一返回 Result 响应体。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，将 BizException 中携带的状态码和错误信息包装为 Result 响应体返回。
     *
     * @param e 捕获到的业务异常
     * @return 包含业务错误码和错误描述的 {@link Result}，HTTP 状态码仍为 200
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理所有未捕获的未知异常，记录错误日志并返回统一的 500 内部错误响应。
     * <p>
     * 不向客户端暴露异常堆栈细节，固定返回 "Internal server error" 提示。
     *
     * @param e 捕获到的未知异常
     * @return code=500、message="Internal server error" 的 {@link Result}
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Internal server error", e);
        return Result.error(500, "Internal server error");
    }
}
