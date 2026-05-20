package com.darkness.user.controller;

import com.darkness.common.result.Result;
import com.darkness.user.model.PermissionVO;
import com.darkness.user.model.RoleVO;
import com.darkness.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/{id}")
    public Result<RoleVO> getRole(@PathVariable Long id) {
        return Result.success(roleService.getRoleById(id));
    }

    @GetMapping
    public Result<List<RoleVO>> listRoles() {
        return Result.success(roleService.listRoles());
    }

    @PostMapping
    public Result<RoleVO> createRole(@RequestBody RoleVO vo) {
        return Result.success(roleService.createRole(vo));
    }

    @PutMapping("/{id}")
    public Result<RoleVO> updateRole(@PathVariable Long id, @RequestBody RoleVO vo) {
        return Result.success(roleService.updateRole(id, vo));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    @PostMapping("/{roleId}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long roleId, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(roleId, permissionIds);
        return Result.success();
    }

    @GetMapping("/{roleId}/permissions")
    public Result<List<PermissionVO>> getPermissions(@PathVariable Long roleId) {
        return Result.success(roleService.getPermissions(roleId));
    }
}
