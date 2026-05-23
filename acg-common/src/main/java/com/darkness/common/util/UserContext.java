package com.darkness.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户上下文工具类，从 Gateway 传递的请求 Header 中提取当前登录用户信息。
 */
public final class UserContext {
    private UserContext() {}

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USERNAME_HEADER = "X-Username";

    public static Long getUserId() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return null;
        String userIdStr = request.getHeader(USER_ID_HEADER);
        if (userIdStr == null || userIdStr.isEmpty()) return null;
        try {
            return Long.valueOf(userIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String getUsername() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return null;
        return request.getHeader(USERNAME_HEADER);
    }

    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
