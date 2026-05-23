package com.darkness.common.util;

import com.darkness.common.enums.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌工具类，负责 Access Token 和 Refresh Token 的签发、解析与校验。
 * <p>
 * 使用 HMAC-SHA 算法签名，密钥来自配置项 jwt.secret。
 * Token 结构：header.payload.signature，payload 中包含 subject（userId）、type（access/refresh）、签发时间和过期时间。
 * </p>
 * <ul>
 *   <li>accessToken — 短期令牌，默认 2 小时有效，用于接口鉴权</li>
 *   <li>refreshToken — 长期令牌，默认 7 天有效，仅用于刷新 accessToken</li>
 * </ul>
 */
@Component
public class JwtUtil {

    /** JWT 签名密钥，对应配置项 jwt.secret，至少 32 字节以满足 HMAC-SHA 要求 */
    @Value("${jwt.secret}")
    private String secret;

    /** accessToken 过期时间，单位毫秒，对应配置项 jwt.access-token-expiration，默认 2 小时 */
    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    /** refreshToken 过期时间，单位毫秒，对应配置项 jwt.refresh-token-expiration，默认 7 天 */
    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * 根据配置的 secret 构建 HMAC-SHA 签名密钥。
     *
     * @return SecretKey 实例
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 accessToken。
     * payload 中 subject 为 userId，type 声明为 ACCESS，签发时间为当前时间，
     * 过期时间为当前时间 + accessTokenExpiration。
     *
     * @param userId 用户 ID
     * @return 签名后的 JWT 字符串
     */
    public String generateAccessToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", TokenType.ACCESS.getValue())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成 refreshToken。
     * payload 中 subject 为 userId，type 声明为 REFRESH，签发时间为当前时间，
     * 过期时间为当前时间 + refreshTokenExpiration。
     *
     * @param userId 用户 ID
     * @return 签名后的 JWT 字符串
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", TokenType.REFRESH.getValue())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 JWT 令牌，返回 Claims 载荷。
     * 验证签名和过期时间，签名不匹配或已过期时抛出异常。
     *
     * @param token JWT 字符串
     * @return Claims 载荷，包含 subject、type、iat、exp 等声明
     * @throws io.jsonwebtoken.JwtException 签名无效或令牌过期时抛出
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从令牌中提取用户 ID。
     * 直接解析 token 的 subject 字段并转换为 Long。
     *
     * @param token JWT 字符串
     * @return 用户 ID
     */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    /**
     * 校验令牌是否有效（签名合法且未过期）。
     * 内部调用 parseToken，捕获所有异常后返回 false。
     *
     * @param token JWT 字符串
     * @return true 表示令牌有效，false 表示无效或已过期
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断令牌是否为 refreshToken 类型。
     * 解析 token 的 type 声明，值为 REFRESH 时返回 true。
     *
     * @param token JWT 字符串
     * @return true 表示是 refreshToken，false 表示是 accessToken
     */
    public boolean isRefreshToken(String token) {
        return TokenType.REFRESH.getValue().equals(parseToken(token).get("type", String.class));
    }
}
