package com.darkness.auth.service;

import com.darkness.common.model.TokenVO;

/**
 * 短信验证码服务接口，提供验证码发送与短信验证码登录能力。
 */
public interface SmsService {

    /**
     * 向指定手机号发送验证码。
     * 生成 6 位随机数字验证码，存入 sms_code 表（5 分钟有效），使用后标记为已使用。
     * 当 sms.provider 配置为 mock 时仅输出到日志，方便本地开发调试。
     *
     * @param phone 手机号，不能为空
     */
    void sendCode(String phone);

    /**
     * 短信验证码登录。
     * 校验验证码未使用且未过期，标记已使用防止重复消费。
     * 若手机号未注册则自动创建新用户（昵称取手机号后四位），然后生成 Token 对返回。
     *
     * @param phone 手机号
     * @param code  6 位数字验证码
     * @return accessToken 和 refreshToken
     */
    TokenVO login(String phone, String code);
}
