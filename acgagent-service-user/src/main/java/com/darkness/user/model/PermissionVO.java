package com.darkness.user.model;

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
}
