package com.darkness.agent.migration;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 存量 Agent 同步迁移程序。
 * <p>
 * 仅当 python-agent.migrate-existing=true 时启动执行（默认关闭）。
 * 对每条 python_agent_id 为空的 agent，调用 Python 创建并回写 python_agent_id；
 * 单条失败仅记日志，不中断其它（迁移尽力而为，可重跑）。
 * <p>
 * 核心逻辑抽到 migrateAgents()（package-private），便于单测绕过 @Value 开关直接验证。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSyncMigrationRunner implements CommandLineRunner {

    private final AgentMapper agentMapper;
    private final PythonAiClient pythonAiClient;

    /** 迁移开关，默认关闭。开启后下次启动执行一次。 */
    @Value("${python-agent.migrate-existing:false}")
    private boolean migrateExisting;

    @Override
    public void run(String... args) {
        if (!migrateExisting) {
            return;
        }
        log.info("Agent Python 同步迁移开始（python-agent.migrate-existing=true）");
        migrateAgents();
    }

    /**
     * 执行存量迁移：遍历所有 agent，对 python_agent_id 为空的逐条在 Python 创建并回写。
     * 单条失败仅记日志、继续处理下一条（可重跑，已同步的会跳过）。
     */
    void migrateAgents() {
        List<AgentDO> all = agentMapper.selectList(null);
        int ok = 0, fail = 0;
        for (AgentDO agent : all) {
            if (agent.getPythonAgentId() != null && !agent.getPythonAgentId().isBlank()) {
                continue; // 已同步，跳过
            }
            try {
                String pythonId = pythonAiClient.createAgent(agent);
                agent.setPythonAgentId(pythonId);
                agentMapper.updateById(agent);
                ok++;
            } catch (Exception e) {
                fail++;
                log.error("Agent 同步失败，id={}, name={}: {}", agent.getId(), agent.getName(), e.getMessage());
            }
        }
        log.info("Agent Python 同步迁移结束：成功 {}，失败 {}", ok, fail);
    }
}
