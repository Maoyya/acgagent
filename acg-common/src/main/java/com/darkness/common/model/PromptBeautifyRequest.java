package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 润色（beautify）请求。v1.1 新增。
 * <p>
 * 用户从 generate 拿到草稿后，可选指定一个 Agent，借用该 Agent 的 LLM 配置
 * 调 Python beautify 润色草稿。后端据此 req 取 Agent 原始 DO（含真实 apiKey）
 * 构造 llm_config 传给 Python。不校验、不落库（合规性在后续 create/update 的保存闸门统一校验）。
 */
@Data
public class PromptBeautifyRequest {

    /** 待润色的草稿 system_prompt（通常来自 generate） */
    @NotBlank(message = "systemPrompt 不能为空")
    private String systemPrompt;

    /** 所选 Agent id（用其 LLM 润色） */
    @NotNull(message = "agentId 不能为空")
    private Long agentId;

    /** 生成模式，默认 ACG */
    private PromptMode mode = PromptMode.ACG;
}
