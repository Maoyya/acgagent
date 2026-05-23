package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色-权限关联实体，映射 sys_role_permission 表。多对多关联中间表，一个角色可拥有多个权限。
 */
@Data
@TableName("sys_role_permission")
public class RolePermissionDO implements Serializable {

    /** 角色 ID，关联 sys_role 表 */
    private Long roleId;

    /** 权限 ID，关联 sys_permission 表 */
    private Long permissionId;
}
