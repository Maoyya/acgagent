package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * 知识库视图对象，代理 Python acgagent-ai 的 KnowledgeBase schema。
 * <p>
 * camelCase 字段 + @JsonAlias 对齐 Python 的 snake_case；前端序列化仍为 camelCase。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeBaseVO {

    /** 知识库 id（Python 侧 string） */
    private String id;

    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;

    /** Embedding 配置：provider/model/baseUrl/apiKey */
    @JsonAlias("embedding_config")
    private Map<String, Object> embeddingConfig;

    /** 分块配置：chunkSize/chunkOverlap/separators */
    @JsonAlias("chunk_config")
    private Map<String, Object> chunkConfig;

    /** 文档数量 */
    @JsonAlias("document_count")
    private Integer documentCount;

    /** 分块数量 */
    @JsonAlias("chunk_count")
    private Integer chunkCount;
}
