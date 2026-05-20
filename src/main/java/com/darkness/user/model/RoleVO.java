package com.darkness.user.model;

import com.darkness.user.entity.RoleDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleVO {
    private Long id;
    private String name;
    private String code;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
