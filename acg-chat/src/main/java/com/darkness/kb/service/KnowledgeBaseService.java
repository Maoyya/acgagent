package com.darkness.kb.service;

import com.darkness.common.model.KnowledgeBaseRequest;
import com.darkness.common.model.KnowledgeBaseVO;

import java.util.List;

/**
 * 知识库代理服务：将知识库管理请求转发到 Python AI 引擎。
 */
public interface KnowledgeBaseService {

    /** 列出所有知识库。 */
    List<KnowledgeBaseVO> list();

    /** 获取知识库详情。 */
    KnowledgeBaseVO get(String kbId);

    /** 创建知识库。 */
    KnowledgeBaseVO create(KnowledgeBaseRequest request);

    /** 更新知识库。 */
    KnowledgeBaseVO update(String kbId, KnowledgeBaseRequest request);

    /** 删除知识库。 */
    void delete(String kbId);
}
