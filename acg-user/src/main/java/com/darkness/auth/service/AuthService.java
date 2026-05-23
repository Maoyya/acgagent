package com.darkness.auth.service;

import com.darkness.common.model.TokenVO;
import com.darkness.common.model.UserVO;

/**
 * 认证服务接口，处理用户注册、密码登录、Token 刷新和 Token 对生成。
 * 所有认证流程最终通过 generateTokenPair 签发 JWT 令牌对。
 */
public interface AuthService {

    /**
     * 用户注册。
     * 校验用户名非空且唯一（已存在时抛出 BizException），使用 BCrypt 加密明文密码后创建用户记录。
     *
     * @param username 用户名，不能为空且不能重复
     * @param password 明文密码，不能为空
     * @return 注册后的用户视图对象
     */
    UserVO register(String username, String password);

    /**
     * 账号密码登录。
     * 根据用户名查询用户，校验 BCrypt 密码匹配，失败时抛出 BizException。
     * 校验通过后调用 generateTokenPair 签发 accessToken 和 refreshToken。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return Token 对（accessToken + refreshToken）
     */
    TokenVO login(String username, String password);

    /**
     * 使用 refreshToken 刷新令牌。
     * 校验 refreshToken 有效性且 token 类型必须为 refresh（非 refresh 类型会抛出 BizException），
     * 解析出 userId 后重新签发新的 Token 对。
     *
     * @param refreshToken 刷新令牌
     * @return 新的 Token 对
     */
    TokenVO refresh(String refreshToken);

    /**
     * 为指定用户生成 Token 对。
     * 使用 JwtUtil 同时生成 accessToken（默认 2 小时有效）和 refreshToken（默认 7 天有效），
     * 封装为 TokenVO 返回，expiresIn 为 accessToken 的剩余秒数。
     *
     * @param userId 用户 ID
     * @return Token 对
     */
    TokenVO generateTokenPair(Long userId);
}
