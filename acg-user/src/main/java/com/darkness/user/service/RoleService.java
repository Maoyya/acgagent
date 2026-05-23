package com.darkness.user.service;

import com.darkness.common.model.PermissionVO;
import com.darkness.common.model.RoleVO;

import java.util.List;

/**
 * 角色业务接口，提供角色 CRUD 及权限分配能力。
 */
public interface RoleService {

    /**
     * 根据主键查询角色。先通过 ID 检索记录，不存在时抛出 BizException(404)。
     *
     * @param id 角色主键
     * @return 角色视图对象
     */
    RoleVO getRoleById(Long id);

    /**
     * 查询全部角色列表，返回所有未逻辑删除的角色。
     *
     * @return 角色视图对象列表
     */
    List<RoleVO> listRoles();

    /**
     * 创建新角色。将 VO 转换为实体后插入数据库，返回含自动生成 ID 的视图对象。
     *
     * @param vo 角色信息（name、code 必填）
     * @return 创建后的角色视图对象
     */
    RoleVO createRole(RoleVO vo);

    /**
     * 更新角色信息。先校验角色是否存在，不存在时抛出 BizException(404)，
     * 再用 VO 字段覆盖并按 ID 更新。
     *
     * @param id 角色主键
     * @param vo 需要更新的字段
     * @return 更新后的角色视图对象
     */
    RoleVO updateRole(Long id, RoleVO vo);

    /**
     * 逻辑删除角色。先校验角色是否存在，不存在时抛出 BizException(404)，
     * 再执行逻辑删除（deleted 字段置为 1）。
     *
     * @param id 角色主键
     */
    void deleteRole(Long id);

    /**
     * 为角色批量分配权限。先清除该角色已有的全部权限关联记录，
     * 再逐条插入新的关联关系（全量替换策略）。
     *
     * @param roleId        角色主键
     * @param permissionIds 需要分配的权限 ID 列表
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /**
     * 查询角色已关联的权限列表。先通过中间表获取权限 ID 列表，
     * 再批量查询权限实体并转换为视图对象。无关联时返回空列表。
     *
     * @param roleId 角色主键
     * @return 该角色关联的权限视图对象列表
     */
    List<PermissionVO> getPermissions(Long roleId);
}
