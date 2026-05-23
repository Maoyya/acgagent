package com.darkness.auth.service;

import com.darkness.common.model.TokenVO;

import java.util.Map;

/**
 * 微信扫码登录服务接口，提供二维码生成和 OAuth2 回调处理。
 */
public interface WxAuthService {

    /**
     * 生成微信扫码登录二维码 URL。
     * 生成随机 state 参数用于防 CSRF 攻击，拼接微信 OAuth2 授权链接。
     * 前端使用返回的 URL 渲染二维码，state 用于回调时校验请求合法性。
     *
     * @return 包含 url（二维码地址）和 state（防 CSRF 随机串）
     */
    Map<String, String> generateQrcode();

    /**
     * 处理微信扫码回调。
     * 使用授权码调用微信 OAuth2 接口换取 access_token 和 openid，
     * 获取微信用户信息（昵称、头像）。
     * 若 openid 未绑定系统用户则自动注册新用户并建立 wx_user 绑定关系，
     * 最后生成 Token 对返回。
     *
     * @param code 微信返回的授权码
     * @return 令牌对
     */
    TokenVO handleCallback(String code);
}
