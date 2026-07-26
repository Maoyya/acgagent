package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 分镜视图对象，镜像 Python acgagent-ai {@code Shot} schema，作为 workshop_project.storyboard JSON 数组元素。
 * <p>字段名与 Python 端一致（无大小写差），无需 {@code @JsonAlias}；
 * 加 {@code @JsonIgnoreProperties(ignoreUnknown=true)} 防止多余字段反序列化失败。
 * 前端配色不在此 VO（由前端决定）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShotVO {

    /** 景别：远景/中景/近景/特写 */
    private String shot;

    /** 镜头描述（画面内容） */
    private String description;

    /** 时长，如 "3s"、"2.5s" */
    private String duration;

    /** 运镜方式，如 "特写"、"推镜"、"固定" */
    private String movement;

    /** 台词/旁白（可为空） */
    private String dialogue;
}
