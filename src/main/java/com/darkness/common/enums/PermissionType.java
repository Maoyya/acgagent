package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 权限类型枚举，用于 PermissionDO 的 type 字段。
 */
@Getter
@AllArgsConstructor
public enum PermissionType {

    MENU(1, "菜单"),
    BUTTON(2, "按钮");

    @EnumValue
    @JsonValue
    private final int value;
    private final String description;

    @JsonCreator
    public static PermissionType fromValue(int value) {
        for (PermissionType t : values()) {
            if (t.value == value) return t;
        }
        throw new IllegalArgumentException("Unknown PermissionType value: " + value);
    }
}
