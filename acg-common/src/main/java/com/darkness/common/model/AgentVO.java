package com.darkness.common.model;

import com.darkness.common.constant.AgentConstants;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.enums.CommonStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 视图对象，用于 Controller 层与前端之间的数据传输，对敏感字段做脱敏处理。
 */
@Data
public class AgentVO {

    /** Agent 主键 */
    private Long id;

    /** 智能体名称，用于前端展示 */
    private String name;

    /** 智能体功能描述 */
    private String description;

    /** 头像图片 URL */
    private String avatar;

    /** 外部 LLM API 的完整地址 */
    private String apiUrl;

    /** API 密钥，前端展示时已脱敏为 "******"，更新时若仍为 "******" 则保留原值不变 */
    private String apiKey;

    /** 模型标识，如 gpt-4、claude-3-sonnet 等 */
    private String model;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;

    /** 额外配置参数，JSON 格式 */
    private String configJson;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 将实体转换为 VO，同时对 apiKey 进行脱敏处理。
     * 将 AgentDO 的所有字段复制到 AgentVO，其中 apiKey 替换为固定掩码 "******"，
     * 避免真实密钥泄露到前端。
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
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 将 VO 转换为实体，供新增或更新操作使用。
     * 注意：此方法不做脱敏逆处理，apiKey 字段直接传入（更新时由 Service 层
     * 判断是否为脱敏值 "******"，若是则保留数据库原值）。
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
        return entity;
    }
}
