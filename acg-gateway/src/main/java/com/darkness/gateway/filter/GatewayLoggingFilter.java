package com.darkness.gateway.filter;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gateway 请求/响应日志过滤器，记录经过网关的所有请求和响应详情。
 * 优先级 -2，在 JwtAuthFilter(-1) 之前执行，确保所有请求（含白名单）都被记录。
 * 记录内容：方法、URI、Headers、请求体、响应状态码、响应体。
 */
@Component
public class GatewayLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayLoggingFilter.class);
    private static final int MAX_PAYLOAD_LENGTH = 2048;

    @Override
    public int getOrder() {
        return -2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String uri = request.getURI().toString();

        log.info(">>> {} {}", method, uri);
        logHeaders(request.getHeaders());

        long start = System.currentTimeMillis();

        // 装饰响应以捕获响应体
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        DataBufferFactory factory = originalResponse.bufferFactory();
                        byte[] content = new byte[dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum()];
                        int offset = 0;
                        for (DataBuffer buf : dataBuffers) {
                            int len = buf.readableByteCount();
                            buf.read(content, offset, len);
                            offset += len;
                        }
                        long elapsed = System.currentTimeMillis() - start;
                        HttpStatusCode status = getStatusCode();
                        log.info("<<< {} {} -> {} ({}ms)", method, uri, status != null ? status.value() : "N/A", elapsed);
                        log.info("<<< Body: {}", truncate(new String(content, StandardCharsets.UTF_8)));
                        return factory.wrap(content);
                    }));
                }
                return super.writeWith(body);
            }
        };

        // 读取请求体
        return request.getBody()
                .reduce(new ArrayList<DataBuffer>(), (list, buf) -> { list.add(buf); return list; })
                .defaultIfEmpty(new ArrayList<>())
                .flatMap(buffers -> {
                    if (!buffers.isEmpty()) {
                        byte[] bytes = new byte[buffers.stream().mapToInt(DataBuffer::readableByteCount).sum()];
                        int offset = 0;
                        for (DataBuffer buf : buffers) {
                            int len = buf.readableByteCount();
                            buf.read(bytes, offset, len);
                            offset += len;
                        }
                        log.info(">>> Body: {}", truncate(new String(bytes, StandardCharsets.UTF_8)));
                    }
                    return chain.filter(exchange.mutate().response(decoratedResponse).build());
                });
    }

    private void logHeaders(HttpHeaders headers) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if ("authorization".equalsIgnoreCase(name) || "cookie".equalsIgnoreCase(name)) {
                parts.add(name + "=***");
            } else {
                parts.add(name + "=" + String.join(",", entry.getValue()));
            }
        }
        log.info(">>> Headers: {}", String.join(",", parts));
    }

    private String truncate(String s) {
        if (s == null) return "null";
        return s.length() > MAX_PAYLOAD_LENGTH ? s.substring(0, MAX_PAYLOAD_LENGTH) + "...(truncated)" : s;
    }
}
