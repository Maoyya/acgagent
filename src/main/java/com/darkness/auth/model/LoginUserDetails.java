package com.darkness.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 认证主体，封装当前登录用户的身份信息。
 * 在 JWT 无密码模式下仅通过 userId 标识身份。
 */
@Data
@AllArgsConstructor
public class LoginUserDetails implements UserDetails {

    /** 系统用户 ID，JWT 无密码模式下作为主要身份标识 */
    private Long userId;

    /** 用户名，用于 Spring Security 兼容，JWT 模式下可为空 */
    private String username;

    /** 密码密文，JWT 无密码模式下不需要，可为空 */
    private String password;

    /** 用户权限集合，JWT 模式下可为空（暂未启用角色鉴权） */
    private Collection<? extends GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities != null ? authorities : List.of();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
