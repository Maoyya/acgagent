package com.darkness.user.service;

import com.darkness.common.model.UserVO;

import java.util.List;

/**
 * 用户业务接口，提供用户 CRUD 及角色分配能力。
 */
public interface UserService {

    /**
     * 根据主键查询用户。先通过 ID 检索记录，不存在时抛出 BizException(404)。
     *
     * @param id 用户主键
     * @return 用户视图对象（不含密码）
     */
    UserVO getUserById(Long id);

    /**
     * 查询全部用户列表，返回所有未逻辑删除的用户。
     *
     * @return 用户视图对象列表
     */
    List<UserVO> listUsers();

    /**
     * 创建新用户。将 VO 转换为实体后插入数据库，返回含自动生成 ID 的视图对象。
     *
     * @param vo 用户信息（username 必填）
     * @return 创建后的用户视图对象
     */
    UserVO createUser(UserVO vo);

    /**
     * 更新用户信息。先校验用户是否存在，不存在时抛出 BizException(404)，
     * 再用 VO 字段覆盖并按 ID 更新。
     *
     * @param id 用户主键
     * @param vo 需要更新的字段
     * @return 更新后的用户视图对象
     */
    UserVO updateUser(Long id, UserVO vo);

    /**
     * 逻辑删除用户。先校验用户是否存在，不存在时抛出 BizException(404)，
     * 再执行逻辑删除（MyBatis-Plus deleted 字段置为 1）。
     *
     * @param id 用户主键
     */
    void deleteUser(Long id);

    /**
     * 为用户批量分配角色。先清除该用户已有的全部角色关联记录，
     * 再逐条插入新的关联关系（全量替换策略）。
     *
     * @param userId  用户主键
     * @param roleIds 需要分配的角色 ID 列表
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 查询用户已关联的角色 ID 列表。
     *
     * @param userId 用户主键
     * @return 该用户关联的角色 ID 列表，无关联时返回空列表
     */
    List<Long> getRoleIds(Long userId);
}
