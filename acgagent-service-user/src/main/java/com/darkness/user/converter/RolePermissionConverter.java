package com.darkness.user.converter;

import com.darkness.user.entity.RolePermissionDO;
import com.darkness.user.model.RolePermissionVO;

import java.util.List;

public class RolePermissionConverter {

    public static RolePermissionVO toVO(RolePermissionDO rolePermission) {
        if (rolePermission == null) {
            return null;
        }
        RolePermissionVO vo = new RolePermissionVO();
        vo.setRoleId(rolePermission.getRoleId());
        vo.setPermissionId(rolePermission.getPermissionId());
        return vo;
    }

    public static List<RolePermissionVO> toVOList(List<RolePermissionDO> rolePermissions) {
        if (rolePermissions == null) {
            return null;
        }
        return rolePermissions.stream().map(RolePermissionConverter::toVO).toList();
    }

    public static RolePermissionDO toEntity(RolePermissionVO vo) {
        if (vo == null) {
            return null;
        }
        RolePermissionDO rolePermission = new RolePermissionDO();
        rolePermission.setRoleId(vo.getRoleId());
        rolePermission.setPermissionId(vo.getPermissionId());
        return rolePermission;
    }
}
