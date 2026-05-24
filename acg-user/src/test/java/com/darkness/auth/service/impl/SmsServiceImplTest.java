package com.darkness.auth.service.impl;

import com.darkness.auth.service.AuthService;
import com.darkness.common.entity.SmsCodeDO;
import com.darkness.common.entity.UserDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.UsedStatus;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.SmsCodeMapper;
import com.darkness.common.mapper.UserMapper;
import com.darkness.common.model.TokenVO;
import com.darkness.common.result.ResultCode;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 短信验证码服务单元测试，覆盖发送验证码和验证码登录逻辑。
 * 核心验证点：验证码 5 分钟有效、一次性使用、手机号未注册时自动注册。
 */
@ExtendWith(MockitoExtension.class)
class SmsServiceImplTest {

    @Mock
    private SmsCodeMapper smsCodeMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuthService authService;

    @InjectMocks
    private SmsServiceImpl smsService;

    /** 初始化 MyBatis-Plus lambda cache，否则 LambdaUpdateWrapper 会抛异常 */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        var assistant = new MapperBuilderAssistant(new Configuration(), "");
        if (TableInfoHelper.getTableInfo(SmsCodeDO.class) == null) {
            TableInfoHelper.initTableInfo(assistant, SmsCodeDO.class);
        }
    }

    // ==================== sendCode ====================

    @Test
    void sendCode_success() {
        when(smsCodeMapper.insert(any(SmsCodeDO.class))).thenReturn(1);
        ReflectionTestUtils.setField(smsService, "smsProvider", "mock");

        smsService.sendCode("13800138000");

        var captor = org.mockito.ArgumentCaptor.forClass(SmsCodeDO.class);
        verify(smsCodeMapper).insert(captor.capture());
        SmsCodeDO captured = captor.getValue();
        assertThat(captured.getPhone()).isEqualTo("13800138000");
        assertThat(captured.getUsed()).isEqualTo(UsedStatus.UNUSED);
        assertThat(captured.getExpiredAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void sendCode_emptyPhone_throwsBadRequest() {
        assertThatThrownBy(() -> smsService.sendCode(""))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    @Test
    void sendCode_nullPhone_throwsBadRequest() {
        assertThatThrownBy(() -> smsService.sendCode(null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    // ==================== login ====================

    @Test
    void login_existingUser_success() {
        // 模拟有效的验证码记录
        SmsCodeDO smsCode = new SmsCodeDO();
        smsCode.setId(1L);
        smsCode.setPhone("13800138000");
        smsCode.setCode("123456");
        smsCode.setUsed(UsedStatus.UNUSED);
        smsCode.setExpiredAt(LocalDateTime.now().plusMinutes(5));
        when(smsCodeMapper.selectOne(any())).thenReturn(smsCode);
        when(smsCodeMapper.update(any(), any())).thenReturn(1);

        // 模拟已注册用户
        UserDO user = new UserDO();
        user.setId(1L);
        user.setPhone("13800138000");
        when(userMapper.selectOne(any())).thenReturn(user);

        TokenVO tokenVO = new TokenVO("access", "refresh", 7200L);
        when(authService.generateTokenPair(1L)).thenReturn(tokenVO);

        TokenVO result = smsService.login("13800138000", "123456");

        assertThat(result.getAccessToken()).isEqualTo("access");
        // 验证码被标记为已使用
        verify(smsCodeMapper).update(any(), any());
    }

    @Test
    void login_autoRegister_whenPhoneNotExists() {
        SmsCodeDO smsCode = new SmsCodeDO();
        smsCode.setId(1L);
        smsCode.setPhone("13800138001");
        smsCode.setUsed(UsedStatus.UNUSED);
        smsCode.setExpiredAt(LocalDateTime.now().plusMinutes(5));
        when(smsCodeMapper.selectOne(any())).thenReturn(smsCode);
        when(smsCodeMapper.update(any(), any())).thenReturn(1);

        // 手机号未注册，返回 null 触发自动注册
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            UserDO user = invocation.getArgument(0);
            user.setId(2L);
            return 1;
        });

        TokenVO tokenVO = new TokenVO("access", "refresh", 7200L);
        when(authService.generateTokenPair(2L)).thenReturn(tokenVO);

        TokenVO result = smsService.login("13800138001", "123456");

        assertThat(result).isNotNull();
        // 验证自动注册：insert 被调用且字段正确
        var userCaptor = org.mockito.ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).insert(userCaptor.capture());
        UserDO registered = userCaptor.getValue();
        assertThat(registered.getPhone()).isEqualTo("13800138001");
        assertThat(registered.getStatus()).isEqualTo(CommonStatus.ENABLED);
    }

    @Test
    void login_invalidCode_throwsUnauthorized() {
        when(smsCodeMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> smsService.login("13800138000", "000000"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void login_nullParams_throwsBadRequest() {
        assertThatThrownBy(() -> smsService.login(null, "123456"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.BAD_REQUEST.getCode());

        assertThatThrownBy(() -> smsService.login("13800138000", null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }
}
