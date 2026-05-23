package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话实体，映射 conversation 表。记录用户与 Agent 之间的一次对话会话。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation")
public class ConversationDO extends BaseEntity {

    /** 发起会话的用户 ID，关联 sys_user 表 */
    private Long userId;

    /** 对话的 Agent ID，关联 agent 表 */
    private Long agentId;

    /** 会话标题，通常由第一条消息自动生成或用户自定义 */
    private String title;
}
