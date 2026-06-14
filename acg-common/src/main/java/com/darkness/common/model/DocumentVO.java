package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 文档视图对象，代理 Python DocumentVO。文档上传后异步处理，status 流转 processing→completed/failed。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentVO {

    /** 文档 id（Python 侧 string） */
    private String id;

    /** 所属知识库 id */
    @JsonAlias("knowledge_base_id")
    private String knowledgeBaseId;

    /** 原始文件名 */
    @JsonAlias("file_name")
    private String fileName;

    /** 文件大小（字节） */
    @JsonAlias("file_size")
    private Long fileSize;

    /** 分块数（处理完成后填充） */
    @JsonAlias("chunk_count")
    private Integer chunkCount;

    /** 状态：processing / completed / failed */
    private String status;

    /** 失败时的错误信息 */
    @JsonAlias("error_message")
    private String errorMessage;
}
