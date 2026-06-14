package com.darkness.kb.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.model.DocumentVO;
import com.darkness.kb.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档代理服务实现，转发到 PythonAiClient。
 */
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final PythonAiClient pythonAiClient;

    @Override
    public List<DocumentVO> list(String kbId) {
        return pythonAiClient.listDocuments(kbId);
    }

    @Override
    public DocumentVO get(String kbId, String docId) {
        return pythonAiClient.getDocument(kbId, docId);
    }

    @Override
    public DocumentVO upload(String kbId, MultipartFile file) {
        return pythonAiClient.uploadDocument(kbId, file);
    }

    @Override
    public void delete(String kbId, String docId) {
        pythonAiClient.deleteDocument(kbId, docId);
    }
}
