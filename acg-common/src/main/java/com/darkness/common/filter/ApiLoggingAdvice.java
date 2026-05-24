package com.darkness.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * API 请求/响应体日志增强，记录反序列化后的对象类名、方法路径和 JSON 内容。
 * 与 {@link RequestLoggingFilter} 配合使用，Filter 负责基本信息（URI、Headers、耗时），
 * 本类负责对象级别的出入参记录。
 * 仅在 Servlet 容器环境下生效。
 */
@ControllerAdvice
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice")
public class ApiLoggingAdvice implements RequestBodyAdvice, ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingAdvice.class);
    private static final int MAX_PAYLOAD_LENGTH = 2048;

    private final ObjectMapper objectMapper;

    public ApiLoggingAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ==================== RequestBodyAdvice ====================

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                            Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return inputMessage;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                 Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        logRequest(parameter, body);
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                   Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    // ==================== ResponseBodyAdvice ====================

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (body != null) {
            String className = body.getClass().getSimpleName();
            // 跳过 SSE 流式对象（无法序列化）
            if (!"SseEmitter".equals(className)) {
                String methodPath = returnType.getDeclaringClass().getSimpleName() + "." + returnType.getMethod().getName();
                log.info("<<< Method: {}", methodPath);
                log.info("<<< Body [{}]: {}", className, truncate(toJson(body)));
            }
        }
        return body;
    }

    // ==================== private ====================

    private void logRequest(MethodParameter parameter, Object body) {
        String className = body.getClass().getSimpleName();
        String methodPath = parameter.getDeclaringClass().getSimpleName() + "." + parameter.getMethod().getName();
        log.info(">>> Method: {}", methodPath);
        log.info(">>> Body [{}]: {}", className, truncate(toJson(body)));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String truncate(String s) {
        if (s == null) return "null";
        return s.length() > MAX_PAYLOAD_LENGTH ? s.substring(0, MAX_PAYLOAD_LENGTH) + "...(truncated)" : s;
    }
}
