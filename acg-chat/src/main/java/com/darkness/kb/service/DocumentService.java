package com.darkness.kb.service;

import com.darkness.common.model.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档代理服务：转发到 Python AI 引擎，包含 multipart 文档上传。
 */
public interface DocumentService {

    /** 列出知识库下的文档。 */
    List<DocumentVO> list(String kbId);

    /** 获取文档详情。 */
    DocumentVO get(String kbId, String docId);

    /** 上传文档（multipart），Python 异步处理，返回 status=processing 的文档元数据。 */
    DocumentVO upload(String kbId, MultipartFile file);

    /** 删除文档。 */
    void delete(String kbId, String docId);
}
