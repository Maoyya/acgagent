package com.darkness.agent.dubbo;

import com.darkness.api.facade.AgentFacade;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Agent 服务 Dubbo Facade 实现，供其他服务通过 RPC 查询 Agent 信息。
 */
@DubboService
@RequiredArgsConstructor
public class AgentFacadeImpl implements AgentFacade {

    private final AgentMapper agentMapper;

    @Override
    public boolean isAgentAvailable(Long agentId) {
        if (agentId == null) return false;
        AgentDO agent = agentMapper.selectById(agentId);
        return agent != null && agent.getStatus() == CommonStatus.ENABLED;
    }
}
