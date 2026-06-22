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

    /** Gateway 注入的角色头名（逗号分隔角色编码）。 */
    public static final String ROLES_HEADER = "X-User-Roles";

    /**
     * 读取当前用户的角色编码集合（来自 X-User-Roles 头）。
     * 无请求上下文或头缺失时返回空集。与 RoleAuthAspect 同源。
     *
     * @return 角色编码集合，不可变空集当无数据
     */
    public static java.util.Set<String> getRoles() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return java.util.Set.of();
        String header = request.getHeader(ROLES_HEADER);
        if (header == null || header.isBlank()) return java.util.Set.of();
        return java.util.Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 当前登录用户是否为管理员（角色含 "admin"）。
     *
     * @return true 当含 admin 角色
     */
    public static boolean isAdmin() {
        return getRoles().contains("admin");
    }

    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
