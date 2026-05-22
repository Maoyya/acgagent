package com.darkness.config;

import com.darkness.auth.filter.JwtAuthenticationFilter;
import com.darkness.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置，定义 JWT 无状态认证、URL 权限规则和 JWT 过滤器注册。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** JWT 认证过滤器，解析请求头中的 Token 并设置 Spring Security 上下文 */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** JSON 序列化工具，用于未认证时写入错误响应体 */
    private final ObjectMapper objectMapper;

    /**
     * 配置 Spring Security 过滤链。
     * <ul>
     *   <li>禁用 CSRF（无状态 JWT 方案不需要）</li>
     *   <li>Session 策略设为 STATELESS，不创建 HttpSession</li>
     *   <li>URL 权限规则：
     *     <ul>
     *       <li>/api/auth/** — 认证相关接口（登录、注册），允许匿名访问</li>
     *       <li>/druid/** — Druid 监控页面，允许匿名访问</li>
     *       <li>GET /api/agents、GET /api/agents/* — Agent 列表和详情，允许匿名浏览</li>
     *       <li>其余所有请求 — 需要认证</li>
     *     </ul>
     *   </li>
     *   <li>未认证请求返回 401 JSON 响应（而非默认的 302 重定向到登录页）</li>
     *   <li>在 UsernamePasswordAuthenticationFilter 之前插入 JWT 过滤器</li>
     * </ul>
     *
     * @param http HttpSecurity 构建器
     * @return 配置好的 SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/druid/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/agents").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/agents/*").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Result.error(401, "Unauthorized")));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
