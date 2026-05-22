package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 聊天消息角色枚举，用于 MessageDO 的 role 字段。
 */
@Getter
@AllArgsConstructor
public enum MessageRole {

    USER("user"),
    ASSISTANT("assistant");

    @EnumValue
    @JsonValue
    private final String value;

    @JsonCreator
    public static MessageRole fromValue(String value) {
        for (MessageRole r : values()) {
            if (r.value.equals(value)) return r;
        }
        throw new IllegalArgumentException("Unknown MessageRole value: " + value);
    }
}
