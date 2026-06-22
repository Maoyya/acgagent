package com.darkness.prompt.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.agent.service.AgentService;
import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.PromptTemplateMapper;
import com.darkness.common.model.AgentVO;
import com.darkness.common.model.CostEstimateVO;
import com.darkness.common.model.ModerationVerdictVO;
import com.darkness.common.model.PromptEstimateRequest;
import com.darkness.common.model.PromptGenerateOutcome;
import com.darkness.common.model.PromptGenerateRequest;
import com.darkness.common.model.PromptGenerateResponseVO;
import com.darkness.common.model.PromptModerateRequest;
import com.darkness.common.model.PromptTemplateRequest;
import com.darkness.common.model.PromptTemplateVO;
import com.darkness.common.result.Result;
import com.darkness.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** PromptService 单元测试。业务意义：生成物归属当前用户；403 是业务结果不落库；Python 500 透传（Fail Loud）。 */
@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock private PromptTemplateMapper promptTemplateMapper;
    @Mock private PythonAiClient pythonAiClient;
    @Mock private AgentService agentService;
    @InjectMocks private PromptServiceImpl promptService;

    @Test
    void generate_success_persistsAsPrivateOfCurrentUser() {
        PromptGenerateRequest req = new PromptGenerateRequest();
        req.setUserHints(List.of("毒舌客服"));
        req.setMode(com.darkness.common.enums.PromptMode.ACG);
        PromptGenerateResponseVO resp = new PromptGenerateResponseVO();
        resp.setSystemPrompt("你是毒舌客服");
        CostEstimateVO est = new CostEstimateVO();
        est.setPromptTokens(120);
        resp.setEstimate(est);
        when(pythonAiClient.generatePrompt(req, 7L)).thenReturn(PromptGenerateOutcome.success(resp));
        when(promptTemplateMapper.insert(any(PromptTemplateDO.class))).thenAnswer(i -> {
            i.getArgument(0, PromptTemplateDO.class).setId(1001L);
            return 1;
        });

        Result<PromptGenerateResponseVO> r = promptService.generate(req, 7L);

        assertThat(r.getCode()).isEqualTo(200);
        ArgumentCaptor<PromptTemplateDO> captor = ArgumentCaptor.forClass(PromptTemplateDO.class);
        verify(promptTemplateMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);              // 归属当前用户
        assertThat(captor.getValue().getName()).isEqualTo("毒舌客服");         // 首条 hint 截断取名
        assertThat(captor.getValue().getEstPromptTokens()).isEqualTo(120);    // 缓存估算
        assertThat(r.getData().getTemplateId()).isEqualTo(1001L);             // Java 追加
    }

    @Test
    void generate_blocked_returns403AndDoesNotPersist() {
        PromptGenerateRequest req = new PromptGenerateRequest();
        req.setUserHints(List.of("暴力内容"));
        ModerationVerdictVO v = new ModerationVerdictVO();
        v.setPassed(false);
        when(pythonAiClient.generatePrompt(req, 7L)).thenReturn(PromptGenerateOutcome.blocked(v));

        Result<PromptGenerateResponseVO> r = promptService.generate(req, 7L);

        assertThat(r.getCode()).isEqualTo(403);
        assertThat(r.getMessage()).isEqualTo("blocked");
        // 接口签名为 Result<PromptGenerateResponseVO>，403 时 data 运行期装的是裁决对象；
        // 用 Object 重载避免编译期 checkcast(PromptGenerateResponseVO) 抛 ClassCastException
        assertThat((Object) r.getData()).isSameAs(v);
        // any(PromptTemplateDO.class) 消除 BaseMapper.insert(T) / insert(Collection) 重载歧义
        verify(promptTemplateMapper, never()).insert(any(PromptTemplateDO.class));
    }

    @Test
    void generate_python500_propagates_doesNotPersist() {
        PromptGenerateRequest req = new PromptGenerateRequest();
        req.setUserHints(List.of("x"));
        when(pythonAiClient.generatePrompt(req, 7L)).thenThrow(new BizException(500, "llm down"));

        assertThatThrownBy(() -> promptService.generate(req, 7L))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(500);
        // any(PromptTemplateDO.class) 消除 BaseMapper.insert(T) / insert(Collection) 重载歧义
        verify(promptTemplateMapper, never()).insert(any(PromptTemplateDO.class));
    }

    // ==================== listTemplates ====================

    @Test
    void listTemplates_nonAdmin_mapsToDoVO() {
        // 注意：WHERE 可见性过滤由 LambdaQueryWrapper 表达，单测 mock selectList(any) 无法验证 SQL；
        // 此处验证 DO→VO 映射 + 分支调用；可见性 SQL 正确性由集成/手动测试覆盖（与 ChatServiceImplTest 同惯例）。
        PromptTemplateDO pub = new PromptTemplateDO();
        pub.setId(1L); pub.setUserId(null); pub.setName("P");
        PromptTemplateDO priv = new PromptTemplateDO();
        priv.setId(2L); priv.setUserId(7L); priv.setName("Q");
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(pub, priv));

        List<PromptTemplateVO> r = promptService.listTemplates(7L, false, null);

        assertThat(r).hasSize(2);
        assertThat(r.get(0).getIsPublic()).isTrue();
        assertThat(r.get(1).getIsPublic()).isFalse();
    }

    // ==================== getTemplate ====================

    @Test
    void getTemplate_notFound_throws404() {
        when(promptTemplateMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> promptService.getTemplate(999L, 1L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void getTemplate_otherUserPrivate_forbidden_403() {
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(1L); tpl.setUserId(1L); // 别人的私有
        when(promptTemplateMapper.selectById(1L)).thenReturn(tpl);
        assertThatThrownBy(() -> promptService.getTemplate(1L, 999L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    void getTemplate_public_readableByAnyone() {
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(1L); tpl.setUserId(null); // 公共
        when(promptTemplateMapper.selectById(1L)).thenReturn(tpl);
        assertThat(promptService.getTemplate(1L, 999L, false).getIsPublic()).isTrue();
    }

    // ==================== createTemplate ====================

    @Test
    void createTemplate_nonAdminSetPublic_forbidden_403() {
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s"); req.setIsPublic(true);
        assertThatThrownBy(() -> promptService.createTemplate(req, 7L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
        // any(PromptTemplateDO.class) 消除 BaseMapper.insert(T) / insert(Collection) 重载歧义
        verify(promptTemplateMapper, never()).insert(any(PromptTemplateDO.class));
    }

    @Test
    void createTemplate_adminPublic_userIdNull() {
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s"); req.setIsPublic(true);
        when(promptTemplateMapper.insert(any(PromptTemplateDO.class))).thenAnswer(i -> {
            i.getArgument(0, PromptTemplateDO.class).setId(1L);
            return 1;
        });
        ArgumentCaptor<PromptTemplateDO> captor = ArgumentCaptor.forClass(PromptTemplateDO.class);
        promptService.createTemplate(req, 1L, true);
        verify(promptTemplateMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull(); // 公共→NULL
    }

    @Test
    void createTemplate_userPrivate_userIdCurrentUser() {
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s"); // isPublic 未置
        when(promptTemplateMapper.insert(any(PromptTemplateDO.class))).thenAnswer(i -> {
            i.getArgument(0, PromptTemplateDO.class).setId(2L);
            return 1;
        });
        ArgumentCaptor<PromptTemplateDO> captor = ArgumentCaptor.forClass(PromptTemplateDO.class);
        promptService.createTemplate(req, 7L, false);
        verify(promptTemplateMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L); // 私有→当前用户
    }

    // ==================== updateTemplate / deleteTemplate ====================

    @Test
    void updateTemplate_otherUserPrivate_forbidden_403() {
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(1L); tpl.setUserId(1L); // 别人的私有
        when(promptTemplateMapper.selectById(1L)).thenReturn(tpl);
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s");
        assertThatThrownBy(() -> promptService.updateTemplate(1L, req, 999L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    void deleteTemplate_publicByNonAdmin_forbidden_403() {
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(1L); tpl.setUserId(null); // 公共
        when(promptTemplateMapper.selectById(1L)).thenReturn(tpl);
        assertThatThrownBy(() -> promptService.deleteTemplate(1L, 7L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
        // anyLong() 消除 BaseMapper.deleteById(Serializable) / deleteById(T) 重载歧义
        verify(promptTemplateMapper, never()).deleteById(anyLong());
    }

    // ==================== moderate / estimate ====================

    @Test
    void moderate_forwardsToPython() {
        PromptModerateRequest req = new PromptModerateRequest();
        req.setSystemPrompt("x"); req.setMode(com.darkness.common.enums.PromptMode.ACG);
        ModerationVerdictVO v = new ModerationVerdictVO(); v.setPassed(true);
        when(pythonAiClient.moderatePrompt(req, 7L)).thenReturn(v);
        assertThat(promptService.moderate(req, 7L)).isSameAs(v);
    }

    @Test
    void estimate_forwardsToPython() {
        PromptEstimateRequest req = new PromptEstimateRequest();
        req.setSystemPrompt("x");
        CostEstimateVO e = new CostEstimateVO(); e.setPromptTokens(50);
        when(pythonAiClient.estimatePrompt(req, 7L)).thenReturn(e);
        assertThat(promptService.estimate(req, 7L).getPromptTokens()).isEqualTo(50);
    }

    // ==================== applyToAgent ====================

    @Test
    void applyToAgent_updatesAgentSystemPromptAndSyncs() {
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(3L); tpl.setSystemPrompt("你是客服");
        when(promptTemplateMapper.selectById(3L)).thenReturn(tpl);
        AgentVO agent = new AgentVO(); agent.setId(9L); agent.setSystemPrompt("old");
        // 模拟真实加载的 Agent：预填 model/apiUrl/capabilities，apply 必须保留它们，只搬 system_prompt
        agent.setModel("gpt-4");
        agent.setApiUrl("http://llm");
        agent.setCapabilities(java.util.List.of("chat", "rag"));
        when(agentService.getAgentById(9L)).thenReturn(agent);
        when(agentService.updateAgent(eq(9L), any(AgentVO.class))).thenReturn(agent);

        promptService.applyToAgent(3L, 9L);

        ArgumentCaptor<AgentVO> captor = ArgumentCaptor.forClass(AgentVO.class);
        verify(agentService).updateAgent(eq(9L), captor.capture());
        assertThat(captor.getValue().getSystemPrompt()).isEqualTo("你是客服"); // 只搬 system_prompt
        // 不变量：apply 不得覆盖其它 Agent 配置
        assertThat(captor.getValue().getModel()).isEqualTo("gpt-4");
        assertThat(captor.getValue().getApiUrl()).isEqualTo("http://llm");
        assertThat(captor.getValue().getCapabilities()).containsExactly("chat", "rag");
    }

    @Test
    void applyToAgent_templateNotFound_throws404() {
        when(promptTemplateMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> promptService.applyToAgent(99L, 9L))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
        // anyLong() 避免 bare any() 推断为 Object 的歧义（AgentService.getAgentById(Long)）
        verify(agentService, never()).getAgentById(anyLong());
    }
}
