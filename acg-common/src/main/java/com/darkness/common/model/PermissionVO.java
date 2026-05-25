package com.darkness.common.model;

import com.darkness.common.entity.PermissionDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "权限名称不能为空")
    @Size(max = 64, message = "权限名称长度不能超过64个字符")
    private String name;

    /** 权限编码，唯一标识，如 "user:list"、"user:create" */
    @NotBlank(message = "权限编码不能为空")
    @Size(max = 128, message = "权限编码长度不能超过128个字符")
    private String code;

    /** 权限类型：MENU-菜单，BUTTON-按钮 */
    @NotNull(message = "权限类型不能为空")
    private PermissionType type;

    /** 前端路由路径，菜单类型时使用 */
    @Size(max = 256, message = "路由路径长度不能超过256个字符")
    private String path;

    /** 菜单图标标识 */
    @Size(max = 64, message = "图标标识长度不能超过64个字符")
    private String icon;

    /** 排序值，数值越小越靠前 */
    private Integer sort;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;

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
