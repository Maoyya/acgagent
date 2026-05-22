package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体，映射 sys_user 表。存储系统用户的基本信息和认证凭证。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserDO extends BaseEntity {

    /** 登录用户名，唯一 */
    private String username;

    /** 登录密码，加密存储 */
    private String password;

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
}
