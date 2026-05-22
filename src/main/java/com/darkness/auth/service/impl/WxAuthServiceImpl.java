package com.darkness.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.auth.entity.WxUserDO;
import com.darkness.auth.mapper.WxUserMapper;
import com.darkness.auth.model.TokenVO;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.service.WxAuthService;
import com.darkness.common.exception.BizException;
import com.darkness.config.WxConfig;
import com.darkness.user.entity.UserDO;
import com.darkness.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信扫码登录服务实现。
 * 流程：生成二维码 URL -> 用户扫码 -> 回调获取 access_token -> 查询/创建用户及绑定关系 -> 签发 JWT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAuthServiceImpl implements WxAuthService {

    private final WxConfig wxConfig;
    private final WxUserMapper wxUserMapper;
    private final UserMapper userMapper;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    /**
     * 生成微信扫码登录二维码 URL。
     * 生成 UUID 作为 state 参数用于防 CSRF 攻击，拼接微信 OAuth2 授权链接
     * （appid + snsapi_login + state），返回 URL 和 state 供前端渲染二维码。
     *
     * @return 包含 url（二维码地址）和 state（防 CSRF 随机串）的 Map
     */
    @Override
    public Map<String, String> generateQrcode() {
        // state 用于防 CSRF 攻击，回调时需校验一致性
        String state = UUID.randomUUID().toString().replace("-", "");
        String url = String.format(
                "https://open.weixin.qq.com/connect/qrconnect?appid=%s&redirect_uri=&response_type=code&scope=snsapi_login&state=%s",
                wxConfig.getAppId(), state);
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        result.put("state", state);
        return result;
    }

    /**
     * 处理微信扫码回调。
     * 使用授权码调用微信 OAuth2 接口换取 access_token 和 openid（失败时抛出 BizException(400)），
     * 再用 access_token 拉取微信用户信息（昵称、头像）。
     * 查询 wx_user 表：若 openid 已绑定则直接获取关联的系统 userId；
     * 若未绑定则自动创建系统用户 + 建立 wx_user 绑定关系，最后签发 Token 对。
     *
     * @param code 微信返回的授权码，不能为空
     * @return 令牌对（accessToken + refreshToken）
     */
    @Override
    public TokenVO handleCallback(String code) {
        if (code == null || code.isBlank()) throw new BizException(400, "Authorization code is required");

        // 用授权码向微信服务器换取 access_token 和 openid
        String tokenUrl = String.format(
                "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
                wxConfig.getAppId(), wxConfig.getAppSecret(), code);

        try {
            RestClient restClient = RestClient.create();
            String response = restClient.get().uri(tokenUrl).retrieve().body(String.class);
            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode")) {
                throw new BizException(400, "WeChat auth failed: " + json.get("errmsg").asText());
            }

            String openid = json.get("openid").asText();
            String accessToken = json.get("access_token").asText();

            // 用 access_token 拉取微信用户信息（昵称、头像）
            String userInfoUrl = String.format(
                    "https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s",
                    accessToken, openid);
            String userInfoResponse = restClient.get().uri(userInfoUrl).retrieve().body(String.class);
            JsonNode userInfo = objectMapper.readTree(userInfoResponse);

            // 查询已有的微信绑定关系
            WxUserDO wxUser = wxUserMapper.selectOne(
                    new LambdaQueryWrapper<WxUserDO>().eq(WxUserDO::getOpenid, openid));

            Long userId;
            if (wxUser != null) {
                // 已绑定，直接使用关联的系统用户
                userId = wxUser.getUserId();
            } else {
                // 未绑定：先创建系统用户，再建立微信绑定关系
                String nickname = userInfo.has("nickname") ? userInfo.get("nickname").asText() : "wx_user";
                String avatar = userInfo.has("headimgurl") ? userInfo.get("headimgurl").asText() : null;

                UserDO user = new UserDO();
                user.setNickname(nickname);
                user.setAvatar(avatar);
                user.setStatus(1);
                userMapper.insert(user);
                userId = user.getId();

                wxUser = new WxUserDO();
                wxUser.setOpenid(openid);
                wxUser.setUserId(userId);
                wxUser.setNickname(nickname);
                wxUser.setAvatarUrl(avatar);
                wxUserMapper.insert(wxUser);
            }

            return authService.generateTokenPair(userId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("WeChat auth error", e);
            throw new BizException(500, "WeChat authentication failed");
        }
    }
}
