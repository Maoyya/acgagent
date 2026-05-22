package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限实体，映射 sys_permission 表。用于 RBAC 权限模型中的权限定义，支持树形结构。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class PermissionDO extends BaseEntity {

    /** 父权限 ID，顶级权限为 0，用于构建树形权限层级 */
    private Long parentId;

    /** 权限名称，如 "用户管理"、"新增用户" */
    private String name;

    /** 权限编码，唯一标识，如 "user:list"、"user:create"，用于程序中权限校验 */
    private String code;

    /** 权限类型：1-菜单，2-按钮（操作） */
    private Integer type;

    /** 前端路由路径，菜单类型时使用 */
    private String path;

    /** 菜单图标标识 */
    private String icon;

    /** 排序值，数值越小越靠前 */
    private Integer sort;

    /** 状态：1-启用，0-禁用 */
    private Integer status;
}
