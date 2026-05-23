package com.darkness.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.common.mapper.PermissionMapper;
import com.darkness.common.mapper.RoleMapper;
import com.darkness.common.mapper.RolePermissionMapper;
import com.darkness.common.util.ServiceHelper;
import com.darkness.common.entity.RoleDO;
import com.darkness.common.entity.RolePermissionDO;
import com.darkness.common.model.PermissionVO;
import com.darkness.common.model.RoleVO;
import com.darkness.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色业务实现层。基于 MyBatis-Plus 实现角色 CRUD 与权限分配逻辑，
 * 查询/更新/删除时校验记录是否存在，不存在则抛出 BizException(404)。
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public RoleVO getRoleById(Long id) {
        return RoleVO.from(ServiceHelper.findOrThrow(roleMapper.selectById(id), "Role", id));
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
        ServiceHelper.findOrThrow(roleMapper.selectById(id), "Role", id);
        RoleDO entity = vo.toEntity();
        entity.setId(id);
        roleMapper.updateById(entity);
        return RoleVO.from(roleMapper.selectById(id));
    }

    @Override
    public void deleteRole(Long id) {
        ServiceHelper.findOrThrow(roleMapper.selectById(id), "Role", id);
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
