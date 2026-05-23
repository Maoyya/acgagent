package com.darkness.auth.constant;

/**
 * 微信开放平台 API 相关常量。
 */
public final class WxApiConstants {
    private WxApiConstants() {}

    /** 微信授权码 grant type。 */
    public static final String GRANT_TYPE_AUTH_CODE = "authorization_code";

    /** 微信 JSON 响应字段名。 */
    public static final String FIELD_ERRCODE = "errcode";
    public static final String FIELD_ERRMSG = "errmsg";
    public static final String FIELD_OPENID = "openid";
    public static final String FIELD_ACCESS_TOKEN = "access_token";
    public static final String FIELD_NICKNAME = "nickname";
    public static final String FIELD_HEADIMGURL = "headimgurl";
}
