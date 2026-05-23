package com.darkness.agent.controller;

import com.darkness.common.model.ConversationVO;
import com.darkness.common.model.CreateConversationRequest;
import com.darkness.common.model.MessageVO;
import com.darkness.common.model.SendMessageRequest;
import com.darkness.agent.service.ChatService;
import com.darkness.common.result.Result;
import com.darkness.common.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话控制器，管理会话的创建/删除/查询，以及通过 SSE 流式发送消息给 Agent。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 创建新会话，将用户与指定 Agent 关联。
     * POST /api/chat/conversations（需认证）
     *
     * @param request 请求体，包含 agentId（必填）和 title（可选）
     * @return 创建后的会话视图对象
     */
    @PostMapping("/conversations")
    public Result<ConversationVO> createConversation(
            @RequestBody @Valid CreateConversationRequest request) {
        String title = request.getTitle() != null ? request.getTitle() : "New Conversation";
        // Gateway 转发后通过 Header 获取用户 ID
        Long userId = UserContext.getUserId();
        return Result.success(chatService.createConversation(userId, request.getAgentId(), title));
    }

    /**
     * 查询当前用户的所有会话，按创建时间倒序排列。
     * GET /api/chat/conversations（需认证）
     *
     * @return 会话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations() {
        Long userId = UserContext.getUserId();
        return Result.success(chatService.listConversations(userId));
    }

    /**
     * 查询指定会话的消息列表。
     * GET /api/chat/conversations/{id}/messages（需认证）
     * 已在 Service 层校验会话归属，防止越权访问他人消息。
     *
     * @param id 会话主键
     * @return 消息列表，按创建时间正序排列
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageVO>> getMessages(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.success(chatService.getMessages(id, userId));
    }

    /**
     * SSE 流式发送消息。
     * POST /api/chat/conversations/{id}/send（需认证）
     * 此端点返回 SseEmitter 而非 Result&lt;T&gt;，因为 SSE 需要保持长连接持续推送数据，无法用统一响应体包装。
     *
     * @param id      会话主键
     * @param request 请求体，包含 content（用户输入的消息内容）
     * @return SseEmitter 实例，前端通过 EventSource 接收流式数据
     */
    @PostMapping("/conversations/{id}/send")
    public SseEmitter sendMessage(
            @PathVariable Long id,
            @RequestBody @Valid SendMessageRequest request) {
        Long userId = UserContext.getUserId();
        return chatService.sendMessage(userId, id, request.getContent());
    }

    /**
     * 逻辑删除会话，需校验会话归属当前用户，非本人会话返回 403。
     * DELETE /api/chat/conversations/{id}（需认证）
     *
     * @param id 会话主键
     */
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        chatService.deleteConversation(id, userId);
        return Result.success();
    }
}
