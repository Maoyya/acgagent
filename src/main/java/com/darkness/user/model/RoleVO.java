package com.darkness.user.model;

import com.darkness.user.entity.RoleDO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色视图对象，用于 Controller 层与前端之间的数据传输。
 */
@Data
public class RoleVO {

    /** 角色主键 ID */
    private Long id;

    /** 角色名称，如 "管理员"、"普通用户" */
    private String name;

    /** 角色编码，唯一标识，如 "admin"、"user" */
    private String code;

    /** 排序值，数值越小越靠前 */
    private Integer sort;

    /** 状态：1-启用，0-禁用 */
    private Integer status;

    /** 备注说明 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 将实体对象转换为视图对象。
     *
     * @param entity 角色实体，为 null 时返回 null
     * @return 角色视图对象
     */
    public static RoleVO from(RoleDO entity) {
        if (entity == null) return null;
        RoleVO vo = new RoleVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setCode(entity.getCode());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 将视图对象转换为实体对象。排除 createdAt、updatedAt 字段，
     * 时间戳由框架自动管理。
     *
     * @return 角色实体对象
     */
    public RoleDO toEntity() {
        RoleDO entity = new RoleDO();
        entity.setId(this.id);
        entity.setName(this.name);
        entity.setCode(this.code);
        entity.setSort(this.sort);
        entity.setStatus(this.status);
        entity.setRemark(this.remark);
        return entity;
    }
}
