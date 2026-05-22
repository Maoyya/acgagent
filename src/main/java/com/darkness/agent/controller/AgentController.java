package com.darkness.agent.controller;

import com.darkness.agent.model.AgentVO;
import com.darkness.agent.service.AgentService;
import com.darkness.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent REST 控制器，提供 /api/agents 下的 CRUD 接口。
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * 查询所有 Agent 列表，apiKey 返回脱敏值。
     * GET /api/agents（无需认证）
     *
     * @return Agent 列表
     */
    @GetMapping
    public Result<List<AgentVO>> listAgents() {
        return Result.success(agentService.listAgents());
    }

    /**
     * 根据 ID 查询 Agent 详情，不存在时抛出 404，apiKey 返回脱敏值。
     * GET /api/agents/{id}（无需认证）
     *
     * @param id Agent 主键
     * @return Agent 视图对象
     */
    @GetMapping("/{id}")
    public Result<AgentVO> getAgent(@PathVariable Long id) {
        return Result.success(agentService.getAgentById(id));
    }

    /**
     * 创建新 Agent，需要提供 name、apiUrl、apiKey 等配置信息。
     * POST /api/agents（需认证）
     *
     * @param vo Agent 配置信息
     * @return 创建后的 Agent 视图对象（apiKey 已脱敏）
     */
    @PostMapping
    public Result<AgentVO> createAgent(@RequestBody AgentVO vo) {
        return Result.success(agentService.createAgent(vo));
    }

    /**
     * 更新 Agent 配置，不存在时抛出 404。apiKey 传入 "******" 时保留原值不变。
     * PUT /api/agents/{id}（需认证）
     *
     * @param id Agent 主键
     * @param vo 需要更新的字段
     * @return 更新后的 Agent 视图对象
     */
    @PutMapping("/{id}")
    public Result<AgentVO> updateAgent(@PathVariable Long id, @RequestBody AgentVO vo) {
        return Result.success(agentService.updateAgent(id, vo));
    }

    /**
     * 逻辑删除 Agent。
     * DELETE /api/agents/{id}（需认证）
     *
     * @param id Agent 主键
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAgent(@PathVariable Long id) {
        agentService.deleteAgent(id);
        return Result.success();
    }
}
