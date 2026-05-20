package com.darkness.user.controller;

import com.darkness.common.result.Result;
import com.darkness.user.model.UserVO;
import com.darkness.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @GetMapping
    public Result<List<UserVO>> listUsers() {
        return Result.success(userService.listUsers());
    }

    @PostMapping
    public Result<UserVO> createUser(@RequestBody UserVO vo) {
        return Result.success(userService.createUser(vo));
    }

    @PutMapping("/{id}")
    public Result<UserVO> updateUser(@PathVariable Long id, @RequestBody UserVO vo) {
        return Result.success(userService.updateUser(id, vo));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @PostMapping("/{userId}/roles")
    public Result<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
        return Result.success();
    }

    @GetMapping("/{userId}/roles")
    public Result<List<Long>> getRoles(@PathVariable Long userId) {
        return Result.success(userService.getRoleIds(userId));
    }
}
