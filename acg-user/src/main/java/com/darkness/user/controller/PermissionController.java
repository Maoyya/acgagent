package com.darkness.user.controller;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.result.Result;
import com.darkness.common.model.PermissionVO;
import com.darkness.user.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理 REST 控制器，提供权限 CRUD 接口。
 * 认证要求：需认证，仅管理员可访问。
 */
@RestController
@RequestMapping("/api/permissions")
@RequireRole("admin")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 根据 ID 查询权限详情，不存在时抛出 404。
     * GET /api/permissions/{id}
     *
     * @param id 权限主键
     * @return 权限视图对象
     */
    @GetMapping("/{id}")
    public Result<PermissionVO> getPermission(@PathVariable Long id) {
        return Result.success(permissionService.getPermissionById(id));
    }

    /**
     * 查询所有权限列表。
     * GET /api/permissions
     *
     * @return 权限列表
     */
    @GetMapping
    public Result<List<PermissionVO>> listPermissions() {
        return Result.success(permissionService.listPermissions());
    }

    /**
     * 创建新权限，权限编码（code）需唯一。
     * POST /api/permissions
     *
     * @param vo 权限信息（name、code、type 必填）
     * @return 创建后的权限视图对象
     */
    @PostMapping
    public Result<PermissionVO> createPermission(@Valid @RequestBody PermissionVO vo) {
        return Result.success(permissionService.createPermission(vo));
    }

    /**
     * 更新权限信息，不存在时抛出 404。
     * PUT /api/permissions/{id}
     *
     * @param id 权限主键
     * @param vo 需要更新的字段
     * @return 更新后的权限视图对象
     */
    @PutMapping("/{id}")
    public Result<PermissionVO> updatePermission(@PathVariable Long id, @Valid @RequestBody PermissionVO vo) {
        return Result.success(permissionService.updatePermission(id, vo));
    }

    /**
     * 逻辑删除权限。
     * DELETE /api/permissions/{id}
     *
     * @param id 权限主键
     */
    @DeleteMapping("/{id}")
    public Result<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return Result.success();
    }
}
