package com.darkness.agent.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.constant.AgentConstants;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.AgentMapper;
import com.darkness.common.model.AgentVO;
import com.darkness.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Agent 服务单元测试，覆盖 Agent CRUD、API Key 脱敏保护，以及与 Python AI 引擎的同步。
 * 核心验证点：不存在记录抛 NOT_FOUND；更新时掩码/null 不覆盖真实 API Key；
 * Agent CUD 时同步 Python，Python 失败则抛异常（@Transactional 由框架回滚，单测验证控制流）。
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private PythonAiClient pythonAiClient;

    @InjectMocks
    private AgentServiceImpl agentService;

    // ==================== getAgentById ====================

    @Test
    void getAgentById_success() {
        AgentDO agent = new AgentDO();
        agent.setId(1L);
        agent.setName("GPT-4");
        agent.setApiKey("real-secret-key");
        when(agentMapper.selectById(1L)).thenReturn(agent);

        AgentVO result = agentService.getAgentById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        // from() 方法会脱敏 apiKey
        assertThat(result.getApiKey()).isEqualTo(AgentConstants.API_KEY_MASK);
    }

    @Test
    void getAgentById_notFound_throws404() {
        when(agentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> agentService.getAgentById(999L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    // ==================== listAgents ====================

    @Test
    void listAgents_returnsAll() {
        AgentDO a1 = new AgentDO();
        a1.setId(1L);
        a1.setName("GPT-4");
        AgentDO a2 = new AgentDO();
        a2.setId(2L);
        a2.setName("Claude");
        when(agentMapper.selectList(null)).thenReturn(List.of(a1, a2));

        List<AgentVO> result = agentService.listAgents();

        assertThat(result).hasSize(2);
    }

    // ==================== createAgent ====================

    @Test
    void createAgent_success_pythonSynced() {
        AgentVO vo = new AgentVO();
        vo.setName("NewAgent");
        vo.setApiKey("secret-key");
        when(agentMapper.insert(any(AgentDO.class))).thenAnswer(invocation -> {
            AgentDO entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
        when(pythonAiClient.createAgent(any(AgentDO.class))).thenReturn("py-xyz");
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);

        AgentVO result = agentService.createAgent(vo);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getPythonAgentId()).isEqualTo("py-xyz");
    }

    @Test
    void createAgent_pythonFails_throwsAndDoesNotWritePythonId() {
        AgentVO vo = new AgentVO();
        vo.setName("X");
        vo.setApiKey("k");
        when(agentMapper.insert(any(AgentDO.class))).thenAnswer(i -> {
            i.getArgument(0, AgentDO.class).setId(2L);
            return 1;
        });
        when(pythonAiClient.createAgent(any(AgentDO.class)))
                .thenThrow(new BizException(503, "down"));

        assertThatThrownBy(() -> agentService.createAgent(vo))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(503);
        // 控制流证明：Python 失败后不应进入"回写 pythonId"步骤
        // 使用 any(AgentDO.class) 消除 BaseMapper.updateById(T) / updateById(Collection) 重载歧义
        verify(agentMapper, never()).updateById(any(AgentDO.class));
    }

    // ==================== updateAgent ====================

    @Test
    void updateAgent_success_pythonSynced() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real-secret-key");
        existing.setPythonAgentId("py-1");
        // updateAgent 内部两次 selectById：取 existing、取回写后对象
        when(agentMapper.selectById(1L)).thenReturn(existing, existing);
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);

        AgentVO vo = new AgentVO();
        vo.setName("Updated");
        vo.setApiKey("new-real-key");

        agentService.updateAgent(1L, vo);

        verify(pythonAiClient).updateAgent(eq("py-1"), any(AgentDO.class));
    }

    @Test
    void updateAgent_notFound_throws404() {
        when(agentMapper.selectById(999L)).thenReturn(null);

        AgentVO vo = new AgentVO();
        assertThatThrownBy(() -> agentService.updateAgent(999L, vo))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void updateAgent_notSynced_throws() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real");
        existing.setPythonAgentId(null); // 未同步
        when(agentMapper.selectById(1L)).thenReturn(existing);

        AgentVO vo = new AgentVO();
        vo.setName("U");
        assertThatThrownBy(() -> agentService.updateAgent(1L, vo))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.INTERNAL_ERROR.getCode());
        // 使用 any(AgentDO.class) 消除 BaseMapper.updateById(T) / updateById(Collection) 重载歧义
        verify(agentMapper, never()).updateById(any(AgentDO.class));
        verifyNoInteractions(pythonAiClient);
    }

    @Test
    void updateAgent_preserveApiKey_whenMaskSent() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real-secret-key");
        existing.setPythonAgentId("py-1");
        when(agentMapper.selectById(1L)).thenReturn(existing, existing);
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);

        AgentVO vo = new AgentVO();
        vo.setName("Updated");
        vo.setApiKey(AgentConstants.API_KEY_MASK);

        agentService.updateAgent(1L, vo);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentDO.class);
        verify(agentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("real-secret-key");
    }

    @Test
    void updateAgent_preserveApiKey_whenNullSent() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real-secret-key");
        existing.setPythonAgentId("py-1");
        when(agentMapper.selectById(1L)).thenReturn(existing, existing);
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);

        AgentVO vo = new AgentVO();
        vo.setName("Updated");
        vo.setApiKey(null);

        agentService.updateAgent(1L, vo);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentDO.class);
        verify(agentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("real-secret-key");
    }

    @Test
    void updateAgent_pythonFails_throws() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real");
        existing.setPythonAgentId("py-1");
        when(agentMapper.selectById(1L)).thenReturn(existing);
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);
        doThrow(new BizException(503, "down"))
                .when(pythonAiClient).updateAgent(eq("py-1"), any(AgentDO.class));

        AgentVO vo = new AgentVO();
        vo.setName("U");
        vo.setApiKey("******");
        assertThatThrownBy(() -> agentService.updateAgent(1L, vo))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(503);
    }

    // ==================== deleteAgent ====================

    @Test
    void deleteAgent_success() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setPythonAgentId("py-1");
        when(agentMapper.selectById(1L)).thenReturn(existing);
        when(agentMapper.deleteById(1L)).thenReturn(1);

        agentService.deleteAgent(1L);

        verify(agentMapper).deleteById(1L);
        verify(pythonAiClient).deleteAgent("py-1");
    }

    @Test
    void deleteAgent_notFound_throws404() {
        when(agentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> agentService.deleteAgent(999L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void deleteAgent_pythonFails_throws() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setPythonAgentId("py-1");
        when(agentMapper.selectById(1L)).thenReturn(existing);
        when(agentMapper.deleteById(1L)).thenReturn(1);
        doThrow(new BizException(503, "down"))
                .when(pythonAiClient).deleteAgent("py-1");

        assertThatThrownBy(() -> agentService.deleteAgent(1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(503);
    }
}
