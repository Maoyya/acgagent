package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统提示词生成模式枚举。
 * <p>ACG-拥抱二次元/动漫风格（仅挡暴力违法/超能力）；COMPLIANT-中性专业，额外限制二次元风格。
 * 序列化与 DB 存储均为小写 value，匹配 Python PromptMode(str, Enum)。
 */
@Getter
@AllArgsConstructor
public enum PromptMode {

    ACG("acg"),
    COMPLIANT("compliant");

    /** 序列化(@JsonValue)与 DB 存储(@EnumValue)值 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 反序列化：大小写不敏感；非法值抛 IllegalArgumentException（Controller 绑定时转 400）。 */
    @JsonCreator
    public static PromptMode fromValue(String value) {
        for (PromptMode m : values()) {
            if (m.value.equalsIgnoreCase(value)) return m;
        }
        throw new IllegalArgumentException("非法的 PromptMode 值: " + value);
    }
}
