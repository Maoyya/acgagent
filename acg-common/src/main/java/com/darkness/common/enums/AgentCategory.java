package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 分类枚举，区分对话、视频、生图等不同类型。
 */
@Getter
@AllArgsConstructor
public enum AgentCategory {

    CHAT("CHAT", "对话"),
    VIDEO("VIDEO", "视频"),
    IMAGE("IMAGE", "生图");

    @EnumValue
    @JsonValue
    private final String value;
    private final String description;

    @JsonCreator
    public static AgentCategory fromValue(String value) {
        for (AgentCategory c : values()) {
            if (c.value.equals(value)) {
                return c;
            }
        }
        return CHAT;
    }
}
