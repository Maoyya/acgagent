package com.darkness.user.service;

import com.darkness.user.model.PermissionVO;
import com.darkness.user.model.RoleVO;

import java.util.List;

public interface RoleService {
    RoleVO getRoleById(Long id);
    List<RoleVO> listRoles();
    RoleVO createRole(RoleVO vo);
    RoleVO updateRole(Long id, RoleVO vo);
    void deleteRole(Long id);
    void assignPermissions(Long roleId, List<Long> permissionIds);
    List<PermissionVO> getPermissions(Long roleId);
}
