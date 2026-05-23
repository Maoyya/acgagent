package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用启用/禁用状态枚举，用于 User、Role、Permission、Agent 的 status 字段。
 */
@Getter
@AllArgsConstructor
public enum CommonStatus {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    @EnumValue
    @JsonValue
    private final int value;
    private final String description;

    @JsonCreator
    public static CommonStatus fromValue(int value) {
        for (CommonStatus s : values()) {
            if (s.value == value) return s;
        }
        throw new IllegalArgumentException("Unknown CommonStatus value: " + value);
    }
}
