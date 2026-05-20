package com.darkness.user.model;

import com.darkness.user.entity.UserDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserVO from(UserDO entity) {
        if (entity == null) return null;
        UserVO vo = new UserVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setNickname(entity.getNickname());
        vo.setEmail(entity.getEmail());
        vo.setPhone(entity.getPhone());
        vo.setAvatar(entity.getAvatar());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public UserDO toEntity() {
        UserDO entity = new UserDO();
        entity.setId(this.id);
        entity.setUsername(this.username);
        entity.setNickname(this.nickname);
        entity.setEmail(this.email);
        entity.setPhone(this.phone);
        entity.setAvatar(this.avatar);
        entity.setStatus(this.status);
        return entity;
    }
}
