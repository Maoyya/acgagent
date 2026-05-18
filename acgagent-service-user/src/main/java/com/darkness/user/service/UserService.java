package com.darkness.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.darkness.user.entity.User;

import java.util.List;

public interface UserService extends IService<User> {

    User getUserById(Long id);

    List<User> listUsers();

    User createUser(User user);

    User updateUser(Long id, User user);

    void deleteUser(Long id);
}
