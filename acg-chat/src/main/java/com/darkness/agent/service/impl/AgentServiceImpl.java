package com.darkness.agent.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.agent.service.AgentService;
import com.darkness.common.constant.AgentConstants;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.AgentMapper;
import com.darkness.common.model.AgentVO;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.ServiceHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent 业务服务实现类，处理 Agent 的增删改查、API Key 脱敏，以及与 Python AI 引擎的配置同步。
 * <p>
 * Java 是 Agent 配置的真相源：CUD 时在 @Transactional 内先落 MySQL，再同步推送 Python；
 * Python 同步失败则抛异常，由 @Transactional 回滚 MySQL，保证两边一致。
 * 更新时若前端脱敏回传掩码值或 apiKey 为 null，保留数据库原值，避免密钥被清空。
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final PythonAiClient pythonAiClient;

    /**
     * 根据 ID 查询 Agent 配置。不存在时抛出 BizException(404)，apiKey 脱敏。
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
     * 创建 Agent。@Transactional 内：插 MySQL（python_agent_id 暂空）→ 调 Python 创建 → 回写 python_agent_id。
     * Python 失败 → 抛异常 → 回滚（删除刚插入的行）。
     *
     * @param vo Agent 视图对象，包含名称、apiUrl、apiKey、model 等字段
     * @return 创建后的 Agent 视图对象（含自增 ID 和 Python 同步锚点）
     */
    @Override
    @Transactional
    public AgentVO createAgent(AgentVO vo) {
        AgentDO entity = vo.toEntity();
        entity.setPythonAgentId(null);
        agentMapper.insert(entity);
        String pythonId = pythonAiClient.createAgent(entity); // 失败抛异常 → 回滚
        entity.setPythonAgentId(pythonId);
        agentMapper.updateById(entity);
        return AgentVO.from(entity);
    }

    /**
     * 更新 Agent。@Transactional 内：校验存在 + 已同步 → 更新 MySQL（掩码/null 保留原 apiKey）→ 同步 Python。
     * Python 失败 → 抛异常 → 回滚到旧值。
     *
     * @param id Agent 主键
     * @param vo Agent 视图对象，包含待更新字段
     * @return 更新后的 Agent 视图对象（apiKey 已脱敏）
     */
    @Override
    @Transactional
    public AgentVO updateAgent(Long id, AgentVO vo) {
        AgentDO existing = ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id);
        if (existing.getPythonAgentId() == null || existing.getPythonAgentId().isBlank()) {
            // 未同步到 Python，无法更新对端，提示重建
            throw new BizException(ResultCode.INTERNAL_ERROR, "Agent 未同步到 AI 引擎，请联系管理员重建");
        }
        AgentDO entity = vo.toEntity();
        entity.setId(id);
        entity.setPythonAgentId(existing.getPythonAgentId());
        if (entity.getApiKey() == null || AgentConstants.API_KEY_MASK.equals(entity.getApiKey())) {
            // 前端脱敏回传掩码值时保留原密钥不变
            entity.setApiKey(existing.getApiKey());
        }
        agentMapper.updateById(entity);
        pythonAiClient.updateAgent(existing.getPythonAgentId(), entity); // 失败抛异常 → 回滚
        return AgentVO.from(agentMapper.selectById(id));
    }

    /**
     * 删除 Agent。@Transactional 内：校验存在 → 删 MySQL → 删 Python（已同步时）。
     * Python 失败 → 抛异常 → 回滚（恢复行）。
     *
     * @param id Agent 主键
     */
    @Override
    @Transactional
    public void deleteAgent(Long id) {
        AgentDO existing = ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id);
        agentMapper.deleteById(id);
        if (existing.getPythonAgentId() != null && !existing.getPythonAgentId().isBlank()) {
            pythonAiClient.deleteAgent(existing.getPythonAgentId()); // 失败抛异常 → 回滚
        }
    }
}
