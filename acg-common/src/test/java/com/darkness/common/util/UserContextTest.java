package com.darkness.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** UserContext 角色读取测试。业务意义：isAdmin 必须正确解析网关注入的 X-User-Roles 头（双维度权限前置）。 */
class UserContextTest {

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void isAdmin_true_whenHeaderContainsAdmin() {
        setRolesHeader("user,admin");
        assertThat(UserContext.isAdmin()).isTrue();
    }

    @Test
    void isAdmin_false_whenNoAdminRole() {
        setRolesHeader("user");
        assertThat(UserContext.isAdmin()).isFalse();
    }

    @Test
    void getRoles_empty_whenNoRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        assertThat(UserContext.getRoles()).isEmpty();
        assertThat(UserContext.isAdmin()).isFalse();
    }

    private void setRolesHeader(String roles) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-User-Roles")).thenReturn(roles);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }
}
