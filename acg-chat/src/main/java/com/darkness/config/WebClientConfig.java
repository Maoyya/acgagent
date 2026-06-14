package com.darkness.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient 配置。
 * <p>
 * pythonWebClient：调用 Python AI 引擎的专用客户端，预置 baseUrl 与连接超时；
 * 普通请求与 SSE 流式的读超时在 PythonAiClient 调用处通过 reactor .timeout() 区分应用。
 */
@Configuration
public class WebClientConfig {

    /**
     * Python 引擎专用 WebClient。
     * 连接超时由 python-agent.connect-timeout 控制（默认 5s）。
     *
     * @param props Python 连接配置
     * @return 预置 baseUrl 与连接超时的 WebClient
     */
    @Bean
    public WebClient pythonWebClient(PythonAgentProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeout());
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
