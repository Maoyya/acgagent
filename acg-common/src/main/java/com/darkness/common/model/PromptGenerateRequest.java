package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import lombok.Data;
import java.util.List;

/** 系统提示词生成请求（Java→Python generate）。字段 camelCase，由 PythonAiClient 构造 snake_case body。 */
@Data
public class PromptGenerateRequest {
    /** 用户零散要求，如 "毒舌但专业的客服" */
    private List<String> userHints;
    /** 生成模式，默认 ACG */
    private PromptMode mode = PromptMode.ACG;
    /** 可选；Agent 能力标签，用于"不超能力"约束 */
    private List<String> targetCapabilities;
}
