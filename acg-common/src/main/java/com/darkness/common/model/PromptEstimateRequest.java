package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/** 独立消耗估算请求（Java→Python estimate）。 */
@Data
public class PromptEstimateRequest {
    /** 待估算的系统提示词正文 */
    @NotBlank(message = "systemPrompt 不能为空")
    private String systemPrompt;
    /** 用户 hints，其 token 计入估算 */
    private List<String> userHints;
}
