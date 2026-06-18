package com.darkness.agent.client;

import com.darkness.common.entity.AgentDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.model.ChatEvent;
import com.darkness.common.model.DocumentVO;
import com.darkness.common.model.KnowledgeBaseRequest;
import com.darkness.common.model.KnowledgeBaseVO;
import com.darkness.common.model.ToolCreateRequest;
import com.darkness.common.model.ToolVO;
import com.darkness.common.result.ResultCode;
import com.darkness.config.PythonAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python AI 引擎（acgagent-ai）远程调用客户端，是 acg-chat 调用 Python 的唯一出口。
 * <p>
 * 职责：
 * <ul>
 *   <li>Agent 配置同步：createAgent / updateAgent / deleteAgent（同步、非流式）</li>
 *   <li>对话流式：streamChat 返回结构化 ChatEvent 的 Flux</li>
 *   <li>知识库/文档/工具代理：KB/Doc/Tool 的 CRUD（含 multipart 文档上传），经 extractData/extractDataList 解析 Python Result</li>
 * </ul>
 * 所有请求带 X-API-Key；对话请求额外带 X-User-Id 透传当前用户。
 * 普通/流式请求分别应用 readTimeout / streamReadTimeout（reactor .timeout()）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonAiClient {

    private final WebClient pythonWebClient;
    private final PythonAgentProperties props;
    private final ObjectMapper objectMapper;

    /**
     * 流式调用 Python 对话接口，返回结构化 ChatEvent 流。
     * <p>
     * Python SSE 每行为 {@code data: {json}}；本方法逐行处理 SSE 流，
     * 反序列化为 ChatEvent。流式读超时由 streamReadTimeout 控制。
     *
     * @param pythonAgentId Python 侧 agent id（同步锚点）
     * @param conversationId Java 会话 id，转字符串作为 Python conversation_id（Python 据此自管 memory）
     * @param message       用户输入
     * @param userId        当前用户 id，透传 X-User-Id
     * @return ChatEvent 流
     */
    public Flux<ChatEvent> streamChat(String pythonAgentId, Long conversationId, String message, Long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversation_id", String.valueOf(conversationId));
        body.put("message", message);
        body.put("stream", true);

        return pythonWebClient.post()
                .uri("/api/v1/chat/{agentId}/completions", pythonAgentId)
                .header("X-API-Key", props.getApiKey())
                .header("X-User-Id", String.valueOf(userId))
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line != null && line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .filter(data -> !data.isEmpty())
                .map(this::parseChatEvent)
                .timeout(Duration.ofMillis(props.getStreamReadTimeout()));
    }

    /**
     * 在 Python 创建 Agent，返回 Python 分配的 string id（用于回写 python_agent_id）。
     * Python 返回非 200 时抛 BizException（携带 Python 的 code）。
     */
    public String createAgent(AgentDO agent) {
        String json = pythonWebClient.post()
                .uri("/api/v1/agents")
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(buildCreateBody(agent))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout()))
                .block();
        return extractPythonAgentId(json);
    }

    /**
     * 同步更新 Python 侧 Agent。Python 返回非 200 时抛 BizException。
     */
    public void updateAgent(String pythonAgentId, AgentDO agent) {
        String json = pythonWebClient.put()
                .uri("/api/v1/agents/{agentId}", pythonAgentId)
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(buildCreateBody(agent))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout()))
                .block();
        verifySuccess(json);
    }

    /**
     * 删除 Python 侧 Agent。Python 返回非 200 时抛 BizException。
     */
    public void deleteAgent(String pythonAgentId) {
        String json = pythonWebClient.delete()
                .uri("/api/v1/agents/{agentId}", pythonAgentId)
                .header("X-API-Key", props.getApiKey())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout()))
                .block();
        verifySuccess(json);
    }

    /**
     * 把 SSE data 行的 JSON 反序列化为 ChatEvent。
     * 非法 JSON 记日志并抛 BizException（中断流，触发 doOnError）。
     */
    public ChatEvent parseChatEvent(String data) {
        try {
            return objectMapper.readValue(data, ChatEvent.class);
        } catch (Exception e) {
            log.warn("Failed to parse Python SSE chunk: {}", data, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "AI 流式响应解析失败");
        }
    }

    /**
     * 从 Python Agent 创建响应中提取 data.id。
     * Python 返回 code != 200 时抛携带其 code 的 BizException。
     */
    public String extractPythonAgentId(String json) {
        JsonNode root = readTree(json);
        int code = root.path("code").asInt(200);
        if (code != 200) {
            throw new BizException(code, root.path("message").asText("Python create agent failed"));
        }
        String id = root.path("data").path("id").asText(null);
        if (id == null || id.isEmpty()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "Python 未返回 agent id");
        }
        return id;
    }

    /**
     * 校验 Python 通用 Result 响应是否成功（code == 200），否则抛携带其 code 的 BizException。
     */
    public void verifySuccess(String json) {
        JsonNode root = readTree(json);
        int code = root.path("code").asInt(200);
        if (code != 200) {
            throw new BizException(code, root.path("message").asText("Python request failed"));
        }
    }

    /**
     * 构造 Python AgentCreateRequest 请求体（snake_case 键，匹配 Python pydantic 模型）。
     */
    public Map<String, Object> buildCreateBody(AgentDO agent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", agent.getName());
        // description / system_prompt 在 Python 侧是 Optional（可接受 null），null 时省略更干净
        if (agent.getDescription() != null) body.put("description", agent.getDescription());
        if (agent.getSystemPrompt() != null) body.put("system_prompt", agent.getSystemPrompt());

        Map<String, Object> llm = new LinkedHashMap<>();
        // provider 是 Python 必填项（无默认），但 llm.py 并未实际使用它（OpenAI 兼容统一走 ChatOpenAI），
        // 故 Java 无值时给默认 "openai"，避免老前端不传 provider 导致 Python 422
        llm.put("provider", agent.getProvider() != null ? agent.getProvider() : "openai");
        llm.put("model", agent.getModel());
        llm.put("base_url", agent.getApiUrl());
        llm.put("api_key", agent.getApiKey());
        // temperature/max_tokens/top_p 在 Python 侧有默认值，仅当 Java 有值时发送，否则省略让 Python 补默认
        if (agent.getTemperature() != null) llm.put("temperature", agent.getTemperature());
        if (agent.getMaxTokens() != null) llm.put("max_tokens", agent.getMaxTokens());
        if (agent.getTopP() != null) llm.put("top_p", agent.getTopP());
        body.put("llm_config", llm);

        // memory_config 有默认工厂，仅当有值时发送；否则省略让 Python 用默认（conversation_window / 8000）
        if (agent.getMemoryType() != null || agent.getMemoryMaxTokens() != null) {
            Map<String, Object> mem = new LinkedHashMap<>();
            if (agent.getMemoryType() != null) mem.put("type", agent.getMemoryType());
            if (agent.getMemoryMaxTokens() != null) mem.put("max_tokens", agent.getMemoryMaxTokens());
            body.put("memory_config", mem);
        }

        // capabilities / knowledge_base_ids / tool_ids 有默认值，null 时省略
        if (agent.getCapabilities() != null) body.put("capabilities", agent.getCapabilities());
        if (agent.getKnowledgeBaseIds() != null) body.put("knowledge_base_ids", agent.getKnowledgeBaseIds());
        if (agent.getToolIds() != null) body.put("tool_ids", agent.getToolIds());
        return body;
    }

    /**
     * 从 Python 通用 Result 响应中提取 data 字段并反序列化为指定类型。
     * code != 200 时抛携带其 code 的 BizException。
     */
    public <T> T extractData(String json, Class<T> type) {
        JsonNode root = readTree(json);
        int code = root.path("code").asInt(200);
        if (code != 200) {
            throw new BizException(code, root.path("message").asText("Python request failed"));
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(data, type);
        } catch (Exception e) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "AI 引擎响应解析失败");
        }
    }

    /**
     * 从 Python 通用 Result 响应中提取 data 数组并反序列化为指定类型的列表。
     */
    public <T> java.util.List<T> extractDataList(String json, Class<T> type) {
        JsonNode root = readTree(json);
        int code = root.path("code").asInt(200);
        if (code != 200) {
            throw new BizException(code, root.path("message").asText("Python request failed"));
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull() || !data.isArray()) {
            return java.util.List.of();
        }
        try {
            java.util.List<T> result = new java.util.ArrayList<>();
            for (JsonNode node : data) {
                result.add(objectMapper.treeToValue(node, type));
            }
            return result;
        } catch (Exception e) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "AI 引擎响应解析失败");
        }
    }

    // ==================== 知识库 ====================

    public java.util.List<KnowledgeBaseVO> listKnowledgeBases() {
        return extractDataList(getJson("/api/v1/knowledge-bases"), KnowledgeBaseVO.class);
    }

    public KnowledgeBaseVO getKnowledgeBase(String kbId) {
        return extractData(getJson("/api/v1/knowledge-bases/" + kbId), KnowledgeBaseVO.class);
    }

    public KnowledgeBaseVO createKnowledgeBase(KnowledgeBaseRequest req) {
        String json = pythonWebClient.post()
                .uri("/api/v1/knowledge-bases")
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(req)
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
        return extractData(json, KnowledgeBaseVO.class);
    }

    public KnowledgeBaseVO updateKnowledgeBase(String kbId, KnowledgeBaseRequest req) {
        String json = pythonWebClient.put()
                .uri("/api/v1/knowledge-bases/{kbId}", kbId)
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(req)
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
        return extractData(json, KnowledgeBaseVO.class);
    }

    public void deleteKnowledgeBase(String kbId) {
        verifySuccess(getJsonDelete("/api/v1/knowledge-bases/" + kbId));
    }

    // ==================== 文档 ====================

    public java.util.List<DocumentVO> listDocuments(String kbId) {
        return extractDataList(getJson("/api/v1/knowledge-bases/" + kbId + "/documents"), DocumentVO.class);
    }

    public DocumentVO getDocument(String kbId, String docId) {
        return extractData(getJson("/api/v1/knowledge-bases/" + kbId + "/documents/" + docId), DocumentVO.class);
    }

    /**
     * 上传文档（multipart 转发）。Python 异步处理，返回 status=processing 的 DocumentVO。
     * file.getResource() 返回 MultipartFileResource，自带原始文件名，直接作为 multipart part。
     */
    public DocumentVO uploadDocument(String kbId, MultipartFile file) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getResource());
        String json = pythonWebClient.post()
                .uri("/api/v1/knowledge-bases/{kbId}/documents", kbId)
                .header("X-API-Key", props.getApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
        return extractData(json, DocumentVO.class);
    }

    public void deleteDocument(String kbId, String docId) {
        verifySuccess(getJsonDelete("/api/v1/knowledge-bases/" + kbId + "/documents/" + docId));
    }

    // ==================== 工具 ====================

    public java.util.List<ToolVO> listTools() {
        return extractDataList(getJson("/api/v1/tools"), ToolVO.class);
    }

    public ToolVO getTool(String toolId) {
        return extractData(getJson("/api/v1/tools/" + toolId), ToolVO.class);
    }

    public ToolVO createTool(ToolCreateRequest req) {
        String json = pythonWebClient.post()
                .uri("/api/v1/tools")
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(req)
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
        return extractData(json, ToolVO.class);
    }

    public void deleteTool(String toolId) {
        verifySuccess(getJsonDelete("/api/v1/tools/" + toolId));
    }

    // ==================== 内部 GET/DELETE 辅助 ====================

    private String getJson(String path) {
        return pythonWebClient.get()
                .uri(path)
                .header("X-API-Key", props.getApiKey())
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
    }

    private String getJsonDelete(String path) {
        return pythonWebClient.delete()
                .uri(path)
                .header("X-API-Key", props.getApiKey())
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "AI 引擎响应解析失败");
        }
    }
}
