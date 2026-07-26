package com.darkness.common.model;

import com.darkness.common.entity.WorkshopProjectDO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创作工坊项目视图对象，对外暴露给前端。
 * <p>DO→VO 经 {@link #from(WorkshopProjectDO)} 工厂转换；JSON 列 storyboard/characters 直接复用 {@link ShotVO}/{@link CharacterVO}。
 */
@Data
public class WorkshopProjectVO {

    /** 项目主键 */
    private Long id;

    /** 归属用户 ID */
    private Long userId;

    /** 项目标题 */
    private String title;

    /** ①故事梗概（用户输入） */
    private String story;

    /** ②剧情（生成的 Markdown） */
    private String plot;

    /** ③分镜数组 */
    private List<ShotVO> storyboard;

    /** ④角色数组（不含 color） */
    private List<CharacterVO> characters;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * DO→VO 转换工厂。entity 为 null 时返回 null，便于 Service 层 {@code from(mapper.selectById(id))} 直链调用。
     */
    public static WorkshopProjectVO from(WorkshopProjectDO entity) {
        if (entity == null) return null;
        WorkshopProjectVO vo = new WorkshopProjectVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setTitle(entity.getTitle());
        vo.setStory(entity.getStory());
        vo.setPlot(entity.getPlot());
        vo.setStoryboard(entity.getStoryboard());
        vo.setCharacters(entity.getCharacters());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
