package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 剧情生成请求（→Python acgagent-ai 剧情生成接口）。
 * <p>字段使用 camelCase；构造 Python 请求体时由 PythonAiClient 转 snake_case。
 */
@Data
public class PlotRequest {

    /** 故事梗概（用户输入），剧情生成的输入；非空 */
    @NotBlank(message = "故事梗概不能为空")
    private String story;
}
