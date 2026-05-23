package com.darkness.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.service.SmsService;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.UsedStatus;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.SmsCodeMapper;
import com.darkness.common.mapper.UserMapper;
import com.darkness.common.model.TokenVO;
import com.darkness.common.result.ResultCode;
import com.darkness.common.entity.SmsCodeDO;
import com.darkness.common.entity.UserDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 短信验证码服务实现，包含发送验证码和验证码登录逻辑。
 * <p>
 * 验证码有效期为 5 分钟，登录时若手机号未注册则自动创建账号。
 * 通过 sms.provider 配置项切换短信发送方式：mock（默认，仅打印日志）或真实短信网关。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {

    private final SmsCodeMapper smsCodeMapper;
    private final UserMapper userMapper;
    private final AuthService authService;

    /** 短信发送提供者，取值：mock（默认，仅打印日志）或真实短信网关标识 */
    @Value("${sms.provider:mock}")
    private String smsProvider;

    /**
     * 向指定手机号发送 6 位随机数字验证码。
     * 生成验证码后写入 sms_code 表（used=UNUSED，expiredAt=当前时间+5分钟），
     * mock 模式下仅输出到日志，方便本地开发调试。
     *
     * @param phone 手机号，不能为空
     */
    @Override
    public void sendCode(String phone) {
        if (phone == null || phone.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, "Phone number is required");

        String code = String.format("%06d", new Random().nextInt(1000000));

        SmsCodeDO smsCode = new SmsCodeDO();
        smsCode.setPhone(phone);
        smsCode.setCode(code);
        smsCode.setUsed(UsedStatus.UNUSED);
        // 验证码 5 分钟后过期
        smsCode.setExpiredAt(LocalDateTime.now().plusMinutes(5));
        smsCodeMapper.insert(smsCode);

        // mock 模式下直接打印验证码到日志，方便开发调试
        if ("mock".equals(smsProvider)) {
            log.info("[Mock SMS] Phone: {}, Code: {}", phone, code);
        } else {
            log.info("[SMS] Sending code to phone: {}", phone);
        }
    }

    /**
     * 短信验证码登录。
     * 查询该手机号未使用且未过期的验证码记录，
     * 验证码无效或已过期时抛出 BizException(401)。验证通过后标记 used=USED 防止重复消费。
     * 若手机号未注册则自动创建用户（昵称取 "user_" + 手机号后四位），最后签发 Token 对。
     *
     * @param phone 手机号
     * @param code  6 位数字验证码
     * @return accessToken 和 refreshToken
     */
    @Override
    public TokenVO login(String phone, String code) {
        if (phone == null || code == null) throw new BizException(ResultCode.BAD_REQUEST, "Phone and code are required");

        // 查询未使用且未过期的验证码
        SmsCodeDO smsCode = smsCodeMapper.selectOne(
                new LambdaQueryWrapper<SmsCodeDO>()
                        .eq(SmsCodeDO::getPhone, phone)
                        .eq(SmsCodeDO::getCode, code)
                        .eq(SmsCodeDO::getUsed, UsedStatus.UNUSED)
                        .gt(SmsCodeDO::getExpiredAt, LocalDateTime.now())
                        .orderByDesc(SmsCodeDO::getCreatedAt)
                        .last("LIMIT 1"));

        if (smsCode == null) throw new BizException(ResultCode.UNAUTHORIZED, "Invalid or expired verification code");

        // 标记验证码已使用，防止重复使用
        smsCodeMapper.update(null,
                new LambdaUpdateWrapper<SmsCodeDO>().eq(SmsCodeDO::getId, smsCode.getId()).set(SmsCodeDO::getUsed, UsedStatus.USED));

        UserDO user = userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getPhone, phone));
        // 手机号未注册时自动创建账号，昵称取手机号后四位拼接
        if (user == null) {
            user = new UserDO();
            user.setPhone(phone);
            user.setNickname("user_" + phone.substring(Math.max(0, phone.length() - 4)));
            user.setStatus(CommonStatus.ENABLED);
            userMapper.insert(user);
        }

        return authService.generateTokenPair(user.getId());
    }
}
