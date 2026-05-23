package com.darkness.api.facade;

import com.darkness.api.dto.UserDTO;

import java.util.List;

/**
 * 用户服务 Dubbo Facade，供其他服务通过 RPC 查询用户信息。
 */
public interface UserFacade {

    /**
     * 根据用户 ID 查询用户基本信息。
     *
     * @param userId 用户 ID
     * @return 用户 DTO，用户不存在时返回 null
     */
    UserDTO getUserById(Long userId);

    /**
     * 批量查询用户信息。
     *
     * @param userIds 用户 ID 列表
     * @return 用户 DTO 列表
     */
    List<UserDTO> listUsersByIds(List<Long> userIds);
}
