package com.darkness.kb.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.model.KnowledgeBaseRequest;
import com.darkness.common.model.KnowledgeBaseVO;
import com.darkness.kb.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库代理服务实现，转发到 PythonAiClient。Python 是知识库存储的真相源，Java 不落库。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final PythonAiClient pythonAiClient;

    @Override
    public List<KnowledgeBaseVO> list() {
        return pythonAiClient.listKnowledgeBases();
    }

    @Override
    public KnowledgeBaseVO get(String kbId) {
        return pythonAiClient.getKnowledgeBase(kbId);
    }

    @Override
    public KnowledgeBaseVO create(KnowledgeBaseRequest request) {
        return pythonAiClient.createKnowledgeBase(request);
    }

    @Override
    public KnowledgeBaseVO update(String kbId, KnowledgeBaseRequest request) {
        return pythonAiClient.updateKnowledgeBase(kbId, request);
    }

    @Override
    public void delete(String kbId) {
        pythonAiClient.deleteKnowledgeBase(kbId);
    }
}
