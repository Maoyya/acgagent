package com.darkness.user.service.impl;

import com.darkness.common.entity.UserDO;
import com.darkness.common.entity.UserRoleDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.UserMapper;
import com.darkness.common.mapper.UserRoleMapper;
import com.darkness.common.model.UserVO;
import com.darkness.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 用户服务单元测试，覆盖用户 CRUD 和角色分配逻辑。
 * 核心验证点：不存在的记录抛 NOT_FOUND，角色分配使用全量替换策略。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @InjectMocks
    private UserServiceImpl userService;

    // ==================== getUserById ====================

    @Test
    void getUserById_success() {
        UserDO user = new UserDO();
        user.setId(1L);
        user.setUsername("testuser");
        when(userMapper.selectById(1L)).thenReturn(user);

        UserVO result = userService.getUserById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
    }

    @Test
    void getUserById_notFound_throws404() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    // ==================== listUsers ====================

    @Test
    void listUsers_returnsAll() {
        UserDO u1 = new UserDO();
        u1.setId(1L);
        u1.setUsername("user1");
        UserDO u2 = new UserDO();
        u2.setId(2L);
        u2.setUsername("user2");
        when(userMapper.selectList(null)).thenReturn(List.of(u1, u2));

        List<UserVO> result = userService.listUsers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("user1");
        assertThat(result.get(1).getUsername()).isEqualTo("user2");
    }

    // ==================== createUser ====================

    @Test
    void createUser_success() {
        UserVO vo = new UserVO();
        vo.setUsername("newuser");
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            UserDO entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        UserVO result = userService.createUser(vo);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    // ==================== updateUser ====================

    @Test
    void updateUser_success() {
        UserDO existing = new UserDO();
        existing.setId(1L);
        existing.setUsername("oldname");
        when(userMapper.selectById(1L)).thenReturn(existing);
        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);

        UserVO updateVo = new UserVO();
        updateVo.setUsername("newname");

        UserVO result = userService.updateUser(1L, updateVo);

        assertThat(result).isNotNull();
    }

    @Test
    void updateUser_notFound_throws404() {
        when(userMapper.selectById(999L)).thenReturn(null);

        UserVO vo = new UserVO();
        assertThatThrownBy(() -> userService.updateUser(999L, vo))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    // ==================== deleteUser ====================

    @Test
    void deleteUser_success() {
        UserDO existing = new UserDO();
        existing.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(existing);
        when(userMapper.deleteById(1L)).thenReturn(1);

        userService.deleteUser(1L);

        verify(userMapper).deleteById(1L);
    }

    @Test
    void deleteUser_notFound_throws404() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    // ==================== assignRoles ====================

    @Test
    void assignRoles_replacesAllRoles() {
        when(userRoleMapper.delete(any())).thenReturn(2);
        when(userRoleMapper.insert(any(UserRoleDO.class))).thenReturn(1);

        userService.assignRoles(1L, List.of(10L, 20L));

        // 验证先删后插：delete 1 次，insert 2 次
        verify(userRoleMapper).delete(any());
        verify(userRoleMapper, times(2)).insert(any(UserRoleDO.class));
    }

    // ==================== getRoleIds ====================

    @Test
    void getRoleIds_returnsRoleIdsForUser() {
        UserRoleDO ur1 = new UserRoleDO();
        ur1.setRoleId(10L);
        UserRoleDO ur2 = new UserRoleDO();
        ur2.setRoleId(20L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(ur1, ur2));

        List<Long> roleIds = userService.getRoleIds(1L);

        assertThat(roleIds).containsExactly(10L, 20L);
    }
}
