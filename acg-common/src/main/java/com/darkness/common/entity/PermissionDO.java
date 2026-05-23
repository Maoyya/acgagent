package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PermissionType;
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

    /** 权限类型：MENU-菜单，BUTTON-按钮 */
    private PermissionType type;

    /** 前端路由路径，菜单类型时使用 */
    private String path;

    /** 菜单图标标识 */
    private String icon;

    /** 排序值，数值越小越靠前 */
    private Integer sort;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;
}
