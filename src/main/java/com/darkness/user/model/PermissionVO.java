package com.darkness.user.model;

import com.darkness.user.entity.PermissionDO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限视图对象，用于 Controller 层与前端之间的数据传输。
 */
@Data
public class PermissionVO {

    /** 权限主键 ID */
    private Long id;

    /** 父权限 ID，顶级权限为 0 */
    private Long parentId;

    /** 权限名称，如 "用户管理"、"新增用户" */
    private String name;

    /** 权限编码，唯一标识，如 "user:list"、"user:create" */
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

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 将实体对象转换为视图对象。
     *
     * @param entity 权限实体，为 null 时返回 null
     * @return 权限视图对象
     */
    public static PermissionVO from(PermissionDO entity) {
        if (entity == null) return null;
        PermissionVO vo = new PermissionVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        vo.setName(entity.getName());
        vo.setCode(entity.getCode());
        vo.setType(entity.getType());
        vo.setPath(entity.getPath());
        vo.setIcon(entity.getIcon());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 将视图对象转换为实体对象。排除 createdAt、updatedAt 字段，
     * 时间戳由框架自动管理。
     *
     * @return 权限实体对象
     */
    public PermissionDO toEntity() {
        PermissionDO entity = new PermissionDO();
        entity.setId(this.id);
        entity.setParentId(this.parentId);
        entity.setName(this.name);
        entity.setCode(this.code);
        entity.setType(this.type);
        entity.setPath(this.path);
        entity.setIcon(this.icon);
        entity.setSort(this.sort);
        entity.setStatus(this.status);
        return entity;
    }
}
