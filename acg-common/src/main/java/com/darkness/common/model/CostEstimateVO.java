package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 消耗估算（←Python）。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CostEstimateVO {
    /** 估算的 prompt token 数（system_prompt + user_hints） */
    @JsonAlias("prompt_tokens")
    private Integer promptTokens;
    /** 一期恒为 0；二期接 LLM 真实 usage */
    @JsonAlias("est_completion_tokens")
    private Integer estCompletionTokens;
    /** 估算所用模型名 */
    private String model;
}
