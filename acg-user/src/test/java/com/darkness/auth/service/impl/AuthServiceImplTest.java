package com.darkness.auth.service.impl;

import com.darkness.auth.util.PasswordUtil;
import com.darkness.common.entity.UserDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.UserMapper;
import com.darkness.common.model.TokenVO;
import com.darkness.common.model.UserVO;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 认证服务单元测试，覆盖注册、登录、Token 刷新的核心业务场景。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordUtil passwordUtil;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    // ==================== register ====================

    @Test
    void register_success() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordUtil.encode("password123")).thenReturn("$2a$10$encoded");
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            UserDO user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });

        UserVO result = authService.register("testuser", "password123");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void register_emptyUsername_throwsBadRequest() {
        assertThatThrownBy(() -> authService.register("", "password123"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    @Test
    void register_nullUsername_throwsBadRequest() {
        assertThatThrownBy(() -> authService.register(null, "password123"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    @Test
    void register_emptyPassword_throwsBadRequest() {
        assertThatThrownBy(() -> authService.register("testuser", ""))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    @Test
    void register_duplicateUsername_throwsBadRequest() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> authService.register("testuser", "password123"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    // ==================== login ====================

    @Test
    void login_success() {
        UserDO user = new UserDO();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("$2a$10$encoded");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordUtil.matches("password123", "$2a$10$encoded")).thenReturn(true);
        when(jwtUtil.generateAccessToken(1L)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(1L)).thenReturn("refresh-token");

        TokenVO result = authService.login("testuser", "password123");

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void login_userNotFound_throwsUnauthorized() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login("nouser", "password123"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        UserDO user = new UserDO();
        user.setId(1L);
        user.setPassword("$2a$10$encoded");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordUtil.matches("wrongpass", "$2a$10$encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("testuser", "wrongpass"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void login_nullPasswordInDb_throwsUnauthorized() {
        UserDO user = new UserDO();
        user.setId(1L);
        user.setPassword(null);
        when(userMapper.selectOne(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.login("testuser", "password123"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    // ==================== refresh ====================

    @Test
    void refresh_success() {
        when(jwtUtil.isTokenValid("valid-refresh-token")).thenReturn(true);
        when(jwtUtil.isRefreshToken("valid-refresh-token")).thenReturn(true);
        when(jwtUtil.getUserId("valid-refresh-token")).thenReturn(1L);
        when(jwtUtil.generateAccessToken(1L)).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(1L)).thenReturn("new-refresh");

        TokenVO result = authService.refresh("valid-refresh-token");

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refresh_nullToken_throwsUnauthorized() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void refresh_invalidToken_throwsUnauthorized() {
        when(jwtUtil.isTokenValid("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("invalid-token"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void refresh_notRefreshToken_throwsUnauthorized() {
        when(jwtUtil.isTokenValid("access-token")).thenReturn(true);
        when(jwtUtil.isRefreshToken("access-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("access-token"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }
}
