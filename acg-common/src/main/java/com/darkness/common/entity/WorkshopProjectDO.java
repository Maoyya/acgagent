package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.darkness.common.model.CharacterVO;
import com.darkness.common.model.ShotVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 创作工坊项目实体，映射 workshop_project 表（每用户私有）。
 * <p>storyboard/characters 为 JSON 列，经 JacksonTypeHandler 映射为 {@code List<ShotVO>}/{@code List<CharacterVO>}。
 * 镜像 {@link PromptTemplateDO} 的 JSON 列处理模式（{@code @TableName(autoResultMap=true)} + {@code JacksonTypeHandler}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "workshop_project", autoResultMap = true)
public class WorkshopProjectDO extends BaseEntity {

    /** 归属用户 ID */
    private Long userId;

    /** 项目标题，默认取故事前 32 字，可改 */
    private String title;

    /** ①故事梗概（用户输入） */
    private String story;

    /** ②剧情（生成的 Markdown 正文） */
    private String plot;

    /** ③分镜数组（JSON 列），元素为 {@link ShotVO} */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ShotVO> storyboard;

    /** ④角色数组（JSON 列，不含 color——前端配色），元素为 {@link CharacterVO} */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<CharacterVO> characters;
}
