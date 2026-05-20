package com.darkness.user.service;

import com.darkness.user.model.PermissionVO;

import java.util.List;

public interface PermissionService {
    PermissionVO getPermissionById(Long id);
    List<PermissionVO> listPermissions();
    PermissionVO createPermission(PermissionVO vo);
    PermissionVO updatePermission(Long id, PermissionVO vo);
    void deletePermission(Long id);
}
