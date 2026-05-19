package com.darkness.user.converter;

import com.darkness.user.entity.PermissionDO;
import com.darkness.user.model.PermissionVO;

import java.util.List;

public class PermissionConverter {

    public static PermissionVO toVO(PermissionDO permission) {
        if (permission == null) {
            return null;
        }
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setParentId(permission.getParentId());
        vo.setName(permission.getName());
        vo.setCode(permission.getCode());
        vo.setType(permission.getType());
        vo.setPath(permission.getPath());
        vo.setIcon(permission.getIcon());
        vo.setSort(permission.getSort());
        vo.setStatus(permission.getStatus());
        vo.setCreatedAt(permission.getCreatedAt());
        vo.setUpdatedAt(permission.getUpdatedAt());
        return vo;
    }

    public static List<PermissionVO> toVOList(List<PermissionDO> permissions) {
        if (permissions == null) {
            return null;
        }
        return permissions.stream().map(PermissionConverter::toVO).toList();
    }

    public static PermissionDO toEntity(PermissionVO vo) {
        if (vo == null) {
            return null;
        }
        PermissionDO permission = new PermissionDO();
        permission.setId(vo.getId());
        permission.setParentId(vo.getParentId());
        permission.setName(vo.getName());
        permission.setCode(vo.getCode());
        permission.setType(vo.getType());
        permission.setPath(vo.getPath());
        permission.setIcon(vo.getIcon());
        permission.setSort(vo.getSort());
        permission.setStatus(vo.getStatus());
        return permission;
    }
}
