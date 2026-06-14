package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 知识库创建/更新请求，透传给 Python。name 必填。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeBaseRequest {

    /** 知识库名称 */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称长度不能超过128个字符")
    private String name;

    /** 描述 */
    private String description;

    /** Embedding 配置（透传 Python embedding_config） */
    @JsonAlias("embedding_config")
    private Map<String, Object> embeddingConfig;

    /** 分块配置（透传 Python chunk_config） */
    @JsonAlias("chunk_config")
    private Map<String, Object> chunkConfig;
}
