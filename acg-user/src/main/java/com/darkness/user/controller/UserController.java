package com.darkness.user.controller;

import com.darkness.common.result.Result;
import com.darkness.common.model.UserVO;
import com.darkness.common.util.UserContext;
import com.darkness.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理 REST 控制器，提供用户 CRUD 及角色分配接口。
 * 认证要求：当前版本暂未接入认证，后续 JWT 集成后需携带有效 Token。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息。从 Gateway 传递的 X-User-Id header 中提取用户 ID。
     * GET /api/users/me（需认证）
     *
     * @return 当前用户视图对象（不含密码）
     */
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        Long userId = UserContext.getUserId();
        return Result.success(userService.getUserById(userId));
    }

    /**
     * 根据 ID 查询用户详情，不存在时抛出 404。
     * GET /api/users/{id}
     *
     * @param id 用户主键
     * @return 用户视图对象（不含密码）
     */
    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    /**
     * 查询所有用户列表。
     * GET /api/users
     *
     * @return 用户列表
     */
    @GetMapping
    public Result<List<UserVO>> listUsers() {
        return Result.success(userService.listUsers());
    }

    /**
     * 创建新用户，用户名需唯一。
     * POST /api/users
     *
     * @param vo 用户信息（username 必填）
     * @return 创建后的用户视图对象
     */
    @PostMapping
    public Result<UserVO> createUser(@Valid @RequestBody UserVO vo) {
        return Result.success(userService.createUser(vo));
    }

    /**
     * 更新用户信息，不存在时抛出 404。
     * PUT /api/users/{id}
     *
     * @param id 用户主键
     * @param vo 需要更新的字段
     * @return 更新后的用户视图对象
     */
    @PutMapping("/{id}")
    public Result<UserVO> updateUser(@PathVariable Long id, @Valid @RequestBody UserVO vo) {
        return Result.success(userService.updateUser(id, vo));
    }

    /**
     * 逻辑删除用户。
     * DELETE /api/users/{id}
     *
     * @param id 用户主键
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    /**
     * 为用户批量分配角色，先清除该用户已有角色再重新写入。
     * POST /api/users/{userId}/roles
     *
     * @param userId  用户主键
     * @param roleIds 需要分配的角色 ID 列表
     */
    @PostMapping("/{userId}/roles")
    public Result<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
        return Result.success();
    }

    /**
     * 查询用户已分配的角色 ID 列表。
     * GET /api/users/{userId}/roles
     *
     * @param userId 用户主键
     * @return 该用户关联的角色 ID 列表
     */
    @GetMapping("/{userId}/roles")
    public Result<List<Long>> getRoles(@PathVariable Long userId) {
        return Result.success(userService.getRoleIds(userId));
    }
}
