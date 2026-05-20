package com.darkness.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.common.exception.BizException;
import com.darkness.user.entity.UserDO;
import com.darkness.user.entity.UserRoleDO;
import com.darkness.user.mapper.UserMapper;
import com.darkness.user.mapper.UserRoleMapper;
import com.darkness.user.model.UserVO;
import com.darkness.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public UserVO getUserById(Long id) {
        UserDO user = userMapper.selectById(id);
        if (user == null) throw new BizException(404, "User not found: " + id);
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
        UserDO existing = userMapper.selectById(id);
        if (existing == null) throw new BizException(404, "User not found: " + id);
        UserDO entity = vo.toEntity();
        entity.setId(id);
        userMapper.updateById(entity);
        return UserVO.from(userMapper.selectById(id));
    }

    @Override
    public void deleteUser(Long id) {
        UserDO existing = userMapper.selectById(id);
        if (existing == null) throw new BizException(404, "User not found: " + id);
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
