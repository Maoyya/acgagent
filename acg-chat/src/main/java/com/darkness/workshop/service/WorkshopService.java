package com.darkness.workshop.service;

import com.darkness.common.model.CharactersRequest;
import com.darkness.common.model.CharacterVO;
import com.darkness.common.model.PlotRequest;
import com.darkness.common.model.ShotVO;
import com.darkness.common.model.StoryboardRequest;
import com.darkness.common.model.WorkshopPlotStreamEvent;
import com.darkness.common.model.WorkshopProjectRequest;
import com.darkness.common.model.WorkshopProjectVO;
import com.darkness.common.result.Result;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 创作工坊服务：项目 CRUD（owner-only）+ 剧情流式生成 / 分镜 / 角色转发。
 * <p>
 * 项目为每用户私有：create 时归属当前用户；get/update/delete 越权抛 403。
 * 生成方法（plot/storyboard/characters）为纯转发 PythonAiClient，Service 层不做组装或落库。
 * userId 由 Controller 从 UserContext 取后透传，便于纯 Mockito 单测。
 */
public interface WorkshopService {

    /**
     * 创建项目。归属当前用户；title 缺省时取 story 前 32 字（blank 则"新建项目"）；story/plot 等按 req 设。
     *
     * @param req    项目内容（title/story/plot/storyboard/characters 均可选）
     * @param userId 当前用户 id（项目归属）
     * @return 创建后的项目 VO
     */
    WorkshopProjectVO createProject(WorkshopProjectRequest req, Long userId);

    /**
     * 列出当前用户的所有项目，按创建时间倒序。
     *
     * @param userId 当前用户 id
     * @return 项目 VO 列表
     */
    List<WorkshopProjectVO> listProjects(Long userId);

    /**
     * 查询单个项目，做 owner 校验。不存在抛 404，越权抛 403。
     *
     * @param id     项目 id
     * @param userId 当前用户 id（用于越权判定）
     * @return 项目 VO
     */
    WorkshopProjectVO getProject(Long id, Long userId);

    /**
     * 局部更新项目（owner-only）。按 req 非空字段覆盖（title/story/plot/storyboard/characters）。
     * 不存在抛 404，越权抛 403。
     *
     * @param id     项目 id
     * @param req    待更新字段
     * @param userId 当前用户 id
     * @return 更新后的项目 VO
     */
    WorkshopProjectVO updateProject(Long id, WorkshopProjectRequest req, Long userId);

    /**
     * 逻辑删除项目（owner-only）。越权抛 403，不存在抛 404。
     *
     * @param id     项目 id
     * @param userId 当前用户 id
     */
    void deleteProject(Long id, Long userId);

    /**
     * 流式剧情生成（纯转发 Python SSE 流）。直接返回 PythonAiClient 的事件流，不落库、不组装 Result。
     *
     * @param req    剧情请求（story 故事梗概）
     * @param userId 当前用户 id（透传 Python 做审计/限流）
     * @return WorkshopPlotStreamEvent 流（content → ... → done/error）
     */
    Flux<WorkshopPlotStreamEvent> plot(PlotRequest req, Long userId);

    /**
     * 分镜生成（同步，纯转发 Python）。直接委托 PythonAiClient，Service 层不做组装。
     *
     * @param req    分镜请求（plot 剧情正文）
     * @param userId 当前用户 id（透传 Python）
     * @return 200 带分镜列表
     */
    Result<List<ShotVO>> storyboard(StoryboardRequest req, Long userId);

    /**
     * 角色生成（同步，纯转发 Python）。直接委托 PythonAiClient，Service 层不做组装。
     *
     * @param req    角色请求（plot 剧情正文）
     * @param userId 当前用户 id（透传 Python）
     * @return 200 带角色列表
     */
    Result<List<CharacterVO>> characters(CharactersRequest req, Long userId);
}
