package com.darkness.kb.controller;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.model.DocumentVO;
import com.darkness.common.result.Result;
import com.darkness.kb.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理控制器（隶属知识库），全部代理到 Python，仅管理员可访问。
 */
@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文档（multipart）。Python 异步处理，返回 status=processing 的元数据，前端可轮询状态。
     * POST /api/knowledge-bases/{kbId}/documents（需认证，仅管理员）
     *
     * @param kbId 所属知识库 id
     * @param file 上传的文件
     */
    @PostMapping
    @RequireRole("admin")
    public Result<DocumentVO> upload(@PathVariable String kbId,
                                     @RequestParam("file") MultipartFile file) {
        return Result.success(documentService.upload(kbId, file));
    }

    /**
     * 列出知识库下的文档。
     * GET /api/knowledge-bases/{kbId}/documents（需认证，仅管理员）
     */
    @GetMapping
    @RequireRole("admin")
    public Result<List<DocumentVO>> list(@PathVariable String kbId) {
        return Result.success(documentService.list(kbId));
    }

    /**
     * 获取文档详情。
     * GET /api/knowledge-bases/{kbId}/documents/{docId}（需认证，仅管理员）
     */
    @GetMapping("/{docId}")
    @RequireRole("admin")
    public Result<DocumentVO> get(@PathVariable String kbId, @PathVariable String docId) {
        return Result.success(documentService.get(kbId, docId));
    }

    /**
     * 删除文档。
     * DELETE /api/knowledge-bases/{kbId}/documents/{docId}（需认证，仅管理员）
     */
    @DeleteMapping("/{docId}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable String kbId, @PathVariable String docId) {
        documentService.delete(kbId, docId);
        return Result.success();
    }
}
