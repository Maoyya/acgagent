package com.darkness.agent.client;

import com.darkness.common.exception.BizException;
import com.darkness.common.model.ChatEvent;
import com.darkness.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PythonAiClient 纯解析逻辑测试（WebClient HTTP/SSE 流按项目惯例归集成测试）。
 * 构造时传真实 ObjectMapper（纯解析方法依赖它），WebClient/Properties 传 null（不被调用）。
 */
class PythonAiClientTest {

    private final PythonAiClient client = new PythonAiClient(null, null, new ObjectMapper());

    @Test
    void parseChatEvent_contentEvent() {
        ChatEvent event = client.parseChatEvent("{\"type\":\"content\",\"content\":\"hi\"}");
        assertThat(event.getType()).isEqualTo("content");
        assertThat(event.getContent()).isEqualTo("hi");
    }

    @Test
    void parseChatEvent_doneEvent() {
        ChatEvent event = client.parseChatEvent("{\"type\":\"done\",\"usage\":{\"total_tokens\":5}}");
        assertThat(event.getType()).isEqualTo("done");
        assertThat(event.getUsage()).containsEntry("total_tokens", 5);
    }

    @Test
    void extractPythonAgentId_success_returnsDataId() {
        String json = "{\"code\":200,\"message\":\"success\",\"data\":{\"id\":\"py-123\",\"name\":\"A\"}}";
        assertThat(client.extractPythonAgentId(json)).isEqualTo("py-123");
    }

    @Test
    void extractPythonAgentId_pythonError_throwsBizExceptionWithCode() {
        String json = "{\"code\":400,\"message\":\"Agent is disabled\"}";
        assertThatThrownBy(() -> client.extractPythonAgentId(json))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(400);
    }

    @Test
    void verifySuccess_pythonError_throws() {
        assertThatThrownBy(() -> client.verifySuccess("{\"code\":500,\"message\":\"boom\"}"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(500);
    }

    @Test
    void verifySuccess_ok_doesNotThrow() {
        client.verifySuccess("{\"code\":200,\"message\":\"success\"}");
    }

    @Test
    void buildCreateBody_mapsAllFields_snakeCase() {
        com.darkness.common.entity.AgentDO agent = new com.darkness.common.entity.AgentDO();
        agent.setName("A");
        agent.setDescription("desc");
        agent.setSystemPrompt("sys");
        agent.setApiUrl("http://llm");
        agent.setApiKey("secret");
        agent.setModel("m");
        agent.setProvider("doubao");
        agent.setTemperature(new java.math.BigDecimal("0.7"));
        agent.setMaxTokens(4096);
        agent.setTopP(new java.math.BigDecimal("0.9"));
        agent.setMemoryType("conversation_window");
        agent.setMemoryMaxTokens(8000);
        agent.setCapabilities(java.util.List.of("chat", "rag"));
        agent.setKnowledgeBaseIds(java.util.List.of("kb1"));
        agent.setToolIds(java.util.List.of("calc"));

        Map<String, Object> body = client.buildCreateBody(agent);

        assertThat(body).containsEntry("name", "A");
        assertThat(body).containsEntry("system_prompt", "sys");
        @SuppressWarnings("unchecked")
        Map<String, Object> llm = (Map<String, Object>) body.get("llm_config");
        assertThat(llm).containsEntry("base_url", "http://llm");
        assertThat(llm).containsEntry("api_key", "secret");
        assertThat(llm).containsEntry("provider", "doubao");
        @SuppressWarnings("unchecked")
        Map<String, Object> mem = (Map<String, Object>) body.get("memory_config");
        assertThat(mem).containsEntry("type", "conversation_window");
        assertThat(body).containsEntry("capabilities", java.util.List.of("chat", "rag"));
        assertThat(body).containsEntry("knowledge_base_ids", java.util.List.of("kb1"));
        assertThat(body).containsEntry("tool_ids", java.util.List.of("calc"));
    }

    @Test
    void extractData_success_returnsDataObject() {
        String json = "{\"code\":200,\"data\":{\"id\":\"kb1\",\"name\":\"KB\"}}";
        com.darkness.common.model.KnowledgeBaseVO vo =
                client.extractData(json, com.darkness.common.model.KnowledgeBaseVO.class);
        assertThat(vo).isNotNull();
        assertThat(vo.getId()).isEqualTo("kb1");
        assertThat(vo.getName()).isEqualTo("KB");
    }

    @Test
    void extractData_pythonError_throwsWithCode() {
        String json = "{\"code\":404,\"message\":\"Knowledge base not found\"}";
        assertThatThrownBy(() -> client.extractData(json, com.darkness.common.model.KnowledgeBaseVO.class))
                .isInstanceOf(com.darkness.common.exception.BizException.class)
                .extracting("code").isEqualTo(404);
    }

    @Test
    void extractDataList_success() {
        String json = "{\"code\":200,\"data\":[{\"id\":\"d1\",\"file_name\":\"a.pdf\"},{\"id\":\"d2\",\"file_name\":\"b.txt\"}]}";
        java.util.List<com.darkness.common.model.DocumentVO> list =
                client.extractDataList(json, com.darkness.common.model.DocumentVO.class);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getFileName()).isEqualTo("a.pdf");
        assertThat(list.get(1).getId()).isEqualTo("d2");
    }
}
