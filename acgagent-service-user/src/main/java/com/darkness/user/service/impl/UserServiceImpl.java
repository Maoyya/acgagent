package com.darkness.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.darkness.common.exception.BizException;
import com.darkness.user.entity.User;
import com.darkness.user.mapper.UserMapper;
import com.darkness.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User getUserById(Long id) {
        User user = this.getById(id);
        if (user == null) {
            throw new BizException(404, "User not found: " + id);
        }
        return user;
    }

    @Override
    public List<User> listUsers() {
        return this.list();
    }

    @Override
    public User createUser(User user) {
        this.save(user);
        return user;
    }

    @Override
    public User updateUser(Long id, User user) {
        user.setId(id);
        this.updateById(user);
        return this.getById(id);
    }

    @Override
    public void deleteUser(Long id) {
        this.removeById(id);
    }
}
