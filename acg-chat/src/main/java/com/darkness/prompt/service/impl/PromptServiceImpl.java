package com.darkness.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.agent.client.PythonAiClient;
import com.darkness.agent.service.AgentService;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PromptMode;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.AgentMapper;
import com.darkness.common.mapper.PromptTemplateMapper;
import com.darkness.common.model.AgentVO;
import com.darkness.common.model.CostEstimateVO;
import com.darkness.common.model.ModerationVerdictVO;
import com.darkness.common.model.PromptBeautifyRequest;
import com.darkness.common.model.PromptBeautifyResponseVO;
import com.darkness.common.model.PromptEstimateRequest;
import com.darkness.common.model.PromptGenerateOutcome;
import com.darkness.common.model.PromptGenerateRequest;
import com.darkness.common.model.PromptGenerateResponseVO;
import com.darkness.common.model.PromptModerateRequest;
import com.darkness.common.model.PromptTemplateRequest;
import com.darkness.common.model.PromptTemplateVO;
import com.darkness.common.result.Result;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.ServiceHelper;
import com.darkness.prompt.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提示词生成与模板管理服务实现。
 * <p>
 * v1.1 流程：generate 只返回草稿（不落库）；beautify 用所选 Agent LLM 润色（不校验/不落库）；
 * create/update 落库前过 moderation 闸门（blocked→403 不落库；Python 不可用→500 强一致）。
 * 注：userId/isAdmin 由 Controller 从 UserContext 取后透传，便于纯 Mockito 单测。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final PythonAiClient pythonAiClient;
    private final AgentService agentService;
    /**
     * 用于 beautify 取 Agent 的原始 DO（含真实 apiKey）。
     * 不能用 AgentService.getAgentById（返回脱敏 VO，apiKey 为 ****** 会污染 llm_config）。
     */
    private final AgentMapper agentMapper;

    @Override
    public Result<PromptGenerateResponseVO> generate(PromptGenerateRequest req, Long userId) {
        PromptGenerateOutcome outcome = pythonAiClient.generatePrompt(req, userId);
        if (outcome.isBlocked()) {
            // moderation 不通过：返回 403 + 裁决，不返回草稿、不落库（镜像 Python 契约）
            return blockedResult(outcome.getVerdict());
        }
        // v1.1：generate 不再自动落库，只返回草稿。落库统一到 create 端点。
        return Result.success(outcome.getSuccess());
    }

    @Override
    public Result<PromptBeautifyResponseVO> beautify(PromptBeautifyRequest req, Long userId) {
        // 取 Agent 原始 DO（含真实 apiKey 构造 llm_config）；不能用 AgentService.getAgentById（脱敏 VO）
        AgentDO agent = agentMapper.selectById(req.getAgentId());
        if (agent == null) {
            throw new BizException(ResultCode.NOT_FOUND, "Agent 不存在: " + req.getAgentId());
        }
        // 不校验、不落库（合规性在后续 create/update 的保存闸门统一校验）
        return Result.success(pythonAiClient.beautifyPrompt(req, agent, userId));
    }

    /**
     * 403-blocked 响应构造：code=403,message="blocked",data=裁决。
     * 泛型方法适配 generate / create / update 三处签名（运行期 data 即 verdict）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Result<T> blockedResult(ModerationVerdictVO verdict) {
        // 用裸类型构造后由方法签名归一化；与 test 契约一致（r.getData() isSameAs(v)）
        return new Result(ResultCode.FORBIDDEN.getCode(), "blocked", verdict);
    }

    @Override
    public java.util.List<PromptTemplateVO> listTemplates(Long userId, boolean isAdmin, PromptMode mode) {
        LambdaQueryWrapper<PromptTemplateDO> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(PromptTemplateDO::getCreatedAt);
        if (mode != null) qw.eq(PromptTemplateDO::getMode, mode);
        if (!isAdmin) {
            // 非管理员：自己的私有 OR 全部公共
            qw.and(w -> w.eq(PromptTemplateDO::getUserId, userId)
                    .or().isNull(PromptTemplateDO::getUserId));
        }
        return promptTemplateMapper.selectList(qw).stream().map(PromptTemplateVO::from).toList();
    }

    @Override
    public PromptTemplateVO getTemplate(Long id, Long userId, boolean isAdmin) {
        PromptTemplateDO tpl = ServiceHelper.findOrThrow(promptTemplateMapper.selectById(id), "PromptTemplate", id);
        checkReadable(tpl, userId, isAdmin);
        return PromptTemplateVO.from(tpl);
    }

    @Override
    public Result<PromptTemplateVO> createTemplate(PromptTemplateRequest req, Long userId, boolean isAdmin) {
        // v1.1 保存闸门：落库前先过 moderation（覆盖 手写/generate/beautify 三来源）。
        // 闸门在前：先 moderate，再 isPublic 归属检查。Python /moderate 不可用 → 抛 500（强一致，绝不放行）。
        ModerationVerdictVO verdict = pythonAiClient.moderatePrompt(
                buildModerateRequest(req.getSystemPrompt(), req.getMode(), req.getTargetCapabilities()), userId);
        if (!Boolean.TRUE.equals(verdict.getPassed())) {
            return blockedResult(verdict); // blocked → 403，不落库
        }
        boolean makePublic = Boolean.TRUE.equals(req.getIsPublic());
        if (makePublic && !isAdmin) {
            throw new BizException(ResultCode.FORBIDDEN, "仅管理员可创建公共模板");
        }
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setUserId(makePublic ? null : userId); // 公共→NULL；私有→当前用户
        tpl.setName(req.getName()); // v1.1：name 由 req 提供（@NotBlank 必填），不再 autoName
        tpl.setSystemPrompt(req.getSystemPrompt());
        tpl.setMode(req.getMode() != null ? req.getMode() : PromptMode.ACG);
        tpl.setTargetCapabilities(req.getTargetCapabilities());
        tpl.setStatus(CommonStatus.ENABLED);
        promptTemplateMapper.insert(tpl);
        return Result.success(PromptTemplateVO.from(tpl));
    }

    @Override
    public Result<PromptTemplateVO> updateTemplate(Long id, PromptTemplateRequest req, Long userId, boolean isAdmin) {
        PromptTemplateDO existing = ServiceHelper.findOrThrow(promptTemplateMapper.selectById(id), "PromptTemplate", id);
        checkWritable(existing, userId, isAdmin); // 越权 403 / 不存在 404（在闸门之前）
        // v1.1 保存闸门：moderate 新 systemPrompt。mode 直接取 req.getMode()——PromptTemplateRequest.mode
        // 有字段级默认 ACG，故 req.mode 恒非 null，闸门与落库用同一 mode（不会脱钩）。Python 不可用 → 抛 500（强一致）。
        ModerationVerdictVO verdict = pythonAiClient.moderatePrompt(
                buildModerateRequest(req.getSystemPrompt(), req.getMode(), req.getTargetCapabilities()), userId);
        if (!Boolean.TRUE.equals(verdict.getPassed())) {
            return blockedResult(verdict); // blocked → 403，不 update
        }
        boolean makePublic = Boolean.TRUE.equals(req.getIsPublic());
        if (makePublic && !isAdmin) {
            throw new BizException(ResultCode.FORBIDDEN, "仅管理员可开放公共模板");
        }
        if (makePublic) existing.setUserId(null); // admin 开放为公共；非 admin 不改归属
        existing.setName(req.getName());
        existing.setSystemPrompt(req.getSystemPrompt());
        existing.setMode(req.getMode() != null ? req.getMode() : existing.getMode());
        existing.setTargetCapabilities(req.getTargetCapabilities());
        promptTemplateMapper.updateById(existing);
        return Result.success(PromptTemplateVO.from(promptTemplateMapper.selectById(id)));
    }

    /** 构造保存闸门用的 moderate 请求（复用 PromptModerateRequest 字段语义）。 */
    private PromptModerateRequest buildModerateRequest(String systemPrompt, PromptMode mode, List<String> targetCapabilities) {
        PromptModerateRequest m = new PromptModerateRequest();
        m.setSystemPrompt(systemPrompt);
        m.setMode(mode != null ? mode : PromptMode.ACG);
        m.setTargetCapabilities(targetCapabilities);
        return m;
    }

    @Override
    public void deleteTemplate(Long id, Long userId, boolean isAdmin) {
        PromptTemplateDO existing = ServiceHelper.findOrThrow(promptTemplateMapper.selectById(id), "PromptTemplate", id);
        checkWritable(existing, userId, isAdmin);
        promptTemplateMapper.deleteById(id);
    }

    /** 可读性：公共人人可读；私有仅 owner 或 admin，否则 403。 */
    private void checkReadable(PromptTemplateDO tpl, Long userId, boolean isAdmin) {
        if (tpl.getUserId() == null) return; // 公共
        if (isAdmin || tpl.getUserId().equals(userId)) return;
        throw new BizException(ResultCode.FORBIDDEN, "无权访问该模板");
    }

    /** 可写性：公共仅 admin；私有仅 owner 或 admin，否则 403。 */
    private void checkWritable(PromptTemplateDO tpl, Long userId, boolean isAdmin) {
        if (tpl.getUserId() == null && !isAdmin) {
            throw new BizException(ResultCode.FORBIDDEN, "仅管理员可修改公共模板");
        }
        if (tpl.getUserId() != null && !isAdmin && !tpl.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权修改该模板");
        }
    }

    @Override
    public ModerationVerdictVO moderate(PromptModerateRequest req, Long userId) {
        return pythonAiClient.moderatePrompt(req, userId);
    }

    @Override
    public CostEstimateVO estimate(PromptEstimateRequest req, Long userId) {
        return pythonAiClient.estimatePrompt(req, userId);
    }

    @Override
    public void applyToAgent(Long templateId, Long agentId) {
        // Controller 已 @RequireRole("admin") 限定；admin 可取任意模板
        PromptTemplateDO tpl = ServiceHelper.findOrThrow(
                promptTemplateMapper.selectById(templateId), "PromptTemplate", templateId);
        AgentVO vo = agentService.getAgentById(agentId); // 不存在抛 404
        vo.setSystemPrompt(tpl.getSystemPrompt());       // 只搬 system_prompt
        agentService.updateAgent(agentId, vo);           // 复用既有 Python 同步
    }
}
