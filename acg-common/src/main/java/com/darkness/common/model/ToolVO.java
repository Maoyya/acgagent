package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * 工具视图对象，代理 Python ToolVO。type: api(自定义) / builtin(内置 calculator/web_search/knowledge_search)。
 * 内置工具不可删除。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolVO {

    /** 工具 id（Python 侧 string；内置工具为 calculator/web_search/knowledge_search） */
    private String id;

    /** 工具显示名 */
    private String name;

    /** 工具描述（喂给 LLM） */
    private String description;

    /** 类型：api / builtin */
    private String type;

    /** 自定义 API 工具的 HTTP 调用配置（url/method/headers/query_params/body_template） */
    private Map<String, Object> config;

    /** 参数 schema（type/properties/required） */
    private Map<String, Object> parameters;
}
