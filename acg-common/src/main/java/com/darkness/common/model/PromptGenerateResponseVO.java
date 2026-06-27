package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** generate 成功响应（←Python，Java 落库后追加 templateId）。 */
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
    /** Java 落库后追加的模板 id；Python 响应不带。前端据此知道存了哪条模板 */
    private Long templateId;
}
