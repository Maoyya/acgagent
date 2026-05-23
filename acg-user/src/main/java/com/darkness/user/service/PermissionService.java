package com.darkness.user.service;

import com.darkness.common.model.PermissionVO;

import java.util.List;

/**
 * 权限业务接口，提供权限 CRUD 能力。
 */
public interface PermissionService {

    /**
     * 根据主键查询权限。先通过 ID 检索记录，不存在时抛出 BizException(404)。
     *
     * @param id 权限主键
     * @return 权限视图对象
     */
    PermissionVO getPermissionById(Long id);

    /**
     * 查询全部权限列表，返回所有未逻辑删除的权限。
     *
     * @return 权限视图对象列表
     */
    List<PermissionVO> listPermissions();

    /**
     * 创建新权限。将 VO 转换为实体后插入数据库，返回含自动生成 ID 的视图对象。
     *
     * @param vo 权限信息（name、code、type 必填）
     * @return 创建后的权限视图对象
     */
    PermissionVO createPermission(PermissionVO vo);

    /**
     * 更新权限信息。先校验权限是否存在，不存在时抛出 BizException(404)，
     * 再用 VO 字段覆盖并按 ID 更新。
     *
     * @param id 权限主键
     * @param vo 需要更新的字段
     * @return 更新后的权限视图对象
     */
    PermissionVO updatePermission(Long id, PermissionVO vo);

    /**
     * 逻辑删除权限。先校验权限是否存在，不存在时抛出 BizException(404)，
     * 再执行逻辑删除（deleted 字段置为 1）。
     *
     * @param id 权限主键
     */
    void deletePermission(Long id);
}
