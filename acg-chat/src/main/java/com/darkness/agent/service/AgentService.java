package com.darkness.agent.service;

import com.darkness.common.model.AgentVO;

import java.util.List;

/**
 * Agent 业务服务接口，定义 Agent 的增删改查操作。
 */
public interface AgentService {

    /**
     * 根据 ID 查询 Agent。
     * 从数据库查询 AgentDO 并转换为 VO（apiKey 自动脱敏），不存在时抛出 404 BizException。
     *
     * @param id Agent 主键
     * @return 脱敏后的 AgentVO
     */
    AgentVO getAgentById(Long id);

    /**
     * 查询所有 Agent 列表。
     * 返回所有未逻辑删除的 Agent，每条记录的 apiKey 均已脱敏。
     *
     * @return 脱敏后的 AgentVO 列表
     */
    List<AgentVO> listAgents();

    /**
     * 创建新 Agent。
     * 将 VO 转为实体持久化到数据库，返回包含生成主键的 VO（apiKey 已脱敏）。
     *
     * @param vo Agent 视图对象
     * @return 创建后的 AgentVO（含生成的主键，apiKey 已脱敏）
     */
    AgentVO createAgent(AgentVO vo);

    /**
     * 更新 Agent 信息。
     * 根据 ID 查找现有记录，当 apiKey 为脱敏值 "******" 时保留数据库原密钥不变，
     * 避免前端回传的掩码值覆盖真实密钥。不存在时抛出 404 BizException。
     *
     * @param id Agent 主键
     * @param vo Agent 视图对象
     * @return 更新后的 AgentVO（apiKey 已脱敏）
     */
    AgentVO updateAgent(Long id, AgentVO vo);

    /**
     * 逻辑删除 Agent。
     * 将 deleted 字段置为 1，数据库中记录仍保留，不存在时抛出 404 BizException。
     *
     * @param id Agent 主键
     */
    void deleteAgent(Long id);
}
