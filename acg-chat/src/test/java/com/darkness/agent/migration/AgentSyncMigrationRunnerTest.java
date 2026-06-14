package com.darkness.agent.migration;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.mapper.AgentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 存量 Agent 迁移测试：验证对每条未同步 agent 调用 Python 创建并回写 python_agent_id；
 * 单条失败不影响其它（记录日志继续）。已同步的跳过。
 * <p>
 * 测试直接调 migrateAgents() 绕过 @Value migrateExisting 开关（该开关在 Spring 启动时注入，
 * 单测里默认 false，run() 会直接 return）。
 */
@ExtendWith(MockitoExtension.class)
class AgentSyncMigrationRunnerTest {

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private PythonAiClient pythonAiClient;

    @InjectMocks
    private AgentSyncMigrationRunner runner;

    @Test
    void migrate_syncsUnsyncedAgents_andWritesPythonId() {
        AgentDO a1 = new AgentDO(); a1.setId(1L); a1.setName("A"); a1.setPythonAgentId(null);
        AgentDO a2 = new AgentDO(); a2.setId(2L); a2.setName("B"); a2.setPythonAgentId(null);
        AgentDO a3 = new AgentDO(); a3.setId(3L); a3.setName("C"); a3.setPythonAgentId("already"); // 已同步，跳过
        when(agentMapper.selectList(any())).thenReturn(List.of(a1, a2, a3));
        when(pythonAiClient.createAgent(any(AgentDO.class))).thenReturn("py-1", "py-2");

        runner.migrateAgents();

        verify(pythonAiClient, times(2)).createAgent(any(AgentDO.class)); // 只同步 a1/a2
        // 使用 ArgumentCaptor.forClass(AgentDO.class) 消除 BaseMapper.updateById(T) / updateById(Collection) 重载歧义
        var captor = org.mockito.ArgumentCaptor.forClass(AgentDO.class);
        verify(agentMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentDO::getPythonAgentId)
                .containsExactlyInAnyOrder("py-1", "py-2");
    }

    @Test
    void migrate_oneFails_continuesOthers() {
        AgentDO a1 = new AgentDO(); a1.setId(1L); a1.setName("A"); a1.setPythonAgentId(null);
        AgentDO a2 = new AgentDO(); a2.setId(2L); a2.setName("B"); a2.setPythonAgentId(null);
        when(agentMapper.selectList(any())).thenReturn(List.of(a1, a2));
        when(pythonAiClient.createAgent(any(AgentDO.class)))
                .thenThrow(new com.darkness.common.exception.BizException(503, "down"))
                .thenReturn("py-2");

        runner.migrateAgents();

        verify(pythonAiClient, times(2)).createAgent(any(AgentDO.class));
        // a1 失败不回写；仅 a2 成功回写 py-2（ArgumentCaptor 消除 updateById 重载歧义）
        var captor = org.mockito.ArgumentCaptor.forClass(AgentDO.class);
        verify(agentMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getPythonAgentId()).isEqualTo("py-2");
    }
}
