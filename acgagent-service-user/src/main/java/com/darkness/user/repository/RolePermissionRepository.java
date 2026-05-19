package com.darkness.user.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.user.entity.RolePermissionDO;
import com.darkness.user.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RolePermissionRepository {

    private final RolePermissionMapper rolePermissionMapper;

    public List<RolePermissionDO> findByRoleId(Long roleId) {
        LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermissionDO::getRoleId, roleId);
        return rolePermissionMapper.selectList(wrapper);
    }

    public List<RolePermissionDO> findByPermissionId(Long permissionId) {
        LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermissionDO::getPermissionId, permissionId);
        return rolePermissionMapper.selectList(wrapper);
    }

    public void save(RolePermissionDO rolePermission) {
        rolePermissionMapper.insert(rolePermission);
    }

    public void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId) {
        LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermissionDO::getRoleId, roleId)
               .eq(RolePermissionDO::getPermissionId, permissionId);
        rolePermissionMapper.delete(wrapper);
    }

    public void deleteByRoleId(Long roleId) {
        LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermissionDO::getRoleId, roleId);
        rolePermissionMapper.delete(wrapper);
    }
}
