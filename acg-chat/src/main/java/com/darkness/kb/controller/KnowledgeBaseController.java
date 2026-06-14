package com.darkness.kb.controller;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.model.KnowledgeBaseRequest;
import com.darkness.common.model.KnowledgeBaseVO;
import com.darkness.common.result.Result;
import com.darkness.kb.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理控制器，全部代理到 Python AI 引擎，仅管理员可访问。
 */
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 列出所有知识库。
     * GET /api/knowledge-bases（需认证，仅管理员）
     */
    @GetMapping
    @RequireRole("admin")
    public Result<List<KnowledgeBaseVO>> list() {
        return Result.success(knowledgeBaseService.list());
    }

    /**
     * 获取知识库详情。
     * GET /api/knowledge-bases/{id}（需认证，仅管理员）
     *
     * @param id 知识库 id
     */
    @GetMapping("/{id}")
    @RequireRole("admin")
    public Result<KnowledgeBaseVO> get(@PathVariable String id) {
        return Result.success(knowledgeBaseService.get(id));
    }

    /**
     * 创建知识库，name 必填。
     * POST /api/knowledge-bases（需认证，仅管理员）
     *
     * @param request 知识库信息
     */
    @PostMapping
    @RequireRole("admin")
    public Result<KnowledgeBaseVO> create(@RequestBody @Valid KnowledgeBaseRequest request) {
        return Result.success(knowledgeBaseService.create(request));
    }

    /**
     * 更新知识库。
     * PUT /api/knowledge-bases/{id}（需认证，仅管理员）
     *
     * @param id      知识库 id
     * @param request 知识库信息
     */
    @PutMapping("/{id}")
    @RequireRole("admin")
    public Result<KnowledgeBaseVO> update(@PathVariable String id,
                                          @RequestBody @Valid KnowledgeBaseRequest request) {
        return Result.success(knowledgeBaseService.update(id, request));
    }

    /**
     * 删除知识库。
     * DELETE /api/knowledge-bases/{id}（需认证，仅管理员）
     *
     * @param id 知识库 id
     */
    @DeleteMapping("/{id}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable String id) {
        knowledgeBaseService.delete(id);
        return Result.success();
    }
}
