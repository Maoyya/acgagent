package com.darkness.user.service.impl;

import com.darkness.common.entity.PermissionDO;
import com.darkness.common.entity.RoleDO;
import com.darkness.common.entity.RolePermissionDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.PermissionMapper;
import com.darkness.common.mapper.RoleMapper;
import com.darkness.common.mapper.RolePermissionMapper;
import com.darkness.common.model.PermissionVO;
import com.darkness.common.model.RoleVO;
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
 * 角色服务单元测试，覆盖角色 CRUD、权限分配和权限查询逻辑。
 * 核心验证点：不存在的记录抛 NOT_FOUND，权限分配使用全量替换策略，空权限列表不报错。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    // ==================== getRoleById ====================

    @Test
    void getRoleById_success() {
        RoleDO role = new RoleDO();
        role.setId(1L);
        role.setName("ADMIN");
        when(roleMapper.selectById(1L)).thenReturn(role);

        RoleVO result = roleService.getRoleById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("ADMIN");
    }

    @Test
    void getRoleById_notFound_throws404() {
        when(roleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> roleService.getRoleById(999L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    // ==================== listRoles ====================

    @Test
    void listRoles_returnsAll() {
        RoleDO r1 = new RoleDO();
        r1.setId(1L);
        r1.setName("ADMIN");
        RoleDO r2 = new RoleDO();
        r2.setId(2L);
        r2.setName("USER");
        when(roleMapper.selectList(null)).thenReturn(List.of(r1, r2));

        List<RoleVO> result = roleService.listRoles();

        assertThat(result).hasSize(2);
    }

    // ==================== createRole ====================

    @Test
    void createRole_success() {
        RoleVO vo = new RoleVO();
        vo.setName("NEW_ROLE");
        vo.setCode("new_role");
        when(roleMapper.insert(any(RoleDO.class))).thenAnswer(invocation -> {
            RoleDO entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        RoleVO result = roleService.createRole(vo);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    // ==================== updateRole ====================

    @Test
    void updateRole_success() {
        RoleDO existing = new RoleDO();
        existing.setId(1L);
        when(roleMapper.selectById(1L)).thenReturn(existing);
        when(roleMapper.updateById(any(RoleDO.class))).thenReturn(1);

        RoleVO vo = new RoleVO();
        vo.setName("UPDATED");

        RoleVO result = roleService.updateRole(1L, vo);

        assertThat(result).isNotNull();
    }

    @Test
    void updateRole_notFound_throws404() {
        when(roleMapper.selectById(999L)).thenReturn(null);

        RoleVO vo = new RoleVO();
        assertThatThrownBy(() -> roleService.updateRole(999L, vo))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    // ==================== deleteRole ====================

    @Test
    void deleteRole_success() {
        RoleDO existing = new RoleDO();
        existing.setId(1L);
        when(roleMapper.selectById(1L)).thenReturn(existing);
        when(roleMapper.deleteById(1L)).thenReturn(1);

        roleService.deleteRole(1L);

        verify(roleMapper).deleteById(1L);
    }

    @Test
    void deleteRole_notFound_throws404() {
        when(roleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> roleService.deleteRole(999L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    // ==================== assignPermissions ====================

    @Test
    void assignPermissions_replacesAll() {
        when(rolePermissionMapper.delete(any())).thenReturn(2);
        when(rolePermissionMapper.insert(any(RolePermissionDO.class))).thenReturn(1);

        roleService.assignPermissions(1L, List.of(100L, 200L));

        verify(rolePermissionMapper).delete(any());
        verify(rolePermissionMapper, times(2)).insert(any(RolePermissionDO.class));
    }

    // ==================== getPermissions ====================

    @Test
    void getPermissions_returnsPermissionsForRole() {
        RolePermissionDO rp1 = new RolePermissionDO();
        rp1.setPermissionId(100L);
        RolePermissionDO rp2 = new RolePermissionDO();
        rp2.setPermissionId(200L);
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rp1, rp2));

        PermissionDO p1 = new PermissionDO();
        p1.setId(100L);
        p1.setName("user:read");
        PermissionDO p2 = new PermissionDO();
        p2.setId(200L);
        p2.setName("user:write");
        when(permissionMapper.selectBatchIds(List.of(100L, 200L))).thenReturn(List.of(p1, p2));

        List<PermissionVO> result = roleService.getPermissions(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("user:read");
    }

    @Test
    void getPermissions_noPermissions_returnsEmpty() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of());

        List<PermissionVO> result = roleService.getPermissions(1L);

        assertThat(result).isEmpty();
        verify(permissionMapper, never()).selectBatchIds(any());
    }
}
