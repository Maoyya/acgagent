package com.darkness.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.common.mapper.UserMapper;
import com.darkness.common.mapper.UserRoleMapper;
import com.darkness.common.util.ServiceHelper;
import com.darkness.common.entity.UserDO;
import com.darkness.common.entity.UserRoleDO;
import com.darkness.common.model.UserVO;
import com.darkness.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户业务实现层。基于 MyBatis-Plus 实现用户 CRUD 与角色分配逻辑，
 * 查询/更新/删除时校验记录是否存在，不存在则抛出 BizException(404)。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public UserVO getUserById(Long id) {
        UserDO user = ServiceHelper.findOrThrow(userMapper.selectById(id), "User", id);
        return UserVO.from(user);
    }

    @Override
    public List<UserVO> listUsers() {
        return userMapper.selectList(null).stream().map(UserVO::from).toList();
    }

    @Override
    public UserVO createUser(UserVO vo) {
        UserDO entity = vo.toEntity();
        userMapper.insert(entity);
        return UserVO.from(entity);
    }

    @Override
    public UserVO updateUser(Long id, UserVO vo) {
        ServiceHelper.findOrThrow(userMapper.selectById(id), "User", id);
        UserDO entity = vo.toEntity();
        entity.setId(id);
        userMapper.updateById(entity);
        return UserVO.from(userMapper.selectById(id));
    }

    @Override
    public void deleteUser(Long id) {
        ServiceHelper.findOrThrow(userMapper.selectById(id), "User", id);
        userMapper.deleteById(id);
    }

    @Override
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        for (Long roleId : roleIds) {
            UserRoleDO ur = new UserRoleDO();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    @Override
    public List<Long> getRoleIds(Long userId) {
        return userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId)
        ).stream().map(UserRoleDO::getRoleId).toList();
    }
}
