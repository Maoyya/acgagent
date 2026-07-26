package com.darkness.workshop.controller;

import com.darkness.common.model.CharactersRequest;
import com.darkness.common.model.CharacterVO;
import com.darkness.common.model.PlotRequest;
import com.darkness.common.model.ShotVO;
import com.darkness.common.model.StoryboardRequest;
import com.darkness.common.model.WorkshopPlotStreamEvent;
import com.darkness.common.model.WorkshopProjectRequest;
import com.darkness.common.model.WorkshopProjectVO;
import com.darkness.common.result.Result;
import com.darkness.common.util.UserContext;
import com.darkness.workshop.service.WorkshopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 创作工坊 REST 控制器：项目 CRUD（owner-only）+ 剧情流式生成 / 分镜 / 角色同步生成。
 * userId 由 {@link UserContext#getUserId()} 取后透传 Service；越权/不存在由 Service 抛 403/404，
 * 经 {@code GlobalExceptionHandler} 统一转 Result。
 */
@RestController
@RequestMapping("/api/workshop")
@RequiredArgsConstructor
public class WorkshopController {

    private final WorkshopService workshopService;

    // ===== 项目 CRUD =====

    /**
     * 创建项目，归属当前用户。title 缺省时由 Service 取 story 前 32 字。
     * POST /api/workshop/projects（需登录）
     *
     * @param req 项目内容（title/story/plot/storyboard/characters 均可选）
     * @return 创建后的项目视图对象
     */
    @PostMapping("/projects")
    public Result<WorkshopProjectVO> create(@Valid @RequestBody WorkshopProjectRequest req) {
        return Result.success(workshopService.createProject(req, UserContext.getUserId()));
    }

    /**
     * 列出当前用户的所有项目，按创建时间倒序。
     * GET /api/workshop/projects（需登录）
     *
     * @return 当前用户名下的项目视图对象列表
     */
    @GetMapping("/projects")
    public Result<List<WorkshopProjectVO>> list() {
        return Result.success(workshopService.listProjects(UserContext.getUserId()));
    }

    /**
     * 查询单个项目（owner-only）。不存在抛 404，越权抛 403。
     * GET /api/workshop/projects/{id}（需登录）
     *
     * @param id 项目主键
     * @return 项目视图对象
     */
    @GetMapping("/projects/{id}")
    public Result<WorkshopProjectVO> get(@PathVariable Long id) {
        return Result.success(workshopService.getProject(id, UserContext.getUserId()));
    }

    /**
     * 局部更新项目（owner-only）。按 req 非空字段覆盖（title/story/plot/storyboard/characters）。
     * 不存在抛 404，越权抛 403。
     * PUT /api/workshop/projects/{id}（需登录）
     *
     * @param id  项目主键
     * @param req 待更新字段
     * @return 更新后的项目视图对象
     */
    @PutMapping("/projects/{id}")
    public Result<WorkshopProjectVO> update(@PathVariable Long id, @Valid @RequestBody WorkshopProjectRequest req) {
        return Result.success(workshopService.updateProject(id, req, UserContext.getUserId()));
    }

    /**
     * 逻辑删除项目（owner-only）。越权抛 403，不存在抛 404。
     * DELETE /api/workshop/projects/{id}（需登录）
     *
     * @param id 项目主键
     * @return 无 data 的成功响应
     */
    @DeleteMapping("/projects/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workshopService.deleteProject(id, UserContext.getUserId());
        return Result.success();
    }

    // ===== 生成（无状态，前端拿到结果后 PUT 落库）=====

    /**
     * 流式剧情生成（直接转发 Python SSE 流）。不落库、不组装 Result。
     * content 事件流式 token，done 事件收尾，error 事件带错误信息。
     * POST /api/workshop/plot（需登录），produces text/event-stream
     *
     * @param req 剧情请求（story 故事梗概）
     * @return WorkshopPlotStreamEvent 流（content → ... → done/error）
     */
    @PostMapping(value = "/plot", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<WorkshopPlotStreamEvent> plot(@Valid @RequestBody PlotRequest req) {
        return workshopService.plot(req, UserContext.getUserId());
    }

    /**
     * 分镜生成（同步，纯转发 Python）。不落库，前端拿到结果后由 PUT /projects/{id} 选择性落库。
     * POST /api/workshop/storyboard（需登录）
     *
     * @param req 分镜请求（plot 剧情正文）
     * @return 200 带分镜视图对象列表
     */
    @PostMapping("/storyboard")
    public Result<List<ShotVO>> storyboard(@Valid @RequestBody StoryboardRequest req) {
        return workshopService.storyboard(req, UserContext.getUserId());
    }

    /**
     * 角色生成（同步，纯转发 Python）。不落库，前端拿到结果后由 PUT /projects/{id} 选择性落库。
     * POST /api/workshop/characters（需登录）
     *
     * @param req 角色请求（plot 剧情正文）
     * @return 200 带角色视图对象列表
     */
    @PostMapping("/characters")
    public Result<List<CharacterVO>> characters(@Valid @RequestBody CharactersRequest req) {
        return workshopService.characters(req, UserContext.getUserId());
    }
}
