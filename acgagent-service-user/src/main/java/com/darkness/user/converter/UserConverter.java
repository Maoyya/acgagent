package com.darkness.user.converter;

import com.darkness.user.entity.UserDO;
import com.darkness.user.model.UserVO;

import java.util.List;

public class UserConverter {

    public static UserVO toVO(UserDO user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }

    public static List<UserVO> toVOList(List<UserDO> users) {
        if (users == null) {
            return null;
        }
        return users.stream().map(UserConverter::toVO).toList();
    }

    public static UserDO toEntity(UserVO vo) {
        if (vo == null) {
            return null;
        }
        UserDO user = new UserDO();
        user.setId(vo.getId());
        user.setUsername(vo.getUsername());
        user.setNickname(vo.getNickname());
        user.setEmail(vo.getEmail());
        user.setPhone(vo.getPhone());
        user.setAvatar(vo.getAvatar());
        user.setStatus(vo.getStatus());
        return user;
    }
}
