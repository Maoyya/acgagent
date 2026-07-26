package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 角色生成请求（→Python acgagent-ai 角色生成接口）。
 * <p>字段使用 camelCase；构造 Python 请求体时由 PythonAiClient 转 snake_case。
 */
@Data
public class CharactersRequest {

    /** 剧情正文（Markdown，由 plot 生成得到），角色生成的输入；非空 */
    @NotBlank(message = "剧情正文不能为空")
    private String plot;
}
