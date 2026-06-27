package com.darkness.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.agent.client.PythonAiClient;
import com.darkness.agent.service.AgentService;
import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PromptMode;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.PromptTemplateMapper;
import com.darkness.common.model.AgentVO;
import com.darkness.common.model.CostEstimateVO;
import com.darkness.common.model.ModerationVerdictVO;
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
 * generate：调 Python generate→403 不抛返回裁决→成功落库为当前用户私有模板。
 * 注：userId/isAdmin 由 Controller 从 UserContext 取后透传，便于纯 Mockito 单测。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final PythonAiClient pythonAiClient;
    private final AgentService agentService;

    @Override
    public Result<PromptGenerateResponseVO> generate(PromptGenerateRequest req, Long userId) {
        PromptGenerateOutcome outcome = pythonAiClient.generatePrompt(req, userId);
        if (outcome.isBlocked()) {
            // moderation 不通过：返回 403 + 裁决，不落库、不抛（镜像 Python 契约）
            // 接口返回类型为 Result<PromptGenerateResponseVO>，但 403 的 data 是 ModerationVerdictVO，
            // 由调用方按 code 判断；此处以 Result.error 构造后 unchecked 强转，保持与 test 契约一致。
            return blockedResult(outcome.getVerdict());
        }
        PromptGenerateResponseVO resp = outcome.getSuccess();
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setUserId(userId); // 生成物即当前用户的私有模板
        tpl.setName(autoName(req.getUserHints(), req.getMode()));
        tpl.setSystemPrompt(resp.getSystemPrompt());
        tpl.setMode(req.getMode() != null ? req.getMode() : PromptMode.ACG);
        tpl.setTargetCapabilities(req.getTargetCapabilities());
        if (resp.getEstimate() != null) {
            tpl.setEstPromptTokens(resp.getEstimate().getPromptTokens());
        }
        tpl.setStatus(CommonStatus.ENABLED);
        promptTemplateMapper.insert(tpl);
        resp.setTemplateId(tpl.getId()); // Java 追加，Python 响应不带
        return Result.success(resp);
    }

    /** 403 响应构造：code=403,message="blocked",data=裁决。unchecked 强转以适配泛型签名。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Result<PromptGenerateResponseVO> blockedResult(com.darkness.common.model.ModerationVerdictVO verdict) {
        // 用裸类型构造后由方法签名归一化，运行时 data 即 verdict（见 test：r.getData() isSameAs(v)）
        return new Result(ResultCode.FORBIDDEN.getCode(), "blocked", verdict);
    }

    /** 自动取名：首条 hint 截断 32 字；空则 "提示词-{mode}"。 */
    private String autoName(List<String> hints, PromptMode mode) {
        if (hints != null && !hints.isEmpty()) {
            String first = hints.get(0);
            if (first != null && !first.isBlank()) {
                return first.length() <= 32 ? first : first.substring(0, 32);
            }
        }
        return "提示词-" + (mode != null ? mode.getValue() : "acg");
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
    public PromptTemplateVO createTemplate(PromptTemplateRequest req, Long userId, boolean isAdmin) {
        boolean makePublic = Boolean.TRUE.equals(req.getIsPublic());
        if (makePublic && !isAdmin) {
            throw new BizException(ResultCode.FORBIDDEN, "仅管理员可创建公共模板");
        }
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setUserId(makePublic ? null : userId); // 公共→NULL；私有→当前用户
        tpl.setName(req.getName());
        tpl.setSystemPrompt(req.getSystemPrompt());
        tpl.setMode(req.getMode() != null ? req.getMode() : PromptMode.ACG);
        tpl.setTargetCapabilities(req.getTargetCapabilities());
        tpl.setStatus(CommonStatus.ENABLED);
        promptTemplateMapper.insert(tpl);
        return PromptTemplateVO.from(tpl);
    }

    @Override
    public PromptTemplateVO updateTemplate(Long id, PromptTemplateRequest req, Long userId, boolean isAdmin) {
        PromptTemplateDO existing = ServiceHelper.findOrThrow(promptTemplateMapper.selectById(id), "PromptTemplate", id);
        checkWritable(existing, userId, isAdmin);
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
        return PromptTemplateVO.from(promptTemplateMapper.selectById(id));
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
