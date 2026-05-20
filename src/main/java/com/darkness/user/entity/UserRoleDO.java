package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("sys_user_role")
public class UserRoleDO implements Serializable {
    private Long userId;
    private Long roleId;
}
