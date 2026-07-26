package com.darkness.workshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.entity.WorkshopProjectDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.WorkshopProjectMapper;
import com.darkness.common.model.CharactersRequest;
import com.darkness.common.model.CharacterVO;
import com.darkness.common.model.PlotRequest;
import com.darkness.common.model.ShotVO;
import com.darkness.common.model.StoryboardRequest;
import com.darkness.common.model.WorkshopPlotStreamEvent;
import com.darkness.common.model.WorkshopProjectRequest;
import com.darkness.common.model.WorkshopProjectVO;
import com.darkness.common.result.Result;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.ServiceHelper;
import com.darkness.workshop.service.WorkshopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 创作工坊服务实现：项目 CRUD（owner-only）+ 生成转发。
 * <p>
 * 镜像 {@code PromptServiceImpl} 的 owner 鉴权 + {@link ServiceHelper#findOrThrow} + {@link LambdaQueryWrapper} 模式。
 * 注：生成方法（plot/storyboard/characters）为纯转发 PythonAiClient，Service 层不做业务组装；落库时机由 Controller 决定。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkshopServiceImpl implements WorkshopService {

    private final WorkshopProjectMapper workshopProjectMapper;
    private final PythonAiClient pythonAiClient;

    @Override
    public WorkshopProjectVO createProject(WorkshopProjectRequest req, Long userId) {
        WorkshopProjectDO entity = new WorkshopProjectDO();
        entity.setUserId(userId);
        entity.setTitle(req.getTitle() != null ? req.getTitle() : autoTitle(req.getStory()));
        entity.setStory(req.getStory());
        entity.setPlot(req.getPlot());
        entity.setStoryboard(req.getStoryboard());
        entity.setCharacters(req.getCharacters());
        workshopProjectMapper.insert(entity);
        return WorkshopProjectVO.from(entity);
    }

    @Override
    public List<WorkshopProjectVO> listProjects(Long userId) {
        LambdaQueryWrapper<WorkshopProjectDO> qw = new LambdaQueryWrapper<>();
        qw.eq(WorkshopProjectDO::getUserId, userId)
                .orderByDesc(WorkshopProjectDO::getCreatedAt);
        return workshopProjectMapper.selectList(qw).stream().map(WorkshopProjectVO::from).toList();
    }

    @Override
    public WorkshopProjectVO getProject(Long id, Long userId) {
        WorkshopProjectDO entity = ServiceHelper.findOrThrow(workshopProjectMapper.selectById(id), "WorkshopProject", id);
        checkOwner(entity, userId);
        return WorkshopProjectVO.from(entity);
    }

    @Override
    public WorkshopProjectVO updateProject(Long id, WorkshopProjectRequest req, Long userId) {
        WorkshopProjectDO entity = ServiceHelper.findOrThrow(workshopProjectMapper.selectById(id), "WorkshopProject", id);
        checkOwner(entity, userId);
        // 局部更新：仅覆盖 req 中非空的字段（title/story/plot/storyboard/characters）
        if (req.getTitle() != null) entity.setTitle(req.getTitle());
        if (req.getStory() != null) entity.setStory(req.getStory());
        if (req.getPlot() != null) entity.setPlot(req.getPlot());
        if (req.getStoryboard() != null) entity.setStoryboard(req.getStoryboard());
        if (req.getCharacters() != null) entity.setCharacters(req.getCharacters());
        workshopProjectMapper.updateById(entity);
        return WorkshopProjectVO.from(workshopProjectMapper.selectById(id));
    }

    @Override
    public void deleteProject(Long id, Long userId) {
        WorkshopProjectDO entity = ServiceHelper.findOrThrow(workshopProjectMapper.selectById(id), "WorkshopProject", id);
        checkOwner(entity, userId);
        workshopProjectMapper.deleteById(id);
    }

    @Override
    public Flux<WorkshopPlotStreamEvent> plot(PlotRequest req, Long userId) {
        // 纯转发 Python SSE 流；不落库、不组装 Result（落库由 Controller 显式调 update 完成）
        return pythonAiClient.streamWorkshopPlot(req, userId);
    }

    @Override
    public Result<List<ShotVO>> storyboard(StoryboardRequest req, Long userId) {
        return Result.success(pythonAiClient.workshopStoryboard(req, userId));
    }

    @Override
    public Result<List<CharacterVO>> characters(CharactersRequest req, Long userId) {
        return Result.success(pythonAiClient.workshopCharacters(req, userId));
    }

    /**
     * title 缺省策略：story 为 blank/null 时返回"新建项目"；否则取 story 前 32 字（≤32 全量）。
     */
    private String autoTitle(String story) {
        if (story == null || story.isBlank()) return "新建项目";
        return story.length() <= 32 ? story : story.substring(0, 32);
    }

    /**
     * owner 鉴权：项目非当前用户拥有则抛 FORBIDDEN（403），不区分"不存在"与"越权"以避免信息泄露。
     */
    private void checkOwner(WorkshopProjectDO entity, Long userId) {
        if (!userId.equals(entity.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该项目");
        }
    }
}
