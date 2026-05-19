package com.darkness.user.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.user.entity.UserRoleDO;
import com.darkness.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserRoleRepository {

    private final UserRoleMapper userRoleMapper;

    public List<UserRoleDO> findByUserId(Long userId) {
        LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRoleDO::getUserId, userId);
        return userRoleMapper.selectList(wrapper);
    }

    public List<UserRoleDO> findByRoleId(Long roleId) {
        LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRoleDO::getRoleId, roleId);
        return userRoleMapper.selectList(wrapper);
    }

    public void save(UserRoleDO userRole) {
        userRoleMapper.insert(userRole);
    }

    public void deleteByUserIdAndRoleId(Long userId, Long roleId) {
        LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRoleDO::getUserId, userId)
               .eq(UserRoleDO::getRoleId, roleId);
        userRoleMapper.delete(wrapper);
    }

    public void deleteByUserId(Long userId) {
        LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRoleDO::getUserId, userId);
        userRoleMapper.delete(wrapper);
    }
}
