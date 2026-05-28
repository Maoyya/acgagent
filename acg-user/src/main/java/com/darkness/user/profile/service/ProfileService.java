package com.darkness.user.profile.service;

import com.darkness.common.model.ChangePasswordRequest;
import com.darkness.common.model.ChangePhoneRequest;
import com.darkness.common.model.UpdateProfileRequest;
import com.darkness.common.model.UserVO;
import com.darkness.common.model.WxBindStatusVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心服务，提供用户信息查询、资料修改、密码修改、手机号修改、头像上传、微信绑定等功能。
 */
public interface ProfileService {

    /**
     * 获取当前用户完整信息，包括角色编码和权限编码。
     *
     * @param userId 当前登录用户 ID
     * @return 用户视图对象（含角色和权限列表）
     */
    UserVO getCurrentUser(Long userId);

    /**
     * 更新当前用户的个人资料（昵称、邮箱）。仅更新非空字段。
     *
     * @param userId  当前登录用户 ID
     * @param request 资料修改请求（nickname、email 可选）
     * @return 更新后的用户视图对象
     */
    UserVO updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * 上传用户头像图片，保存到本地文件系统并返回访问 URL。
     * 支持格式：JPEG、PNG、GIF、WebP，最大 2MB。
     *
     * @param userId 当前登录用户 ID
     * @param file   头像图片文件
     * @return 头像访问 URL
     */
    String uploadAvatar(Long userId, MultipartFile file);

    /**
     * 修改手机号。需验证短信验证码，验证通过后更新用户手机号。
     * 验证码验证后标记为已使用，防止重复消费。
     *
     * @param userId  当前登录用户 ID
     * @param request 手机号修改请求（newPhone + verifyCode）
     */
    void changePhone(Long userId, ChangePhoneRequest request);

    /**
     * 修改密码。需验证旧密码，新密码与确认密码必须一致。
     *
     * @param userId  当前登录用户 ID
     * @param request 密码修改请求（oldPassword + newPassword + confirmPassword）
     */
    void changePassword(Long userId, ChangePasswordRequest request);

    /**
     * 查询当前用户的微信绑定状态。已绑定时返回脱敏后的 openid（保留前 2 位和后 2 位）。
     *
     * @param userId 当前登录用户 ID
     * @return 微信绑定状态视图对象
     */
    WxBindStatusVO getWxBindStatus(Long userId);

    /**
     * 解除微信绑定。未绑定时抛出 BizException(BAD_REQUEST)。
     *
     * @param userId 当前登录用户 ID
     */
    void unbindWx(Long userId);
}
