package com.darkness.user.service.impl;

import com.darkness.common.exception.BizException;
import com.darkness.user.converter.UserConverter;
import com.darkness.user.entity.User;
import com.darkness.user.model.UserVO;
import com.darkness.user.repository.UserRepository;
import com.darkness.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserVO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "User not found: " + id));
        return UserConverter.toVO(user);
    }

    @Override
    public List<UserVO> listUsers() {
        List<User> users = userRepository.findAll();
        return UserConverter.toVOList(users);
    }

    @Override
    public UserVO createUser(UserVO vo) {
        User user = UserConverter.toEntity(vo);
        userRepository.save(user);
        return UserConverter.toVO(user);
    }

    @Override
    public UserVO updateUser(Long id, UserVO vo) {
        userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "User not found: " + id));
        User user = UserConverter.toEntity(vo);
        user.setId(id);
        userRepository.save(user);
        return UserConverter.toVO(userRepository.findById(id).orElse(null));
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "User not found: " + id));
        userRepository.deleteById(id);
    }
}
