package com.darkness.user.dubbo;

import com.darkness.api.dto.UserDTO;
import com.darkness.api.facade.UserFacade;
import com.darkness.common.entity.UserDO;
import com.darkness.common.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务 Dubbo Facade 实现，供其他服务通过 RPC 查询用户信息。
 */
@DubboService
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {

    private final UserMapper userMapper;

    @Override
    public UserDTO getUserById(Long userId) {
        UserDO user = userMapper.selectById(userId);
        return toDTO(user);
    }

    @Override
    public List<UserDTO> listUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDO> users = userMapper.selectBatchIds(userIds);
        return users.stream().map(this::toDTO).collect(Collectors.toList());
    }

    private UserDTO toDTO(UserDO user) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setStatus(user.getStatus() != null ? user.getStatus().getValue() : null);
        return dto;
    }
}
