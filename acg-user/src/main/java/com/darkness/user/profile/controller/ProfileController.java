package com.darkness.user.profile.controller;

import com.darkness.common.model.ChangePasswordRequest;
import com.darkness.common.model.ChangePhoneRequest;
import com.darkness.common.model.UpdateProfileRequest;
import com.darkness.common.model.UserVO;
import com.darkness.common.model.WxBindStatusVO;
import com.darkness.common.result.Result;
import com.darkness.common.util.UserContext;
import com.darkness.user.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心控制器，提供当前用户的资料查询和修改功能。
 * 所有接口需认证，无角色限制。用户 ID 通过 Gateway 传递的 X-User-Id 请求头获取。
 */
@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * 获取当前登录用户完整信息（含角色和权限列表）。
     * GET /api/user/profile/me（需认证）
     *
     * @return 当前用户视图对象（含角色编码和权限编码）
     */
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        return Result.success(profileService.getCurrentUser(UserContext.getUserId()));
    }

    /**
     * 更新当前用户的个人资料（昵称、邮箱），仅更新传入的非空字段。
     * PUT /api/user/profile/me/profile（需认证）
     *
     * @param request 资料修改请求（nickname、email 可选）
     * @return 更新后的用户视图对象
     */
    @PutMapping("/me/profile")
    public Result<UserVO> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        return Result.success(profileService.updateProfile(UserContext.getUserId(), request));
    }

    /**
     * 上传用户头像图片。支持 JPEG、PNG、GIF、WebP 格式，最大 2MB。
     * PUT /api/user/profile/me/avatar（需认证）
     *
     * @param file 头像图片文件，不能为空
     * @return 头像访问 URL
     */
    @PutMapping("/me/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(profileService.uploadAvatar(UserContext.getUserId(), file));
    }

    /**
     * 修改手机号。需提供新手机号和对应的短信验证码。
     * PUT /api/user/profile/me/phone（需认证）
     *
     * @param request 手机号修改请求（newPhone + verifyCode，均必填）
     */
    @PutMapping("/me/phone")
    public Result<Void> changePhone(@RequestBody @Valid ChangePhoneRequest request) {
        profileService.changePhone(UserContext.getUserId(), request);
        return Result.success();
    }

    /**
     * 修改密码。需提供旧密码验证身份，新密码需两次输入确认一致。
     * PUT /api/user/profile/me/password（需认证）
     *
     * @param request 密码修改请求（oldPassword + newPassword + confirmPassword，均必填）
     */
    @PutMapping("/me/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        profileService.changePassword(UserContext.getUserId(), request);
        return Result.success();
    }

    /**
     * 查询当前用户的微信绑定状态。已绑定时返回脱敏后的 openid。
     * GET /api/user/profile/me/wx-status（需认证）
     *
     * @return 微信绑定状态视图对象（bound + 脱敏 openid）
     */
    @GetMapping("/me/wx-status")
    public Result<WxBindStatusVO> getWxBindStatus() {
        return Result.success(profileService.getWxBindStatus(UserContext.getUserId()));
    }

    /**
     * 解除微信绑定。未绑定时返回 400 错误。
     * POST /api/user/profile/me/wx-unbind（需认证）
     */
    @PostMapping("/me/wx-unbind")
    public Result<Void> unbindWx() {
        profileService.unbindWx(UserContext.getUserId());
        return Result.success();
    }
}
