package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户-角色关联实体，映射 sys_user_role 表。多对多关联中间表，一个用户可拥有多个角色。
 */
@Data
@TableName("sys_user_role")
public class UserRoleDO implements Serializable {

    /** 用户 ID，关联 sys_user 表 */
    private Long userId;

    /** 角色 ID，关联 sys_role 表 */
    private Long roleId;
}
