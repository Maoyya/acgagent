package com.darkness.auth.filter;

import com.darkness.auth.model.LoginUserDetails;
import com.darkness.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器，从请求头 Authorization 中提取 Bearer Token，
 * 解析成功后将用户身份写入 Spring Security 上下文。
 * <p>
 * 过滤逻辑：
 * <ol>
 *   <li>从请求头 Authorization 中提取 Bearer Token（截取 "Bearer " 之后的部分）</li>
 *   <li>调用 JwtUtil 校验令牌有效性（签名 + 过期时间）</li>
 *   <li>解析出 userId，构造 LoginUserDetails 并写入 SecurityContext</li>
 *   <li>无论 Token 是否存在或有效，都继续执行后续过滤器链</li>
 * </ol>
 * 在 JWT 无密码模式下仅用 userId 做身份标识，不需要密码和权限。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    /**
     * 执行 JWT 认证过滤。
     * 从 Authorization 头提取 Bearer Token，校验通过后构造认证对象写入 SecurityContext。
     * 无论 Token 是否存在或有效，最终都会调用 filterChain.doFilter 继续后续处理。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isTokenValid(token)) {
                Long userId = jwtUtil.getUserId(token);
                // JWT 无密码模式下仅用 userId 做身份标识，不需要密码和权限
                LoginUserDetails userDetails = new LoginUserDetails(userId, null, null, null);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
