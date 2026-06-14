package com.darkness.tool.controller;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.model.ToolCreateRequest;
import com.darkness.common.model.ToolVO;
import com.darkness.common.result.Result;
import com.darkness.tool.service.ToolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具管理控制器，全部代理到 Python，仅管理员可访问。
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    /**
     * 列出所有工具（内置 + 自定义）。
     * GET /api/tools（需认证，仅管理员）
     */
    @GetMapping
    @RequireRole("admin")
    public Result<List<ToolVO>> list() {
        return Result.success(toolService.list());
    }

    /**
     * 获取工具详情。
     * GET /api/tools/{id}（需认证，仅管理员）
     *
     * @param id 工具 id
     */
    @GetMapping("/{id}")
    @RequireRole("admin")
    public Result<ToolVO> get(@PathVariable String id) {
        return Result.success(toolService.get(id));
    }

    /**
     * 创建自定义 API 工具，name/description 必填。
     * POST /api/tools（需认证，仅管理员）
     *
     * @param request 工具创建请求
     */
    @PostMapping
    @RequireRole("admin")
    public Result<ToolVO> create(@RequestBody @Valid ToolCreateRequest request) {
        return Result.success(toolService.create(request));
    }

    /**
     * 删除工具（内置工具 calculator/web_search/knowledge_search 不可删，错误透传）。
     * DELETE /api/tools/{id}（需认证，仅管理员）
     *
     * @param id 工具 id
     */
    @DeleteMapping("/{id}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable String id) {
        toolService.delete(id);
        return Result.success();
    }
}
