package com.darkness.user.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.auth.util.PasswordUtil;
import com.darkness.common.entity.PermissionDO;
import com.darkness.common.entity.RoleDO;
import com.darkness.common.entity.RolePermissionDO;
import com.darkness.common.entity.SmsCodeDO;
import com.darkness.common.entity.UserDO;
import com.darkness.common.entity.UserRoleDO;
import com.darkness.common.entity.WxUserDO;
import com.darkness.common.enums.UsedStatus;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.PermissionMapper;
import com.darkness.common.mapper.RoleMapper;
import com.darkness.common.mapper.RolePermissionMapper;
import com.darkness.common.mapper.SmsCodeMapper;
import com.darkness.common.mapper.UserMapper;
import com.darkness.common.mapper.UserRoleMapper;
import com.darkness.common.mapper.WxUserMapper;
import com.darkness.common.model.ChangePasswordRequest;
import com.darkness.common.model.ChangePhoneRequest;
import com.darkness.common.model.UpdateProfileRequest;
import com.darkness.common.model.UserVO;
import com.darkness.common.model.WxBindStatusVO;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.ServiceHelper;
import com.darkness.user.common.util.FileUploadUtil;
import com.darkness.user.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 个人中心服务实现，提供用户信息查询、资料修改、密码修改、手机号修改、头像上传、微信绑定等功能。
 * <p>
 * 通过 UserMapper、RoleMapper、PermissionMapper 等完成用户-角色-权限的级联查询，
 * 通过 SmsCodeMapper 完成验证码校验，通过 WxUserMapper 完成微信绑定管理。
 */
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final WxUserMapper wxUserMapper;
    private final SmsCodeMapper smsCodeMapper;
    private final PasswordUtil passwordUtil;
    private final FileUploadUtil fileUploadUtil;

    /**
     * 获取当前用户完整信息，包括角色编码和权限编码。
     * 通过 sys_user_role → sys_role → sys_role_permission → sys_permission 级联查询。
     *
     * @param userId 当前登录用户 ID
     * @return 用户视图对象（含角色和权限列表）
     */
    @Override
    public UserVO getCurrentUser(Long userId) {
        UserDO user = ServiceHelper.findOrThrow(userMapper.selectById(userId), "User", userId);
        UserVO vo = UserVO.from(user);

        // 查询用户角色
        List<UserRoleDO> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        if (!userRoles.isEmpty()) {
            List<Long> roleIds = userRoles.stream().map(UserRoleDO::getRoleId).toList();
            List<String> roleCodes = roleMapper.selectBatchIds(roleIds).stream()
                    .map(RoleDO::getCode)
                    .toList();
            vo.setRoles(roleCodes);

            // 查询角色对应的权限
            List<RolePermissionDO> rolePermissions = rolePermissionMapper.selectList(
                    new LambdaQueryWrapper<RolePermissionDO>().in(RolePermissionDO::getRoleId, roleIds));
            if (!rolePermissions.isEmpty()) {
                List<Long> permissionIds = rolePermissions.stream()
                        .map(RolePermissionDO::getPermissionId)
                        .distinct()
                        .toList();
                List<String> permissionCodes = permissionMapper.selectBatchIds(permissionIds).stream()
                        .map(PermissionDO::getCode)
                        .toList();
                vo.setPermissions(permissionCodes);
            }
        }

        return vo;
    }

    /**
     * 更新当前用户的个人资料（昵称、邮箱）。仅更新非空字段。
     *
     * @param userId  当前登录用户 ID
     * @param request 资料修改请求（nickname、email 可选）
     * @return 更新后的用户视图对象
     */
    @Override
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        UserDO user = ServiceHelper.findOrThrow(userMapper.selectById(userId), "User", userId);

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }

        userMapper.updateById(user);
        return UserVO.from(user);
    }

    /**
     * 上传用户头像。委托 FileUploadUtil 保存文件，获取 URL 后更新用户 avatar 字段。
     *
     * @param userId 当前登录用户 ID
     * @param file   头像图片文件
     * @return 头像访问 URL
     */
    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        ServiceHelper.findOrThrow(userMapper.selectById(userId), "User", userId);

        String avatarUrl = fileUploadUtil.saveFile(file);

        // 更新用户头像字段
        UserDO update = new UserDO();
        update.setId(userId);
        update.setAvatar(avatarUrl);
        userMapper.updateById(update);

        return avatarUrl;
    }

    /**
     * 修改手机号。校验短信验证码有效性后更新用户手机号。
     * 验证码使用后标记为已使用，防止重复消费。同时校验新手机号不能与其他用户重复。
     *
     * @param userId  当前登录用户 ID
     * @param request 手机号修改请求（newPhone + verifyCode）
     */
    @Override
    public void changePhone(Long userId, ChangePhoneRequest request) {
        UserDO user = ServiceHelper.findOrThrow(userMapper.selectById(userId), "User", userId);

        // 查询该手机号未使用的验证码，按创建时间倒序取最新一条
        SmsCodeDO smsCode = smsCodeMapper.selectOne(
                new LambdaQueryWrapper<SmsCodeDO>()
                        .eq(SmsCodeDO::getPhone, request.getNewPhone())
                        .eq(SmsCodeDO::getCode, request.getVerifyCode())
                        .eq(SmsCodeDO::getUsed, UsedStatus.UNUSED)
                        .orderByDesc(SmsCodeDO::getCreatedAt)
                        .last("LIMIT 1"));

        if (smsCode == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "验证码无效或已过期");
        }

        // 标记验证码已使用，防止重复消费
        smsCode.setUsed(UsedStatus.USED);
        smsCodeMapper.updateById(smsCode);

        // 校验新手机号不能与其他用户重复
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getPhone, request.getNewPhone())
                        .ne(UserDO::getId, userId));
        if (count > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该手机号已被其他用户使用");
        }

        user.setPhone(request.getNewPhone());
        userMapper.updateById(user);
    }

    /**
     * 修改密码。校验新密码与确认密码一致，验证旧密码正确后更新。
     *
     * @param userId  当前登录用户 ID
     * @param request 密码修改请求（oldPassword + newPassword + confirmPassword）
     */
    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        // 新密码与确认密码必须一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码与确认密码不一致");
        }

        UserDO user = ServiceHelper.findOrThrow(userMapper.selectById(userId), "User", userId);

        // 验证旧密码
        if (!passwordUtil.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "旧密码不正确");
        }

        user.setPassword(passwordUtil.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    /**
     * 查询当前用户的微信绑定状态。已绑定时对 openid 做脱敏处理（保留前 2 位和后 2 位，中间用 * 代替）。
     *
     * @param userId 当前登录用户 ID
     * @return 微信绑定状态视图对象
     */
    @Override
    public WxBindStatusVO getWxBindStatus(Long userId) {
        WxUserDO wxUser = wxUserMapper.selectOne(
                new LambdaQueryWrapper<WxUserDO>().eq(WxUserDO::getUserId, userId));

        if (wxUser == null) {
            return new WxBindStatusVO(false, null);
        }

        // 脱敏：保留前 2 位和后 2 位，中间用 * 代替
        String openid = wxUser.getOpenid();
        String masked;
        if (openid != null && openid.length() > 4) {
            masked = openid.substring(0, 2) + "***" + openid.substring(openid.length() - 2);
        } else {
            masked = "***";
        }

        return new WxBindStatusVO(true, masked);
    }

    /**
     * 解除微信绑定。查询 wx_user 表中当前用户的绑定记录，存在则删除，不存在则抛出异常。
     *
     * @param userId 当前登录用户 ID
     */
    @Override
    public void unbindWx(Long userId) {
        WxUserDO wxUser = wxUserMapper.selectOne(
                new LambdaQueryWrapper<WxUserDO>().eq(WxUserDO::getUserId, userId));

        if (wxUser == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "未绑定微信账号");
        }

        wxUserMapper.deleteById(wxUser.getId());
    }
}
