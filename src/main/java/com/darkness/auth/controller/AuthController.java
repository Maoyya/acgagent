package com.darkness.auth.controller;

import com.darkness.auth.model.TokenVO;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.service.SmsService;
import com.darkness.auth.service.WxAuthService;
import com.darkness.common.result.Result;
import com.darkness.user.model.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器，提供账号密码注册/登录、令牌刷新、短信验证码登录和微信扫码登录入口。
 * 所有接口路径以 /api/auth 为前缀，返回值统一使用 {@link Result} 包装。
 * 请求体均为 JSON 格式（application/json），通过 Map 接收键值对参数。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SmsService smsService;
    private final WxAuthService wxAuthService;

    /**
     * 账号密码注册。
     * 请求体需包含 username（用户名）和 password（明文密码），不能为空。
     * 服务端校验用户名唯一性后使用 BCrypt 加密密码并创建用户。
     *
     * @param body 包含 username 和 password 的 JSON 对象
     * @return 注册成功的用户信息（UserVO）
     */
    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody Map<String, String> body) {
        return Result.success(authService.register(body.get("username"), body.get("password")));
    }

    /**
     * 账号密码登录。
     * 请求体需包含 username 和 password。校验通过后返回 accessToken 和 refreshToken。
     *
     * @param body 包含 username 和 password 的 JSON 对象
     * @return Token 对，包含 accessToken、refreshToken 和 expiresIn
     */
    @PostMapping("/login")
    public Result<TokenVO> login(@RequestBody Map<String, String> body) {
        return Result.success(authService.login(body.get("username"), body.get("password")));
    }

    /**
     * 使用 refreshToken 刷新令牌。
     * 请求体需包含 refreshToken，服务端校验其有效性和类型后重新签发 Token 对。
     *
     * @param body 包含 refreshToken 的 JSON 对象
     * @return 新的 Token 对
     */
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@RequestBody Map<String, String> body) {
        return Result.success(authService.refresh(body.get("refreshToken")));
    }

    /**
     * 发送短信验证码。
     * 请求体需包含 phone（手机号）。服务端生成 6 位验证码并发送，
     * mock 模式下仅输出到日志。成功返回空数据。
     *
     * @param body 包含 phone（手机号）的 JSON 对象
     * @return 空结果
     */
    @PostMapping("/sms/send")
    public Result<Void> sendSmsCode(@RequestBody Map<String, String> body) {
        smsService.sendCode(body.get("phone"));
        return Result.success();
    }

    /**
     * 短信验证码登录。
     * 请求体需包含 phone 和 code。校验验证码有效后登录，
     * 手机号未注册时自动创建账号。返回 Token 对。
     *
     * @param body 包含 phone 和 code 的 JSON 对象
     * @return Token 对
     */
    @PostMapping("/sms/login")
    public Result<TokenVO> smsLogin(@RequestBody Map<String, String> body) {
        return Result.success(smsService.login(body.get("phone"), body.get("code")));
    }

    /**
     * 获取微信扫码登录二维码 URL。
     * 无需请求参数。返回微信 OAuth2 授权链接和防 CSRF 的 state 参数。
     *
     * @return 包含 url（二维码地址）和 state（防 CSRF 随机串）
     */
    @PostMapping("/wx/qrcode")
    public Result<Map<String, String>> wxQrcode() {
        return Result.success(wxAuthService.generateQrcode());
    }

    /**
     * 微信扫码回调处理。
     * 请求体需包含 code（微信返回的授权码）。服务端用授权码换取 access_token 和 openid，
     * 未绑定时自动注册系统用户。返回 Token 对。
     *
     * @param body 包含 code（微信返回的授权码）的 JSON 对象
     * @return Token 对
     */
    @PostMapping("/wx/callback")
    public Result<TokenVO> wxCallback(@RequestBody Map<String, String> body) {
        return Result.success(wxAuthService.handleCallback(body.get("code")));
    }
}
