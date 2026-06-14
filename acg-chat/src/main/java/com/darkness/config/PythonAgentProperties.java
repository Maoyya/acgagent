package com.darkness.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python AI 引擎连接配置，绑定 Nacos acg-chat.yaml 中 python-agent.* 前缀的配置。
 * <p>
 * api-key 仅在 Java 服务端使用（调用 Python 的 X-API-Key），绝不返回前端。
 */
@Data
@Component
@ConfigurationProperties(prefix = "python-agent")
public class PythonAgentProperties {

    /** Python 服务基础地址，如 http://127.0.0.1:8100 */
    private String baseUrl;

    /** 调用 Python 的 X-API-Key（与 Python acgagent-ai/.env 的 ACG_AI_API_KEY 一致） */
    private String apiKey;

    /** 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** 普通请求读超时（毫秒），通过 reactor .timeout() 在调用处生效 */
    private int readTimeout = 10000;

    /** SSE 流式读超时（毫秒），对齐 chat.sse-timeout（默认 5 分钟） */
    private int streamReadTimeout = 300000;
}
