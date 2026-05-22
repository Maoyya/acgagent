package com.darkness.user.model;

import com.darkness.user.entity.UserDO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户视图对象，用于 Controller 层与前端之间的数据传输。
 * 排除 password 等敏感字段，仅暴露安全的展示与编辑属性。
 */
@Data
public class UserVO {

    /** 用户主键 ID */
    private Long id;

    /** 登录用户名，唯一 */
    private String username;

    /** 用户昵称，用于前端展示 */
    private String nickname;

    /** 邮箱地址 */
    private String email;

    /** 手机号码 */
    private String phone;

    /** 头像图片 URL */
    private String avatar;

    /** 状态：1-启用，0-禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 将实体对象转换为视图对象。排除 password 字段，避免敏感信息泄露。
     *
     * @param entity 用户实体，为 null 时返回 null
     * @return 用户视图对象
     */
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

    /**
     * 将视图对象转换为实体对象。排除 password、createdAt、updatedAt 字段，
     * 密码需通过专门的修改密码接口设置，时间戳由框架自动管理。
     *
     * @return 用户实体对象
     */
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
