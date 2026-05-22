package com.darkness.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信验证码记录实体，映射 sms_code 表。每条记录对应一次验证码发送。
 */
@Data
@TableName("sms_code")
public class SmsCodeDO {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收验证码的手机号 */
    private String phone;

    /** 6 位数字验证码 */
    private String code;

    /** 是否已使用：0-未使用，1-已使用 */
    private Integer used;

    /** 过期时间，超过此时间后验证码失效 */
    private LocalDateTime expiredAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
