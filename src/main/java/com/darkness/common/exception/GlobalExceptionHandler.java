package com.darkness.common.exception;

import com.darkness.common.result.Result;
import com.darkness.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，捕获 BizException、JSR 303 校验异常和未处理异常，统一返回 Result 响应体。
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
     * 处理 JSR 303 参数校验异常，收集所有字段校验失败信息后返回 400 响应。
     *
     * @param e 校验异常
     * @return code=400、message 包含所有校验失败信息的 {@link Result}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 处理所有未捕获的未知异常，记录错误日志并返回统一的 500 内部错误响应。
     * 不向客户端暴露异常堆栈细节，固定返回 "Internal server error" 提示。
     *
     * @param e 捕获到的未知异常
     * @return code=500、message="Internal server error" 的 {@link Result}
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Internal server error", e);
        return Result.error(ResultCode.INTERNAL_ERROR, ResultCode.INTERNAL_ERROR.getMessage());
    }
}
