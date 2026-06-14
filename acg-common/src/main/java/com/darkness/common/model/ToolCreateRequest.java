package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 工具创建请求，透传给 Python。name/description 必填。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCreateRequest {

    /** 工具显示名 */
    @NotBlank(message = "工具名称不能为空")
    @Size(max = 128, message = "工具名称长度不能超过128个字符")
    private String name;

    /** 工具描述 */
    @NotBlank(message = "工具描述不能为空")
    private String description;

    /** 类型，默认 api */
    private String type = "api";

    /** 自定义 API 工具的 HTTP 调用配置（透传 Python config） */
    private Map<String, Object> config;

    /** 参数 schema（透传 Python parameters） */
    private Map<String, Object> parameters;
}
