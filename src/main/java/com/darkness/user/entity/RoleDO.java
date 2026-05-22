package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体，映射 sys_role 表。用于 RBAC 权限模型中的角色定义。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class RoleDO extends BaseEntity {

    /** 角色名称，如 "管理员"、"普通用户" */
    private String name;

    /** 角色编码，唯一标识，如 "admin"、"user"，用于程序中权限判断 */
    private String code;

    /** 排序值，数值越小越靠前 */
    private Integer sort;

    /** 状态：1-启用，0-禁用 */
    private Integer status;

    /** 备注说明 */
    private String remark;
}
