package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.darkness.common.enums.AgentCategory;
import com.darkness.common.enums.CommonStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agent 实体，映射 agent 表。存储 AI 智能体的配置信息。
 * <p>
 * 既包含 Java 侧的展示/分类字段，也包含同步到 Python 引擎所需的丰富配置
 * （system_prompt、provider、temperature、capabilities、knowledge_base_ids、tool_ids 等），
 * 以及同步锚点 python_agent_id。
 * capabilities/knowledge_base_ids/tool_ids 为 JSON 列，通过 JacksonTypeHandler 映射为 List<String>。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent", autoResultMap = true)
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

    /** 模型标识，如 gpt-4、doubao-pro 等 */
    private String model;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;

    /** 额外配置参数（已废弃，改用下方独立列），保留以向前兼容 */
    private String configJson;

    /** Agent 分类：CHAT-对话，VIDEO-视频，IMAGE-图像 */
    private AgentCategory category;

    /** 系统提示词，同步到 Python 作为 Agent 的 system_prompt */
    private String systemPrompt;

    /** LLM 厂商标识：doubao/qwen/deepseek，同步到 Python llm_config.provider */
    private String provider;

    /** 生成温度，同步到 Python llm_config.temperature */
    private BigDecimal temperature;

    /** 最大输出 token 数，同步到 Python llm_config.max_tokens */
    private Integer maxTokens;

    /** Top-P 采样，同步到 Python llm_config.top_p */
    private BigDecimal topP;

    /** 记忆类型：conversation_window/summary/none，同步到 Python memory_config.type */
    private String memoryType;

    /** 上下文窗口 token 数，同步到 Python memory_config.max_tokens */
    private Integer memoryMaxTokens;

    /** 能力标签数组，如 ["chat","rag","tool_use"]，同步到 Python capabilities */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> capabilities;

    /** 关联知识库 id 列表（Python 侧 string id），同步到 Python knowledge_base_ids */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgeBaseIds;

    /** 关联工具 id 列表（Python 侧 string id），同步到 Python tool_ids */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> toolIds;

    /** 同步锚点：Python 侧 agent 的 string id；未同步时为 null，对话接口会拒绝 */
    private String pythonAgentId;
}
