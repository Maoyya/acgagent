package com.darkness.user.model;

import com.darkness.user.entity.PermissionDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PermissionVO {
    private Long id;
    private Long parentId;
    private String name;
    private String code;
    private Integer type;
    private String path;
    private String icon;
    private Integer sort;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
