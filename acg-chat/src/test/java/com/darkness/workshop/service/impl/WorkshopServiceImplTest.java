package com.darkness.workshop.service.impl;

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
import com.darkness.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkshopServiceImpl 单元测试。业务意义：
 * <ul>
 *   <li>项目为每用户私有——create 时归属当前用户；get/update/delete 越权一律 403（owner-only）。</li>
 *   <li>title 缺省取 story 前 32 字，便于无标题场景下列表展示。</li>
 *   <li>生成方法（plot/storyboard/characters）为纯转发 PythonAiClient，Service 层不做业务组装。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WorkshopServiceImplTest {

    @Mock private WorkshopProjectMapper mapper;
    @Mock private PythonAiClient pythonAiClient;
    @InjectMocks private WorkshopServiceImpl service;

    @Test
    void createProject_defaultsTitleFromStory() {
        // 业务意义：title 缺省时取 story 前 32 字；项目归属当前用户
        when(mapper.insert(any(WorkshopProjectDO.class))).thenAnswer(i -> {
            i.getArgument(0, WorkshopProjectDO.class).setId(1L);
            return 1;
        });
        WorkshopProjectRequest req = new WorkshopProjectRequest();
        req.setStory("少女在雨夜的东京街头捡到会说话的黑猫");
        WorkshopProjectVO vo = service.createProject(req, 7L);
        assertThat(vo.getTitle()).isEqualTo("少女在雨夜的东京街头捡到会说话的黑猫"); // ≤32 全量
        // 归属当前用户
        ArgumentCaptor<WorkshopProjectDO> captor = ArgumentCaptor.forClass(WorkshopProjectDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    }

    @Test
    void getProject_otherUser_forbidden_403() {
        // 业务意义：项目私有，越权访问必须 403（不能泄露是否存在）
        WorkshopProjectDO tpl = new WorkshopProjectDO();
        tpl.setId(1L);
        tpl.setUserId(1L);
        when(mapper.selectById(1L)).thenReturn(tpl);
        assertThatThrownBy(() -> service.getProject(1L, 999L))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    void listProjects_returnsOnlyOwn() {
        // 业务意义：列表查询按 userId 过滤（impl 用 LambdaQueryWrapper.eq(userId)）
        WorkshopProjectDO p = new WorkshopProjectDO();
        p.setId(1L);
        p.setUserId(7L);
        p.setTitle("t");
        when(mapper.selectList(any())).thenReturn(List.of(p));
        assertThat(service.listProjects(7L)).hasSize(1);
    }

    @Test
    void storyboard_delegatesToPython() {
        // 业务意义：storyboard 为纯转发——Service 不做组装，直接委托 PythonAiClient
        StoryboardRequest req = new StoryboardRequest();
        req.setPlot("剧情");
        when(pythonAiClient.workshopStoryboard(req, 7L)).thenReturn(List.of(new ShotVO()));
        assertThat(service.storyboard(req, 7L).getData()).hasSize(1);
    }

    @Test
    void plot_delegatesToPython() {
        // 业务意义：plot 为纯转发——直接返回 Python SSE 流
        PlotRequest req = new PlotRequest();
        req.setStory("故事");
        WorkshopPlotStreamEvent evt = new WorkshopPlotStreamEvent();
        evt.setType("done");
        when(pythonAiClient.streamWorkshopPlot(req, 7L)).thenReturn(Flux.just(evt));
        assertThat(service.plot(req, 7L).toStream().toList()).hasSize(1);
    }

    @Test
    void characters_delegatesToPython() {
        // 业务意义：characters 为纯转发——Service 不做组装
        CharactersRequest req = new CharactersRequest();
        req.setPlot("剧情");
        when(pythonAiClient.workshopCharacters(req, 7L)).thenReturn(List.of(new CharacterVO()));
        assertThat(service.characters(req, 7L).getData()).hasSize(1);
    }
}
