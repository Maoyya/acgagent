package com.darkness.user.service;

import com.darkness.user.model.UserVO;

import java.util.List;

public interface UserService {

    UserVO getUserById(Long id);

    List<UserVO> listUsers();

    UserVO createUser(UserVO vo);

    UserVO updateUser(Long id, UserVO vo);

    void deleteUser(Long id);
}
