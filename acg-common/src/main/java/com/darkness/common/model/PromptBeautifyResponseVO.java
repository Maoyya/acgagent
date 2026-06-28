package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 润色（beautify）响应。v1.1 新增。
 * <p>
 * 仅承载润色后的 system_prompt（←Python）。不校验、不落库。
 * 加 @JsonIgnoreProperties 与其他 Python-facing VO 一致（容错未知字段）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromptBeautifyResponseVO {

    /** 润色后的 system_prompt（Python 返回 snake_case，@JsonAlias 兼容） */
    @JsonAlias("system_prompt")
    private String systemPrompt;
}
