package com.darkness.prompt.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.agent.service.AgentService;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.AgentMapper;
import com.darkness.common.mapper.PromptTemplateMapper;
import com.darkness.common.model.AgentVO;
import com.darkness.common.model.CostEstimateVO;
import com.darkness.common.model.ModerationVerdictVO;
import com.darkness.common.model.PromptBeautifyRequest;
import com.darkness.common.model.PromptBeautifyResponseVO;
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

/** PromptService 单元测试。业务意义：v1.1 generate 只返回草稿不落库；beautify 用所选 Agent LLM 不校验；create/update 保存闸门拦截非法提示词；Python 500 透传（Fail Loud）。 */
@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock private PromptTemplateMapper promptTemplateMapper;
    @Mock private PythonAiClient pythonAiClient;
    @Mock private AgentService agentService;
    @Mock private AgentMapper agentMapper;
    @InjectMocks private PromptServiceImpl promptService;

    /** 构造 passed=true 裁决（保存闸门放行用）。 */
    private static ModerationVerdictVO passedVerdict() {
        ModerationVerdictVO v = new ModerationVerdictVO();
        v.setPassed(true);
        return v;
    }

    @Test
    void generate_success_returnsDraft_doesNotPersist() {
        // v1.1：generate 不再自动落库，只返回草稿、无 templateId
        PromptGenerateRequest req = new PromptGenerateRequest();
        req.setUserHints(List.of("毒舌客服"));
        req.setMode(com.darkness.common.enums.PromptMode.ACG);
        PromptGenerateResponseVO resp = new PromptGenerateResponseVO();
        resp.setSystemPrompt("你是毒舌客服");
        CostEstimateVO est = new CostEstimateVO();
        est.setPromptTokens(120);
        resp.setEstimate(est);
        when(pythonAiClient.generatePrompt(req, 7L)).thenReturn(PromptGenerateOutcome.success(resp));

        Result<PromptGenerateResponseVO> r = promptService.generate(req, 7L);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData().getSystemPrompt()).isEqualTo("你是毒舌客服"); // 返回草稿
        // 业务意义：generate 不落库——前端拿到草稿后须显式调 create 才落库
        verify(promptTemplateMapper, never()).insert(any(PromptTemplateDO.class));
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

    // ==================== beautify (v1.1) ====================

    @Test
    void beautify_usesAgentLlm_returnsRefined_notPersisted() {
        // 业务意义：beautify 用所选 Agent 的 LLM 润色，不校验、不落库（合规性在 create 闸门统一校验）
        PromptBeautifyRequest req = new PromptBeautifyRequest();
        req.setSystemPrompt("你是客服");
        req.setAgentId(9L);
        AgentDO agent = new AgentDO();
        agent.setId(9L);
        agent.setApiKey("real-key"); // 原始 DO 含真实 apiKey（非脱敏 VO）
        when(agentMapper.selectById(9L)).thenReturn(agent);
        PromptBeautifyResponseVO refined = new PromptBeautifyResponseVO();
        refined.setSystemPrompt("你是专业的客服");
        when(pythonAiClient.beautifyPrompt(eq(req), eq(agent), eq(7L))).thenReturn(refined);

        Result<PromptBeautifyResponseVO> r = promptService.beautify(req, 7L);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData().getSystemPrompt()).isEqualTo("你是专业的客服");
        verify(promptTemplateMapper, never()).insert(any(PromptTemplateDO.class)); // 不落库
        verify(pythonAiClient, never()).moderatePrompt(any(PromptModerateRequest.class), anyLong()); // 不校验
    }

    @Test
    void beautify_agentNotFound_throws404() {
        PromptBeautifyRequest req = new PromptBeautifyRequest();
        req.setSystemPrompt("x");
        req.setAgentId(99L);
        when(agentMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> promptService.beautify(req, 7L))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
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
        // v1.1：闸门（moderate）在 isPublic 检查之前，故需 stub moderate→passed 才能走到 FORBIDDEN
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s"); req.setIsPublic(true);
        when(pythonAiClient.moderatePrompt(any(PromptModerateRequest.class), eq(7L))).thenReturn(passedVerdict());
        assertThatThrownBy(() -> promptService.createTemplate(req, 7L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
        // any(PromptTemplateDO.class) 消除 BaseMapper.insert(T) / insert(Collection) 重载歧义
        verify(promptTemplateMapper, never()).insert(any(PromptTemplateDO.class));
    }

    @Test
    void createTemplate_adminPublic_userIdNull() {
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s"); req.setIsPublic(true);
        when(pythonAiClient.moderatePrompt(any(PromptModerateRequest.class), eq(1L))).thenReturn(passedVerdict());
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
        when(pythonAiClient.moderatePrompt(any(PromptModerateRequest.class), eq(7L))).thenReturn(passedVerdict());
        when(promptTemplateMapper.insert(any(PromptTemplateDO.class))).thenAnswer(i -> {
            i.getArgument(0, PromptTemplateDO.class).setId(2L);
            return 1;
        });
        ArgumentCaptor<PromptTemplateDO> captor = ArgumentCaptor.forClass(PromptTemplateDO.class);
        promptService.createTemplate(req, 7L, false);
        verify(promptTemplateMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L); // 私有→当前用户
    }

    @Test
    void createTemplate_moderationBlocked_returns403_notPersisted() {
        // 业务意义：保存闸门拦截非法提示词，绝不落库
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("违规内容");
        ModerationVerdictVO blocked = new ModerationVerdictVO();
        blocked.setPassed(false);
        when(pythonAiClient.moderatePrompt(any(PromptModerateRequest.class), eq(7L))).thenReturn(blocked);

        Result<PromptTemplateVO> r = promptService.createTemplate(req, 7L, false);

        assertThat(r.getCode()).isEqualTo(403);
        assertThat(r.getMessage()).isEqualTo("blocked");
        assertThat((Object) r.getData()).isSameAs(blocked);
        verify(promptTemplateMapper, never()).insert(any(PromptTemplateDO.class));
    }

    @Test
    void createTemplate_pythonModerateDown_failsLoud() {
        // 业务意义：保存闸门依赖 Python /moderate（强一致 #15），Python 挂 → 保存失败，绝不放行
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s");
        when(pythonAiClient.moderatePrompt(any(PromptModerateRequest.class), eq(7L)))
                .thenThrow(new BizException(500, "moderate down"));

        assertThatThrownBy(() -> promptService.createTemplate(req, 7L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(500);
        verify(promptTemplateMapper, never()).insert(any(PromptTemplateDO.class));
    }

    // ==================== updateTemplate / deleteTemplate ====================

    @Test
    void updateTemplate_otherUserPrivate_forbidden_403() {
        // v1.1：updateTemplate = selectById → checkWritable(越权抛403) → moderate → update。
        // 越权在闸门之前，故不会触发 moderate（无需 stub）。
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(1L); tpl.setUserId(1L); // 别人的私有
        when(promptTemplateMapper.selectById(1L)).thenReturn(tpl);
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s");
        assertThatThrownBy(() -> promptService.updateTemplate(1L, req, 999L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(pythonAiClient, never()).moderatePrompt(any(PromptModerateRequest.class), anyLong()); // 越权不触发闸门
    }

    @Test
    void updateTemplate_moderationBlocked_returns403_notUpdated() {
        // 业务意义：改后提示词违规 → 不 update（改也要过闸门）
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(1L); tpl.setUserId(7L); // 自己的私有
        when(promptTemplateMapper.selectById(1L)).thenReturn(tpl);
        ModerationVerdictVO blocked = new ModerationVerdictVO();
        blocked.setPassed(false);
        when(pythonAiClient.moderatePrompt(any(PromptModerateRequest.class), eq(7L))).thenReturn(blocked);

        Result<PromptTemplateVO> r = promptService.updateTemplate(1L,
                buildTplReq("N", "违规"), 7L, false);

        assertThat(r.getCode()).isEqualTo(403);
        assertThat(r.getMessage()).isEqualTo("blocked");
        verify(promptTemplateMapper, never()).updateById(any(PromptTemplateDO.class)); // 不 update
    }

    /** 构造最小 PromptTemplateRequest 辅助。 */
    private static PromptTemplateRequest buildTplReq(String name, String systemPrompt) {
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName(name);
        req.setSystemPrompt(systemPrompt);
        return req;
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
