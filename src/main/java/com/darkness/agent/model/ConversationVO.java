package com.darkness.agent.model;

import com.darkness.agent.entity.ConversationDO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话视图对象，用于 Controller 层与前端之间的数据传输。
 */
@Data
public class ConversationVO {

    /** 会话主键 */
    private Long id;

    /** 发起会话的用户 ID */
    private Long userId;

    /** 对话的 Agent ID */
    private Long agentId;

    /** 会话标题 */
    private String title;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 将会话实体转换为 VO。
     * 复制 entity 的所有字段到 VO，用于向前端隐藏持久层细节。
     *
     * @param entity 会话实体
     * @return ConversationVO，若 entity 为 null 则返回 null
     */
    public static ConversationVO from(ConversationDO entity) {
        if (entity == null) return null;
        ConversationVO vo = new ConversationVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setAgentId(entity.getAgentId());
        vo.setTitle(entity.getTitle());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
