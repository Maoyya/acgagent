package com.darkness.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.common.entity.UserRoleDO;

/**
 * 用户-角色关联数据访问层，基于 MyBatis-Plus BaseMapper 提供中间表的 CRUD 操作。
 */
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {
}
