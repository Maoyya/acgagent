package com.darkness.agent.service.impl;

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
import static org.mockito.Mockito.*;

/**
 * Agent 服务单元测试，覆盖 Agent CRUD 和 API Key 脱敏保护逻辑。
 * 核心验证点：不存在的记录抛 NOT_FOUND，更新时掩码/null 不覆盖真实 API Key。
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock
    private AgentMapper agentMapper;

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
    void createAgent_success() {
        AgentVO vo = new AgentVO();
        vo.setName("NewAgent");
        vo.setApiKey("secret-key");
        when(agentMapper.insert(any(AgentDO.class))).thenAnswer(invocation -> {
            AgentDO entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        AgentVO result = agentService.createAgent(vo);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    // ==================== updateAgent ====================

    @Test
    void updateAgent_success() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real-secret-key");
        when(agentMapper.selectById(1L)).thenReturn(existing);
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);

        AgentVO vo = new AgentVO();
        vo.setName("Updated");
        vo.setApiKey("new-real-key");

        AgentVO result = agentService.updateAgent(1L, vo);

        assertThat(result).isNotNull();
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
    void updateAgent_preserveApiKey_whenMaskSent() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real-secret-key");
        when(agentMapper.selectById(1L)).thenReturn(existing);
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);

        AgentVO vo = new AgentVO();
        vo.setName("Updated");
        vo.setApiKey(AgentConstants.API_KEY_MASK);

        agentService.updateAgent(1L, vo);

        // 验证 updateById 传入的实体保留了真实的 apiKey
        var captor = org.mockito.ArgumentCaptor.forClass(AgentDO.class);
        verify(agentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("real-secret-key");
    }

    @Test
    void updateAgent_preserveApiKey_whenNullSent() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real-secret-key");
        when(agentMapper.selectById(1L)).thenReturn(existing);
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);

        AgentVO vo = new AgentVO();
        vo.setName("Updated");
        vo.setApiKey(null);

        agentService.updateAgent(1L, vo);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentDO.class);
        verify(agentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("real-secret-key");
    }

    // ==================== deleteAgent ====================

    @Test
    void deleteAgent_success() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        when(agentMapper.selectById(1L)).thenReturn(existing);
        when(agentMapper.deleteById(1L)).thenReturn(1);

        agentService.deleteAgent(1L);

        verify(agentMapper).deleteById(1L);
    }

    @Test
    void deleteAgent_notFound_throws404() {
        when(agentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> agentService.deleteAgent(999L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }
}
