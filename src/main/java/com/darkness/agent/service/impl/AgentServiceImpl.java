package com.darkness.agent.service.impl;

import com.darkness.agent.constant.AgentConstants;
import com.darkness.agent.entity.AgentDO;
import com.darkness.agent.mapper.AgentMapper;
import com.darkness.agent.model.AgentVO;
import com.darkness.agent.service.AgentService;
import com.darkness.common.util.ServiceHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 业务服务实现类，处理 Agent 的增删改查逻辑及 API Key 脱敏回写。
 * <p>
 * 查询单条记录时，不存在则抛出 BizException(404)。更新时若前端脱敏回传掩码值
 * 或 apiKey 为 null，则保留数据库中的原始密钥不变，避免密钥被清空。
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;

    /**
     * 根据 ID 查询 Agent 配置。不存在时抛出 BizException(404)。
     * 返回的 AgentVO 中 apiKey 已脱敏。
     *
     * @param id Agent 主键
     * @return Agent 视图对象（apiKey 已脱敏）
     */
    @Override
    public AgentVO getAgentById(Long id) {
        return AgentVO.from(ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id));
    }

    /**
     * 查询所有 Agent 配置列表，按数据库默认顺序返回。
     *
     * @return Agent 视图对象列表
     */
    @Override
    public List<AgentVO> listAgents() {
        return agentMapper.selectList(null).stream().map(AgentVO::from).toList();
    }

    /**
     * 创建 Agent 配置。将 VO 转换为实体后插入 agent 表，返回包含自增主键的 VO。
     *
     * @param vo Agent 视图对象，包含名称、apiUrl、apiKey、model 等字段
     * @return 创建后的 Agent 视图对象（含自增 ID）
     */
    @Override
    public AgentVO createAgent(AgentVO vo) {
        AgentDO entity = vo.toEntity();
        agentMapper.insert(entity);
        return AgentVO.from(entity);
    }

    /**
     * 更新 Agent 配置。先校验 ID 存在（不存在抛 BizException(404)），
     * 若前端脱敏回传掩码值或 apiKey 为 null，则保留数据库中的原始密钥不变，
     * 避免 API Key 被意外清空。
     *
     * @param id Agent 主键
     * @param vo  Agent 视图对象，包含待更新字段
     * @return 更新后的 Agent 视图对象
     */
    @Override
    public AgentVO updateAgent(Long id, AgentVO vo) {
        AgentDO existing = ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id);
        AgentDO entity = vo.toEntity();
        entity.setId(id);
        if (entity.getApiKey() == null || AgentConstants.API_KEY_MASK.equals(entity.getApiKey())) {
            // 前端脱敏回传掩码值时保留原密钥不变
            entity.setApiKey(existing.getApiKey());
        }
        agentMapper.updateById(entity);
        return AgentVO.from(agentMapper.selectById(id));
    }

    /**
     * 删除 Agent 配置。先校验 ID 存在（不存在抛 BizException(404)），再执行物理删除。
     *
     * @param id Agent 主键
     */
    @Override
    public void deleteAgent(Long id) {
        ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id);
        agentMapper.deleteById(id);
    }
}
