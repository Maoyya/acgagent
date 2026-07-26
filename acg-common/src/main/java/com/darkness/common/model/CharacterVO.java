package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 角色视图对象，镜像 Python acgagent-ai {@code Character} schema，作为 workshop_project.characters JSON 数组元素。
 * <p>字段名与 Python 端一致（无大小写差），无需 {@code @JsonAlias}；
 * 加 {@code @JsonIgnoreProperties(ignoreUnknown=true)} 防止多余字段反序列化失败。
 * <b>不含 color 字段</b>——前端负责配色，避免后端硬编码。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CharacterVO {

    /** 角色名 */
    private String name;

    /** 角色定位，如 "主角"、"配角"、"反派" */
    private String role;

    /** 角色描述（外貌、性格、关键设定） */
    private String description;
}
