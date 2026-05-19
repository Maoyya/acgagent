package com.darkness.user.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.user.entity.RoleDO;
import com.darkness.user.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleRepository {

    private final RoleMapper roleMapper;

    public Optional<RoleDO> findById(Long id) {
        return Optional.ofNullable(roleMapper.selectById(id));
    }

    public List<RoleDO> findAll() {
        return roleMapper.selectList(null);
    }

    public RoleDO save(RoleDO role) {
        if (role.getId() == null) {
            roleMapper.insert(role);
        } else {
            roleMapper.updateById(role);
        }
        return role;
    }

    public void deleteById(Long id) {
        roleMapper.deleteById(id);
    }

    public Optional<RoleDO> findByCode(String code) {
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleDO::getCode, code);
        return Optional.ofNullable(roleMapper.selectOne(wrapper));
    }
}
