package com.darkness.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;

/**
 * HTTP 请求日志过滤器，记录方法、URI、Headers、耗时和状态码。
 * 请求体和响应体由 {@link ApiLoggingAdvice} 记录（含对象类名）。
 * 仅在 Servlet 容器环境下生效。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();

        log.info(">>> {} {}{}", method, uri, query != null ? "?" + query : "");
        log.info(">>> Headers: {}", extractHeaders(request));

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info("<<< {} {} -> {} ({}ms)", method, uri, response.getStatus(), elapsed);
        }
    }

    private String extractHeaders(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if ("authorization".equalsIgnoreCase(name) || "cookie".equalsIgnoreCase(name)) {
                sb.append(name).append("=***,");
            } else {
                sb.append(name).append("=").append(request.getHeader(name)).append(",");
            }
        }
        return sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";
    }
}
