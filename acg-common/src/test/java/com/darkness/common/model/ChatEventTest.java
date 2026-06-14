package com.darkness.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatEvent 序列化测试：镜像 Python 的 SSE 事件 schema（snake_case）。
 * 防止字段命名漂移导致前端解析失败。
 */
class ChatEventTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_pythonContentEvent() throws Exception {
        // Python 实际下发的 snake_case 报文
        String json = "{\"type\":\"content\",\"content\":\"你好\"}";
        ChatEvent event = mapper.readValue(json, ChatEvent.class);
        assertThat(event.getType()).isEqualTo("content");
        assertThat(event.getContent()).isEqualTo("你好");
    }

    @Test
    void deserialize_pythonToolCallEvent_snakeCase() throws Exception {
        String json = "{\"type\":\"tool_call\",\"tool_name\":\"knowledge_search\",\"tool_input\":{\"q\":\"x\"}}";
        ChatEvent event = mapper.readValue(json, ChatEvent.class);
        assertThat(event.getType()).isEqualTo("tool_call");
        assertThat(event.getToolName()).isEqualTo("knowledge_search");
        assertThat(event.getToolInput()).containsEntry("q", "x");
    }

    @Test
    void deserialize_pythonDoneEvent() throws Exception {
        String json = "{\"type\":\"done\",\"usage\":{\"total_tokens\":12}}";
        ChatEvent event = mapper.readValue(json, ChatEvent.class);
        assertThat(event.getType()).isEqualTo("done");
        assertThat(event.getUsage()).containsEntry("total_tokens", 12);
    }
}
