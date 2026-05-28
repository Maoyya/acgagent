package com.darkness.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gateway JWT 鉴权全局过滤器。
 * 从 Authorization header 提取 Bearer Token，本地验签后将 userId 写入下游请求 header。
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String secret;

    /** 白名单路径前缀——这些路径不需要鉴权 */
    private static final String[] WHITE_LIST = {
            "/api/auth/",
            "/druid/"
    };

    /** Agent 详情路径正则，预编译避免每次请求重新编译 */
    private static final Pattern AGENT_ID_PATH = Pattern.compile("^/api/agents/\\d+$");

    /** 缓存的签名密钥，初始化后不再重新生成 */
    private volatile SecretKey signingKey;

    private SecretKey getSigningKey() {
        if (signingKey == null) {
            signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        return signingKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 白名单放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // GET /api/agents 和 GET /api/agents/{id} 放行
        if ("GET".equals(exchange.getRequest().getMethod().name())
                && (path.equals("/api/agents") || AGENT_ID_PATH.matcher(path).matches())) {
            return chain.filter(exchange);
        }

        // 提取 Token
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();

            // 从 JWT 中提取用户角色
            Object rolesClaim = claims.get("roles");
            String rolesStr = "";
            if (rolesClaim instanceof List<?> rolesList && !rolesList.isEmpty()) {
                rolesStr = rolesList.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
            }

            // 将用户 ID 和角色写入下游请求 header
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Roles", rolesStr)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isWhitelisted(String path) {
        for (String prefix : WHITE_LIST) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
