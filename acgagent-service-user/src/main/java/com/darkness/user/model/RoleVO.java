package com.darkness.user.model;

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
}
