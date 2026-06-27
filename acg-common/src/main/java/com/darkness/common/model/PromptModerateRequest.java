package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/** 独立 moderation 请求（Java→Python moderate）。 */
@Data
public class PromptModerateRequest {
    /** 待校验的系统提示词正文 */
    @NotBlank(message = "systemPrompt 不能为空")
    private String systemPrompt;
    /** 校验模式 */
    private PromptMode mode = PromptMode.ACG;
    /** 能力标签，用于拼接能力边界 */
    private List<String> targetCapabilities;
}
