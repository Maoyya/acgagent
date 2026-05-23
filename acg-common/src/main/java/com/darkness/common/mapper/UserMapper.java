package com.darkness.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.common.entity.UserDO;

/**
 * 用户数据访问层，基于 MyBatis-Plus BaseMapper 提供用户表的 CRUD 操作。
 */
public interface UserMapper extends BaseMapper<UserDO> {
}
