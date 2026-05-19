package com.darkness.user.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.user.entity.UserDO;
import com.darkness.user.mapper.UserDoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final UserDoMapper userDoMapper;

    public Optional<UserDO> findById(Long id) {
        return Optional.ofNullable(userDoMapper.selectById(id));
    }

    public List<UserDO> findAll() {
        return userDoMapper.selectList(null);
    }

    public UserDO save(UserDO user) {
        if (user.getId() == null) {
            userDoMapper.insert(user);
        } else {
            userDoMapper.updateById(user);
        }
        return user;
    }

    public void deleteById(Long id) {
        userDoMapper.deleteById(id);
    }

    public Optional<UserDO> findByUsername(String username) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getUsername, username);
        return Optional.ofNullable(userDoMapper.selectOne(wrapper));
    }

    public Optional<UserDO> findByEmail(String email) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getEmail, email);
        return Optional.ofNullable(userDoMapper.selectOne(wrapper));
    }

    public Optional<UserDO> findByPhone(String phone) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getPhone, phone);
        return Optional.ofNullable(userDoMapper.selectOne(wrapper));
    }
}
