package com.darkness.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.agent.client.AgentClient;
import com.darkness.agent.entity.AgentDO;
import com.darkness.agent.entity.ConversationDO;
import com.darkness.agent.entity.MessageDO;
import com.darkness.agent.mapper.AgentMapper;
import com.darkness.agent.mapper.ConversationMapper;
import com.darkness.agent.mapper.MessageMapper;
import com.darkness.agent.model.ConversationVO;
import com.darkness.agent.model.MessageVO;
import com.darkness.agent.service.ChatService;
import com.darkness.common.exception.BizException;
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

/**
 * 对话服务实现类，管理会话的创建/删除、消息查询，以及 SSE 流式消息发送。
 * <p>
 * SSE 流式发送通过 AgentClient 调用外部 LLM API，在独立线程池中异步执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AgentMapper agentMapper;
    private final AgentClient agentClient;

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
        ConversationDO conv = conversationMapper.selectById(conversationId);
        if (conv == null) throw new BizException(404, "Conversation not found");
        if (!conv.getUserId().equals(userId)) throw new BizException(403, "Forbidden");

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
        ConversationDO conv = conversationMapper.selectById(conversationId);
        if (conv == null) throw new BizException(404, "Conversation not found");
        if (!conv.getUserId().equals(userId)) throw new BizException(403, "Forbidden");
        conversationMapper.deleteById(conversationId);
    }

    /**
     * 通过 SSE 流式发送用户消息给 AI Agent。
     * <p>
     * 流程：
     * 1. 校验会话归属（防止越权）和 Agent 存在性；
     * 2. 持久化用户消息到 message 表；
     * 3. 查询会话完整历史作为 LLM 上下文；
     * 4. 创建 SseEmitter（超时时间由 chat.sse-timeout 配置，默认 5 分钟），
     *    在异步线程池中调用 AgentClient.stream() 逐 token 推送；
     * 5. 流结束后持久化助手回复并完成 SseEmitter。
     *
     * @param userId         当前登录用户 ID
     * @param conversationId 会话 ID
     * @param content        用户输入的消息内容
     * @return SseEmitter 实例，前端通过 EventSource 接收流式数据
     */
    @Override
    public SseEmitter sendMessage(Long userId, Long conversationId, String content) {
        // 校验会话归属
        ConversationDO conv = conversationMapper.selectById(conversationId);
        if (conv == null) throw new BizException(404, "Conversation not found");
        if (!conv.getUserId().equals(userId)) throw new BizException(403, "Forbidden");

        AgentDO agent = agentMapper.selectById(conv.getAgentId());
        if (agent == null) throw new BizException(404, "Agent not found");

        // 持久化用户消息
        MessageDO userMsg = new MessageDO();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(content);
        messageMapper.insert(userMsg);

        // 查询当前会话的完整消息历史作为 LLM 上下文
        List<MessageDO> history = messageMapper.selectList(
                new LambdaQueryWrapper<MessageDO>()
                        .eq(MessageDO::getConversationId, conversationId)
                        .orderByAsc(MessageDO::getCreatedAt));

        SseEmitter emitter = new SseEmitter(sseTimeout);

        executor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            try {
                agentClient.stream(agent, history, content)
                        .doOnNext(chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                                fullResponse.append(chunk);
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(() -> {
                            // 持久化助手回复
                            MessageDO assistantMsg = new MessageDO();
                            assistantMsg.setConversationId(conversationId);
                            assistantMsg.setRole("assistant");
                            assistantMsg.setContent(fullResponse.toString());
                            messageMapper.insert(assistantMsg);
                            emitter.complete();
                        })
                        .doOnError(emitter::completeWithError)
                        .subscribe();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
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
