package com.darkness.agent.model;

import com.darkness.agent.entity.MessageDO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息视图对象，用于 Controller 层与前端之间的数据传输。
 */
@Data
public class MessageVO {

    /** 消息主键 */
    private Long id;

    /** 所属会话 ID */
    private Long conversationId;

    /** 角色：user 或 assistant */
    private String role;

    /** 消息正文内容 */
    private String content;

    /** 消息消耗的 token 数 */
    private Integer tokens;

    /** 消息发送时间 */
    private LocalDateTime createdAt;

    /**
     * 将消息实体转换为 VO。
     * 复制 entity 的所有字段到 VO，用于向前端隐藏持久层细节。
     *
     * @param entity 消息实体
     * @return MessageVO，若 entity 为 null 则返回 null
     */
    public static MessageVO from(MessageDO entity) {
        if (entity == null) return null;
        MessageVO vo = new MessageVO();
        vo.setId(entity.getId());
        vo.setConversationId(entity.getConversationId());
        vo.setRole(entity.getRole());
        vo.setContent(entity.getContent());
        vo.setTokens(entity.getTokens());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
