package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * generate 成功响应（←Python）。
 * v1.1：generate 不再自动落库，故响应只含草稿（systemPrompt + moderation + estimate），
 * 不带 templateId——落库统一由 create 端点完成。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromptGenerateResponseVO {
    /** 生成的系统提示词正文 */
    @JsonAlias("system_prompt")
    private String systemPrompt;
    /** 生成模式 */
    private PromptMode mode;
    /** moderation 裁决（成功时 passed=true） */
    private ModerationVerdictVO moderation;
    /** 消耗估算 */
    private CostEstimateVO estimate;
}
