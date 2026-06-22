package com.darkness.common.model;

import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PromptMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 提示词模板视图对象，对外暴露给前端。isPublic 为派生字段（user_id=NULL 时为 true）。 */
@Data
public class PromptTemplateVO {

    /** 模板主键 */
    private Long id;

    /** 归属用户 ID；null=公共模板 */
    private Long userId;

    /** 是否公共模板（user_id=NULL 时 true），派生展示字段 */
    private Boolean isPublic;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称长度不能超过128")
    private String name;

    /** 系统提示词正文 */
    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    /** 生成模式 */
    private PromptMode mode;

    /** 能力标签数组 */
    private List<String> targetCapabilities;

    /** 估算 prompt token（缓存） */
    private Integer estPromptTokens;

    /** 状态 */
    private CommonStatus status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** DO→VO，并派生 isPublic。entity 为 null 返回 null。 */
    public static PromptTemplateVO from(PromptTemplateDO entity) {
        if (entity == null) return null;
        PromptTemplateVO vo = new PromptTemplateVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setIsPublic(entity.getUserId() == null);
        vo.setName(entity.getName());
        vo.setSystemPrompt(entity.getSystemPrompt());
        vo.setMode(entity.getMode());
        vo.setTargetCapabilities(entity.getTargetCapabilities());
        vo.setEstPromptTokens(entity.getEstPromptTokens());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
