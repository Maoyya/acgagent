package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 验证码使用状态枚举，用于 SmsCodeDO 的 used 字段。
 */
@Getter
@AllArgsConstructor
public enum UsedStatus {

    UNUSED(0, "未使用"),
    USED(1, "已使用");

    @EnumValue
    @JsonValue
    private final int value;
    private final String description;

    @JsonCreator
    public static UsedStatus fromValue(int value) {
        for (UsedStatus s : values()) {
            if (s.value == value) return s;
        }
        throw new IllegalArgumentException("Unknown UsedStatus value: " + value);
    }
}
