package com.darkness.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码加密与校验工具，底层使用 Spring Security 的 BCryptPasswordEncoder。
 * BCrypt 自动加盐，同一明文每次加密结果不同，安全性高于 MD5/SHA。
 */
@Component
public class PasswordUtil {

    /** BCrypt 编码器实例，强度为默认值 10 */
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 对明文密码进行 BCrypt 加密。
     * 自动生成随机盐并混入密文，每次调用结果不同。
     *
     * @param rawPassword 明文密码
     * @return BCrypt 加密后的密文字符串
     */
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * 校验明文密码与密文是否匹配。
     * BCrypt 会从密文中提取盐值后重新计算哈希进行比对。
     *
     * @param rawPassword     用户输入的明文密码
     * @param encodedPassword 数据库中存储的 BCrypt 密文
     * @return true 表示密码匹配，false 表示不匹配
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
