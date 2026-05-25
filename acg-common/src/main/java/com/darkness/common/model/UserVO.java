package com.darkness.common.model;

import com.darkness.common.entity.UserDO;
import com.darkness.common.enums.CommonStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(min = 5, max = 50, message = "用户名长度必须在5-50个字符之间")
    private String username;

    /** 用户昵称，用于前端展示 */
    @Size(max = 64, message = "昵称长度不能超过64个字符")
    private String nickname;

    /** 邮箱地址 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    private String email;

    /** 手机号码 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    private String phone;

    /** 头像图片 URL */
    @Size(max = 512, message = "头像URL长度不能超过512个字符")
    private String avatar;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;

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
