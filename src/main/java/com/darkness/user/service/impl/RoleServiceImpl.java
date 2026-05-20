package com.darkness.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.common.exception.BizException;
import com.darkness.user.entity.PermissionDO;
import com.darkness.user.entity.RoleDO;
import com.darkness.user.entity.RolePermissionDO;
import com.darkness.user.mapper.PermissionMapper;
import com.darkness.user.mapper.RoleMapper;
import com.darkness.user.mapper.RolePermissionMapper;
import com.darkness.user.model.PermissionVO;
import com.darkness.user.model.RoleVO;
import com.darkness.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public RoleVO getRoleById(Long id) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) throw new BizException(404, "Role not found: " + id);
        return RoleVO.from(role);
    }

    @Override
    public List<RoleVO> listRoles() {
        return roleMapper.selectList(null).stream().map(RoleVO::from).toList();
    }

    @Override
    public RoleVO createRole(RoleVO vo) {
        RoleDO entity = vo.toEntity();
        roleMapper.insert(entity);
        return RoleVO.from(entity);
    }

    @Override
    public RoleVO updateRole(Long id, RoleVO vo) {
        RoleDO existing = roleMapper.selectById(id);
        if (existing == null) throw new BizException(404, "Role not found: " + id);
        RoleDO entity = vo.toEntity();
        entity.setId(id);
        roleMapper.updateById(entity);
        return RoleVO.from(roleMapper.selectById(id));
    }

    @Override
    public void deleteRole(Long id) {
        RoleDO existing = roleMapper.selectById(id);
        if (existing == null) throw new BizException(404, "Role not found: " + id);
        roleMapper.deleteById(id);
    }

    @Override
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId));
        for (Long permId : permissionIds) {
            RolePermissionDO rp = new RolePermissionDO();
            rp.setRoleId(roleId);
            rp.setPermissionId(permId);
            rolePermissionMapper.insert(rp);
        }
    }

    @Override
    public List<PermissionVO> getPermissions(Long roleId) {
        List<Long> permIds = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId)
        ).stream().map(RolePermissionDO::getPermissionId).toList();

        if (permIds.isEmpty()) return List.of();
        return permissionMapper.selectBatchIds(permIds).stream().map(PermissionVO::from).toList();
    }
}
