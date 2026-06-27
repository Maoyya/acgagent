package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/** moderation 裁决结果（←Python）。blocked 时作为 403 的 data 返回前端。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModerationVerdictVO {
    /** 是否通过 moderation */
    private Boolean passed;
    /** 违反的规则文案列表 */
    @JsonAlias("violated_rules")
    private List<String> violatedRules;
    /** 违规原因说明 */
    private List<String> reasons;
    /** 置信度 0.0-1.0（二期多裁判投票钩子） */
    private Double confidence;
    /** 校验时使用的模式 */
    private PromptMode mode;
}
