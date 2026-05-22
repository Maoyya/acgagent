package com.darkness.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.entity.BaseEntity;
import com.darkness.common.enums.CommonStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 实体，映射 agent 表。存储 AI 智能体的配置信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent")
public class AgentDO extends BaseEntity {

    /** 智能体名称，用于前端展示 */
    private String name;

    /** 智能体功能描述 */
    private String description;

    /** 头像图片 URL */
    private String avatar;

    /** 外部 LLM API 的完整地址，如 https://api.openai.com/v1/chat/completions */
    private String apiUrl;

    /** 调用外部 API 的密钥，明文存储，生产环境需加密 */
    private String apiKey;

    /** 模型标识，如 gpt-4、claude-3-sonnet 等 */
    private String model;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;

    /** 额外配置参数，JSON 格式，如 temperature、max_tokens 等 */
    private String configJson;
}
