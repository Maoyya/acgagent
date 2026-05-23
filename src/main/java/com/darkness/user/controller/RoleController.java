package com.darkness.user.controller;

import com.darkness.common.result.Result;
import com.darkness.user.model.PermissionVO;
import com.darkness.user.model.RoleVO;
import com.darkness.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理 REST 控制器，提供角色 CRUD 及权限分配接口。
 * 认证要求：当前版本暂未接入认证，后续 JWT 集成后需携带有效 Token。
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 根据 ID 查询角色详情，不存在时抛出 404。
     * GET /api/roles/{id}
     *
     * @param id 角色主键
     * @return 角色视图对象
     */
    @GetMapping("/{id}")
    public Result<RoleVO> getRole(@PathVariable Long id) {
        return Result.success(roleService.getRoleById(id));
    }

    /**
     * 查询所有角色列表。
     * GET /api/roles
     *
     * @return 角色列表
     */
    @GetMapping
    public Result<List<RoleVO>> listRoles() {
        return Result.success(roleService.listRoles());
    }

    /**
     * 创建新角色，角色编码（code）需唯一。
     * POST /api/roles
     *
     * @param vo 角色信息（name、code 必填）
     * @return 创建后的角色视图对象
     */
    @PostMapping
    public Result<RoleVO> createRole(@RequestBody RoleVO vo) {
        return Result.success(roleService.createRole(vo));
    }

    /**
     * 更新角色信息，不存在时抛出 404。
     * PUT /api/roles/{id}
     *
     * @param id 角色主键
     * @param vo 需要更新的字段
     * @return 更新后的角色视图对象
     */
    @PutMapping("/{id}")
    public Result<RoleVO> updateRole(@PathVariable Long id, @RequestBody RoleVO vo) {
        return Result.success(roleService.updateRole(id, vo));
    }

    /**
     * 逻辑删除角色。
     * DELETE /api/roles/{id}
     *
     * @param id 角色主键
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    /**
     * 为角色批量分配权限，先清除该角色已有权限再重新写入。
     * POST /api/roles/{roleId}/permissions
     *
     * @param roleId        角色主键
     * @param permissionIds 需要分配的权限 ID 列表
     */
    @PostMapping("/{roleId}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long roleId, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(roleId, permissionIds);
        return Result.success();
    }

    /**
     * 查询角色已分配的权限列表。
     * GET /api/roles/{roleId}/permissions
     *
     * @param roleId 角色主键
     * @return 该角色关联的权限列表
     */
    @GetMapping("/{roleId}/permissions")
    public Result<List<PermissionVO>> getPermissions(@PathVariable Long roleId) {
        return Result.success(roleService.getPermissions(roleId));
    }
}
