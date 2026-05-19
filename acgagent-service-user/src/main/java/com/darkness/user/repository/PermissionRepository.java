package com.darkness.user.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.user.entity.PermissionDO;
import com.darkness.user.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PermissionRepository {

    private final PermissionMapper permissionMapper;

    public Optional<PermissionDO> findById(Long id) {
        return Optional.ofNullable(permissionMapper.selectById(id));
    }

    public List<PermissionDO> findAll() {
        return permissionMapper.selectList(null);
    }

    public PermissionDO save(PermissionDO permission) {
        if (permission.getId() == null) {
            permissionMapper.insert(permission);
        } else {
            permissionMapper.updateById(permission);
        }
        return permission;
    }

    public void deleteById(Long id) {
        permissionMapper.deleteById(id);
    }

    public List<PermissionDO> findByParentId(Long parentId) {
        LambdaQueryWrapper<PermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionDO::getParentId, parentId);
        return permissionMapper.selectList(wrapper);
    }

    public List<PermissionDO> findByType(Integer type) {
        LambdaQueryWrapper<PermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionDO::getType, type);
        return permissionMapper.selectList(wrapper);
    }
}
