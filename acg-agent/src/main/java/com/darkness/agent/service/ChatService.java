package com.darkness.agent.service;

import com.darkness.agent.model.ConversationVO;
import com.darkness.agent.model.MessageVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话服务接口，定义会话管理、消息查询和 SSE 流式发送操作。
 */
public interface ChatService {

    /**
     * 创建新会话。
     * 建立用户与指定 Agent 之间的会话，持久化到 conversation 表并返回 VO。
     *
     * @param userId  当前用户 ID
     * @param agentId 关联的 Agent ID
     * @param title   会话标题
     * @return 创建后的会话视图对象
     */
    ConversationVO createConversation(Long userId, Long agentId, String title);

    /**
     * 查询指定用户的会话列表，按创建时间倒序排列
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    List<ConversationVO> listConversations(Long userId);

    /**
     * 查询指定会话的消息列表。
     * 校验会话是否属于当前用户，不属于时抛出 403 BizException，通过后返回该会话全部消息。
     *
     * @param conversationId 会话 ID
     * @param userId         当前用户 ID，用于归属校验
     * @return 消息列表
     */
    List<MessageVO> getMessages(Long conversationId, Long userId);

    /**
     * 删除指定会话。
     * 校验会话归属后将 conversation 及其关联消息逻辑删除（deleted 置 1），
     * 不属于当前用户时抛出 403 BizException。
     *
     * @param conversationId 会话 ID
     * @param userId         当前用户 ID
     */
    void deleteConversation(Long conversationId, Long userId);

    /**
     * 通过 SSE 流式发送消息给 Agent，并将用户消息和助手回复持久化。
     * 校验会话归属后，先将用户消息持久化到 message 表，再查询该会话完整消息历史作为
     * LLM 上下文，通过 AgentClient 以 SSE 流式调用外部 LLM API，流完成后将助手回复
     * 持久化到 message 表并统计 token 消耗。
     *
     * @param userId         当前用户 ID
     * @param conversationId 会话 ID
     * @param content        用户发送的消息内容
     * @return SseEmitter 流式响应，前端通过 EventSource 接收逐 token 内容
     */
    SseEmitter sendMessage(Long userId, Long conversationId, String content);
}
