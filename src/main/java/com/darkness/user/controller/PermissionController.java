package com.darkness.user.controller;

import com.darkness.common.result.Result;
import com.darkness.user.model.PermissionVO;
import com.darkness.user.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping("/{id}")
    public Result<PermissionVO> getPermission(@PathVariable Long id) {
        return Result.success(permissionService.getPermissionById(id));
    }

    @GetMapping
    public Result<List<PermissionVO>> listPermissions() {
        return Result.success(permissionService.listPermissions());
    }

    @PostMapping
    public Result<PermissionVO> createPermission(@RequestBody PermissionVO vo) {
        return Result.success(permissionService.createPermission(vo));
    }

    @PutMapping("/{id}")
    public Result<PermissionVO> updatePermission(@PathVariable Long id, @RequestBody PermissionVO vo) {
        return Result.success(permissionService.updatePermission(id, vo));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return Result.success();
    }
}
