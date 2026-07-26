package com.darkness.common.model;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创作工坊项目创建/局部更新请求。所有字段可选——支持局部更新（哪个字段传了就更新哪个）。
 * <p>创建时由 Service 校验：title 缺省时取 story 前 32 字。
 */
@Data
public class WorkshopProjectRequest {

    /** 项目标题，默认取故事前 32 字；可选 */
    @Size(max = 128, message = "项目标题长度不能超过128")
    private String title;

    /** ①故事梗概（用户输入）；可选 */
    private String story;

    /** ②剧情（生成的 Markdown）；可选 */
    private String plot;

    /** ③分镜数组；可选 */
    private List<ShotVO> storyboard;

    /** ④角色数组（不含 color）；可选 */
    private List<CharacterVO> characters;
}
