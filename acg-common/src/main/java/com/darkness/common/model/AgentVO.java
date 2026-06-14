package com.darkness.common.model;

import com.darkness.common.constant.AgentConstants;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.enums.AgentCategory;
import com.darkness.common.enums.CommonStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 视图对象，用于 Controller 层与前端之间的数据传输，对敏感字段做脱敏处理。
 * <p>
 * 既承载 Java 侧展示字段，也承载同步到 Python 引擎所需的丰富配置。apiKey 返回前端时固定脱敏为 "******"。
 */
@Data
public class AgentVO {

    /** Agent 主键 */
    private Long id;

    /** 智能体名称，用于前端展示 */
    @NotBlank(message = "智能体名称不能为空")
    @Size(max = 128, message = "智能体名称长度不能超过128个字符")
    private String name;

    /** 智能体功能描述 */
    @Size(max = 512, message = "描述长度不能超过512个字符")
    private String description;

    /** 头像图片 URL */
    @Size(max = 512, message = "头像URL长度不能超过512个字符")
    private String avatar;

    /** 外部 LLM API 的完整地址 */
    @NotBlank(message = "API地址不能为空")
    @Size(max = 512, message = "API地址长度不能超过512个字符")
    private String apiUrl;

    /** API 密钥，前端展示时已脱敏为 "******"，更新时若仍为 "******" 则保留原值不变 */
    @NotBlank(message = "API密钥不能为空")
    @Size(max = 512, message = "API密钥长度不能超过512个字符")
    private String apiKey;

    /** 模型标识 */
    @Size(max = 128, message = "模型标识长度不能超过128个字符")
    private String model;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;

    /** 额外配置参数（已废弃，保留向前兼容） */
    private String configJson;

    /** Agent 分类：CHAT-对话，VIDEO-视频，IMAGE-图像 */
    private AgentCategory category;

    /** 系统提示词，同步到 Python system_prompt */
    private String systemPrompt;

    /** LLM 厂商：doubao/qwen/deepseek */
    private String provider;

    /** 生成温度 */
    private BigDecimal temperature;

    /** 最大输出 token 数 */
    private Integer maxTokens;

    /** Top-P 采样 */
    private BigDecimal topP;

    /** 记忆类型：conversation_window/summary/none */
    private String memoryType;

    /** 上下文窗口 token 数 */
    private Integer memoryMaxTokens;

    /** 能力标签数组 */
    private List<String> capabilities;

    /** 关联知识库 id 列表（Python 侧 string id） */
    private List<String> knowledgeBaseIds;

    /** 关联工具 id 列表（Python 侧 string id） */
    private List<String> toolIds;

    /** 同步锚点：Python 侧 agent 的 string id，未同步时为 null */
    private String pythonAgentId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 将实体转换为 VO，同时对 apiKey 进行脱敏处理。
     *
     * @param entity Agent 实体
     * @return 脱敏后的 AgentVO，若 entity 为 null 则返回 null
     */
    public static AgentVO from(AgentDO entity) {
        if (entity == null) return null;
        AgentVO vo = new AgentVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setAvatar(entity.getAvatar());
        vo.setApiUrl(entity.getApiUrl());
        vo.setApiKey(entity.getApiKey() != null ? AgentConstants.API_KEY_MASK : null); // 脱敏：不将真实 API Key 返回前端
        vo.setModel(entity.getModel());
        vo.setStatus(entity.getStatus());
        vo.setConfigJson(entity.getConfigJson());
        vo.setCategory(entity.getCategory());
        vo.setSystemPrompt(entity.getSystemPrompt());
        vo.setProvider(entity.getProvider());
        vo.setTemperature(entity.getTemperature());
        vo.setMaxTokens(entity.getMaxTokens());
        vo.setTopP(entity.getTopP());
        vo.setMemoryType(entity.getMemoryType());
        vo.setMemoryMaxTokens(entity.getMemoryMaxTokens());
        vo.setCapabilities(entity.getCapabilities());
        vo.setKnowledgeBaseIds(entity.getKnowledgeBaseIds());
        vo.setToolIds(entity.getToolIds());
        vo.setPythonAgentId(entity.getPythonAgentId());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 将 VO 转换为实体，供新增或更新操作使用。
     * 注意：此方法不做脱敏逆处理，apiKey 字段直接传入（更新时由 Service 层判断是否为掩码值）。
     *
     * @return AgentDO 实体
     */
    public AgentDO toEntity() {
        AgentDO entity = new AgentDO();
        entity.setId(this.id);
        entity.setName(this.name);
        entity.setDescription(this.description);
        entity.setAvatar(this.avatar);
        entity.setApiUrl(this.apiUrl);
        entity.setApiKey(this.apiKey);
        entity.setModel(this.model);
        entity.setStatus(this.status);
        entity.setConfigJson(this.configJson);
        entity.setCategory(this.category);
        entity.setSystemPrompt(this.systemPrompt);
        entity.setProvider(this.provider);
        entity.setTemperature(this.temperature);
        entity.setMaxTokens(this.maxTokens);
        entity.setTopP(this.topP);
        entity.setMemoryType(this.memoryType);
        entity.setMemoryMaxTokens(this.memoryMaxTokens);
        entity.setCapabilities(this.capabilities);
        entity.setKnowledgeBaseIds(this.knowledgeBaseIds);
        entity.setToolIds(this.toolIds);
        entity.setPythonAgentId(this.pythonAgentId);
        return entity;
    }
}
