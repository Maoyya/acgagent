package com.darkness.agent.client;

import com.darkness.agent.constant.SseConstants;
import com.darkness.agent.entity.AgentDO;
import com.darkness.agent.entity.MessageDO;
import com.darkness.common.constant.AuthConstants;
import com.darkness.common.enums.MessageRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI Agent 远程调用客户端，通过 SSE 流式协议与 AI 模型 API 通信。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentClient {

    /** JSON 序列化/反序列化工具，用于解析 SSE chunk 中的 JSON 数据提取 delta 内容 */
    private final ObjectMapper objectMapper;

    /**
     * 以流式方式调用 AI 模型 API，逐 token 返回生成内容。
     * 请求格式遵循 OpenAI ChatCompletion 兼容协议（model + messages + stream=true），
     * 通过 SSE 协议接收响应，逐行解析 data 前缀的 JSON chunk，从每个 chunk 的
     * choices[0].delta.content 路径提取增量文本，跳过结束标记和非法行，
     * 最终以 Flux 流形式返回拼接后的完整文本。
     *
     * @param agent       Agent 配置（含 apiUrl、apiKey、model）
     * @param history     历史消息列表，用于构建上下文
     * @param userMessage 当前用户输入
     * @return Flux 流，每次发射一段增量文本内容
     */
    public Flux<String> stream(AgentDO agent, List<MessageDO> history, String userMessage) {
        List<Map<String, String>> messages = history.stream()
                .map(m -> Map.of("role", m.getRole().getValue(), "content", m.getContent()))
                .collect(Collectors.toList());
        messages.add(Map.of("role", MessageRole.USER.getValue(), "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model", agent.getModel());
        body.put("messages", messages);
        body.put("stream", true);

        return WebClient.create(agent.getApiUrl())
                .post()
                .header("Authorization", AuthConstants.BEARER_PREFIX + agent.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                // 过滤空行和非数据行，跳过结束标记
                .filter(line -> !line.isBlank() && line.startsWith(SseConstants.DATA_PREFIX))
                .map(line -> line.substring(SseConstants.DATA_PREFIX.length()).trim())
                .filter(data -> !SseConstants.DONE_MARKER.equals(data))
                .handle((data, sink) -> {
                    try {
                        JsonNode json = objectMapper.readTree(data);
                        JsonNode delta = json.at("/choices/0/delta/content");
                        if (!delta.isMissingNode() && !delta.isNull()) {
                            sink.next(delta.asText());
                        }
                    } catch (Exception e) {
                        log.debug("Skipping non-JSON SSE chunk: {}", data);
                    }
                });
    }
}
