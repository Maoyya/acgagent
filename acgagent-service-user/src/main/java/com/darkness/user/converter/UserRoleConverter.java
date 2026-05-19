package com.darkness.user.converter;

import com.darkness.user.entity.UserRoleDO;
import com.darkness.user.model.UserRoleVO;

import java.util.List;

public class UserRoleConverter {

    public static UserRoleVO toVO(UserRoleDO userRole) {
        if (userRole == null) {
            return null;
        }
        UserRoleVO vo = new UserRoleVO();
        vo.setUserId(userRole.getUserId());
        vo.setRoleId(userRole.getRoleId());
        return vo;
    }

    public static List<UserRoleVO> toVOList(List<UserRoleDO> userRoles) {
        if (userRoles == null) {
            return null;
        }
        return userRoles.stream().map(UserRoleConverter::toVO).toList();
    }

    public static UserRoleDO toEntity(UserRoleVO vo) {
        if (vo == null) {
            return null;
        }
        UserRoleDO userRole = new UserRoleDO();
        userRole.setUserId(vo.getUserId());
        userRole.setRoleId(vo.getRoleId());
        return userRole;
    }
}
