package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("sys_role_permission")
public class RolePermissionDO implements Serializable {

    private Long roleId;
    private Long permissionId;
}
