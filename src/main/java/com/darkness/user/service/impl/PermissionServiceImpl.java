package com.darkness.user.service.impl;

import com.darkness.common.exception.BizException;
import com.darkness.user.entity.PermissionDO;
import com.darkness.user.mapper.PermissionMapper;
import com.darkness.user.model.PermissionVO;
import com.darkness.user.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限业务实现层。基于 MyBatis-Plus 实现权限 CRUD 逻辑，
 * 查询/更新/删除时校验记录是否存在，不存在则抛出 BizException(404)。
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;

    @Override
    public PermissionVO getPermissionById(Long id) {
        PermissionDO perm = permissionMapper.selectById(id);
        if (perm == null) throw new BizException(404, "Permission not found: " + id);
        return PermissionVO.from(perm);
    }

    @Override
    public List<PermissionVO> listPermissions() {
        return permissionMapper.selectList(null).stream().map(PermissionVO::from).toList();
    }

    @Override
    public PermissionVO createPermission(PermissionVO vo) {
        PermissionDO entity = vo.toEntity();
        permissionMapper.insert(entity);
        return PermissionVO.from(entity);
    }

    @Override
    public PermissionVO updatePermission(Long id, PermissionVO vo) {
        PermissionDO existing = permissionMapper.selectById(id);
        if (existing == null) throw new BizException(404, "Permission not found: " + id);
        PermissionDO entity = vo.toEntity();
        entity.setId(id);
        permissionMapper.updateById(entity);
        return PermissionVO.from(permissionMapper.selectById(id));
    }

    @Override
    public void deletePermission(Long id) {
        PermissionDO existing = permissionMapper.selectById(id);
        if (existing == null) throw new BizException(404, "Permission not found: " + id);
        permissionMapper.deleteById(id);
    }
}
