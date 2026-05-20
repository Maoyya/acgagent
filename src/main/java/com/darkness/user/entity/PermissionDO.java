package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class PermissionDO extends BaseEntity {
    private Long parentId;
    private String name;
    private String code;
    private Integer type;
    private String path;
    private String icon;
    private Integer sort;
    private Integer status;
}
