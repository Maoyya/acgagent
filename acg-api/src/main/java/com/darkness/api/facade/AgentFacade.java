package com.darkness.api.facade;

/**
 * Agent 服务 Dubbo Facade，供其他服务通过 RPC 查询 Agent 信息。
 */
public interface AgentFacade {

    /**
     * 判断 Agent 是否存在且启用。
     *
     * @param agentId Agent ID
     * @return true 表示可用
     */
    boolean isAgentAvailable(Long agentId);
}
