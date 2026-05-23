package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.enums.MessageRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息实体，映射 message 表。存储会话中的单条聊天消息。
 */
@Data
@TableName("message")
public class MessageDO {

    /** 消息主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID，关联 conversation 表 */
    private Long conversationId;

    /** 消息角色：USER-用户发送，ASSISTANT-AI 回复 */
    private MessageRole role;

    /** 消息正文内容 */
    private String content;

    /** 消息消耗的 token 数，用于用量统计 */
    private Integer tokens;

    /** 消息发送时间 */
    private LocalDateTime createdAt;
}
