package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PromptMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 系统提示词模板实体，映射 prompt_template 表。
 * <p>双维度：user_id=NULL 为公共模板(管理员开放)，非 NULL 为该用户的私有模板。
 * target_capabilities 为 JSON 列，经 JacksonTypeHandler 映射为 List<String>。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "prompt_template", autoResultMap = true)
public class PromptTemplateDO extends BaseEntity {

    /** 归属用户 ID；NULL=公共模板，非 NULL=该用户的私有模板 */
    private Long userId;

    /** 模板名称，生成时自动取首条 hint 截断，可后续修改 */
    private String name;

    /** 系统提示词正文 */
    private String systemPrompt;

    /** 生成模式：ACG/COMPLIANT */
    private PromptMode mode;

    /** 能力标签数组，用于"不超能力"约束，如 ["chat","rag"] */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> targetCapabilities;

    /** 生成时估算的 prompt token 数(缓存)，未估算为 null */
    private Integer estPromptTokens;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;
}
