package com.darkness.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.entity.ConversationDO;
import com.darkness.common.entity.MessageDO;
import com.darkness.common.mapper.AgentMapper;
import com.darkness.common.mapper.ConversationMapper;
import com.darkness.common.mapper.MessageMapper;
import com.darkness.common.model.ChatEvent;
import com.darkness.common.model.ConversationVO;
import com.darkness.common.model.MessageVO;
import com.darkness.agent.service.ChatService;
import com.darkness.common.enums.MessageRole;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.ServiceHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对话服务实现类，管理会话的创建/删除、消息查询，以及 SSE 流式消息发送。
 * <p>
 * SSE 流式发送通过 PythonAiClient 调用 Python AI 引擎，在独立线程池中异步执行，
 * 把 Python 返回的结构化 ChatEvent 透传给前端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AgentMapper agentMapper;
    private final PythonAiClient pythonAiClient;
    private final ObjectMapper objectMapper;

    /** SSE 连接超时时间（毫秒），默认 5 分钟 */
    @Value("${chat.sse-timeout:300000}")
    private long sseTimeout;

    /** 异步线程池，用于执行 SSE 流式请求 */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 创建新会话。将用户与指定 Agent 关联，插入 conversation 表后返回 VO。
     *
     * @param userId  当前登录用户 ID
     * @param agentId 关联的 Agent ID
     * @param title   会话标题，前端传入
     * @return 创建后的会话视图对象
     */
    @Override
    public ConversationVO createConversation(Long userId, Long agentId, String title) {
        ConversationDO conv = new ConversationDO();
        conv.setUserId(userId);
        conv.setAgentId(agentId);
        conv.setTitle(title);
        conversationMapper.insert(conv);
        return ConversationVO.from(conv);
    }

    /**
     * 查询指定用户的所有会话，按创建时间倒序排列。
     *
     * @param userId 当前登录用户 ID
     * @return 会话视图对象列表
     */
    @Override
    public List<ConversationVO> listConversations(Long userId) {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<ConversationDO>()
                        .eq(ConversationDO::getUserId, userId)
                        .orderByDesc(ConversationDO::getCreatedAt)
        ).stream().map(ConversationVO::from).toList();
    }

    /**
     * 查询指定会话的消息列表，按创建时间正序排列。
     * 先校验会话存在且归属当前用户（防止 IDOR 越权），校验失败抛出 BizException(404/403)。
     *
     * @param conversationId 会话 ID
     * @param userId         当前登录用户 ID，用于归属校验
     * @return 消息视图对象列表
     */
    @Override
    public List<MessageVO> getMessages(Long conversationId, Long userId) {
        // 校验会话归属，防止 IDOR 越权读取他人消息
        ConversationDO conv = ServiceHelper.findOrThrow(conversationMapper.selectById(conversationId), "Conversation", conversationId);
        if (!conv.getUserId().equals(userId)) throw new BizException(ResultCode.FORBIDDEN, "Forbidden");

        return messageMapper.selectList(
                new LambdaQueryWrapper<MessageDO>()
                        .eq(MessageDO::getConversationId, conversationId)
                        .orderByAsc(MessageDO::getCreatedAt)
        ).stream().map(MessageVO::from).toList();
    }

    /**
     * 删除指定会话。先校验会话存在且归属当前用户（防止越权删除），校验失败抛出 BizException(404/403)。
     *
     * @param conversationId 会话 ID
     * @param userId         当前登录用户 ID，用于归属校验
     */
    @Override
    public void deleteConversation(Long conversationId, Long userId) {
        ConversationDO conv = ServiceHelper.findOrThrow(conversationMapper.selectById(conversationId), "Conversation", conversationId);
        if (!conv.getUserId().equals(userId)) throw new BizException(ResultCode.FORBIDDEN, "Forbidden");
        conversationMapper.deleteById(conversationId);
    }

    /**
     * 通过 SSE 流式发送用户消息给 AI Agent（经 Python 引擎）。
     * <p>
     * 流程：
     * 1. 校验会话归属（防越权）与 Agent 存在；
     * 2. 校验 Agent 已同步到 Python（python_agent_id 非空）；
     * 3. 持久化用户消息到 message 表（前端历史 UI 用）；
     * 4. 创建 SseEmitter，异步调用 PythonAiClient.streamChat 透传结构化 ChatEvent；
     * 5. 累计 content 事件，收到 done 事件时持久化助手回复；
     * 6. 收到 error 事件不持久化残缺回复，流结束后以 completeWithError 收尾。
     * <p>
     * 会话上下文由 Python 按 conversation_id 自管（M1 双写），Java 不再查历史喂 LLM。
     *
     * @param userId         当前登录用户 ID
     * @param conversationId 会话 ID
     * @param content        用户输入的消息内容
     * @return SseEmitter 实例，前端通过 EventSource 接收流式数据
     */
    @Override
    public SseEmitter sendMessage(Long userId, Long conversationId, String content) {
        // 校验会话归属
        ConversationDO conv = ServiceHelper.findOrThrow(conversationMapper.selectById(conversationId), "Conversation", conversationId);
        if (!conv.getUserId().equals(userId)) throw new BizException(ResultCode.FORBIDDEN, "Forbidden");

        AgentDO agent = ServiceHelper.findOrThrow(agentMapper.selectById(conv.getAgentId()), "Agent", conv.getAgentId());
        // 校验 Agent 已同步到 Python
        if (agent.getPythonAgentId() == null || agent.getPythonAgentId().isBlank()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "Agent 未同步到 AI 引擎，请联系管理员重建");
        }

        // 持久化用户消息
        MessageDO userMsg = new MessageDO();
        userMsg.setConversationId(conversationId);
        userMsg.setRole(MessageRole.USER);
        userMsg.setContent(content);
        messageMapper.insert(userMsg);

        SseEmitter emitter = new SseEmitter(sseTimeout);
        executor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            AtomicBoolean hadError = new AtomicBoolean(false);
            pythonAiClient.streamChat(agent.getPythonAgentId(), conversationId, content, userId)
                    .doOnNext(event -> {
                        try {
                            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
                            switch (event.getType() == null ? "" : event.getType()) {
                                case "content" -> {
                                    if (event.getContent() != null) fullResponse.append(event.getContent());
                                }
                                case "done" -> persistAssistant(conversationId, fullResponse.toString());
                                case "error" -> hadError.set(true);
                                default -> { /* tool_call/tool_result/thinking 仅透传，不持久化 */ }
                            }
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(() -> {
                        if (hadError.get()) emitter.completeWithError(new RuntimeException("AI stream error"));
                        else emitter.complete();
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();
        });
        return emitter;
    }

    /** 持久化助手回复消息（仅在 done 事件时调用，避免持久化残缺回复）。 */
    private void persistAssistant(Long conversationId, String content) {
        MessageDO assistantMsg = new MessageDO();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole(MessageRole.ASSISTANT);
        assistantMsg.setContent(content);
        messageMapper.insert(assistantMsg);
    }

    /**
     * 应用关闭时优雅关闭 SSE 异步线程池。
     * 先调用 shutdown() 停止接受新任务，等待 10 秒让已提交任务完成，
     * 超时后调用 shutdownNow() 强制中断，最后恢复中断状态。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
