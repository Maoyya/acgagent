package com.darkness.user.converter;

import com.darkness.user.entity.RoleDO;
import com.darkness.user.model.RoleVO;

import java.util.List;

public class RoleConverter {

    public static RoleVO toVO(RoleDO role) {
        if (role == null) {
            return null;
        }
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setName(role.getName());
        vo.setCode(role.getCode());
        vo.setSort(role.getSort());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setCreatedAt(role.getCreatedAt());
        vo.setUpdatedAt(role.getUpdatedAt());
        return vo;
    }

    public static List<RoleVO> toVOList(List<RoleDO> roles) {
        if (roles == null) {
            return null;
        }
        return roles.stream().map(RoleConverter::toVO).toList();
    }

    public static RoleDO toEntity(RoleVO vo) {
        if (vo == null) {
            return null;
        }
        RoleDO role = new RoleDO();
        role.setId(vo.getId());
        role.setName(vo.getName());
        role.setCode(vo.getCode());
        role.setSort(vo.getSort());
        role.setStatus(vo.getStatus());
        role.setRemark(vo.getRemark());
        return role;
    }
}
