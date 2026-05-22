package com.darkness.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.auth.model.TokenVO;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.util.JwtUtil;
import com.darkness.auth.util.PasswordUtil;
import com.darkness.common.exception.BizException;
import com.darkness.user.entity.UserDO;
import com.darkness.user.mapper.UserMapper;
import com.darkness.user.model.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现，处理用户注册、密码登录和 Token 对生成。
 * <p>
 * 注册流程：校验用户名非空 + 唯一性 -> BCrypt 加密密码 -> 插入 user 表 -> 返回 UserVO。
 * 登录流程：按用户名查询 -> BCrypt 比对密码 -> 签发 accessToken + refreshToken。
 * 刷新流程：校验 refreshToken 有效且类型为 refresh -> 重新签发 Token 对。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册。
     * 校验用户名和密码非空，查询 user 表确认用户名唯一（已存在则抛 BizException(400)），
     * 使用 BCrypt 加密明文密码后插入用户记录，默认昵称取用户名，状态设为 1（启用）。
     *
     * @param username 用户名，不能为空且不能重复
     * @param password 明文密码，不能为空
     * @return 注册后的用户视图对象
     */
    @Override
    public UserVO register(String username, String password) {
        if (username == null || username.isBlank()) throw new BizException(400, "Username is required");
        if (password == null || password.isBlank()) throw new BizException(400, "Password is required");

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (count > 0) throw new BizException(400, "Username already exists");

        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(passwordUtil.encode(password));
        user.setNickname(username);
        user.setStatus(1);
        userMapper.insert(user);
        return UserVO.from(user);
    }

    /**
     * 账号密码登录。
     * 根据用户名查询 user 表，用户不存在或密码不匹配时抛出 BizException(401)，
     * 校验通过后调用 generateTokenPair 签发 accessToken 和 refreshToken。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return Token 对（accessToken + refreshToken + expiresIn）
     */
    @Override
    public TokenVO login(String username, String password) {
        UserDO user = userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (user == null) throw new BizException(401, "Invalid credentials");
        if (user.getPassword() == null || !passwordUtil.matches(password, user.getPassword())) {
            throw new BizException(401, "Invalid credentials");
        }
        return generateTokenPair(user.getId());
    }

    /**
     * 使用 refreshToken 刷新令牌。
     * 依次校验 refreshToken 非空、签名有效（未过期）、类型为 refresh，任一不满足则抛出 BizException(401)。
     * 解析出 userId 后重新签发新的 Token 对。
     *
     * @param refreshToken 刷新令牌
     * @return 新的 Token 对
     */
    @Override
    public TokenVO refresh(String refreshToken) {
        if (refreshToken == null || !jwtUtil.isTokenValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BizException(401, "Invalid refresh token");
        }
        Long userId = jwtUtil.getUserId(refreshToken);
        return generateTokenPair(userId);
    }

    /**
     * 为指定用户生成 Token 对（accessToken + refreshToken）。
     * accessToken 默认 2 小时有效，refreshToken 默认 7 天有效，expiresIn 固定返回 7200 秒。
     *
     * @param userId 用户 ID
     * @return Token 对
     */
    @Override
    public TokenVO generateTokenPair(Long userId) {
        String accessToken = jwtUtil.generateAccessToken(userId);
        String refreshToken = jwtUtil.generateRefreshToken(userId);
        // TODO: expiresIn 应与 jwt.access-token-expiration 配置保持一致
        return new TokenVO(accessToken, refreshToken, 7200L);
    }
}
