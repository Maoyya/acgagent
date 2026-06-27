package com.darkness.prompt.service;

import com.darkness.common.model.PromptGenerateRequest;
import com.darkness.common.model.PromptGenerateResponseVO;
import com.darkness.common.result.Result;

/**
 * 系统提示词生成与模板管理服务。
 * 编排 Python generate→moderation→estimate→落库；提供模板 CRUD 与 apply-to-agent。
 */
public interface PromptService {

    /**
     * 生成系统提示词。调 Python generate；moderation 不通过(403)则返回裁决且不落库；
     * 通过则把生成物落库为当前用户的私有模板，返回带 templateId 的响应。
     *
     * @param req    生成请求（userHints/mode/targetCapabilities）
     * @param userId 当前用户 id（落库归属 + 透传 Python）
     * @return 200 成功带响应；403 blocked 带裁决
     */
    Result<PromptGenerateResponseVO> generate(PromptGenerateRequest req, Long userId);

    /**
     * 列出当前用户可见的模板：非 admin=自己的私有+全部公共；admin=全部。可按 mode 过滤。
     *
     * @param userId  当前用户 id
     * @param isAdmin 是否管理员
     * @param mode    可选 mode 过滤
     * @return 可见模板 VO 列表（按创建时间倒序）
     */
    java.util.List<com.darkness.common.model.PromptTemplateVO> listTemplates(Long userId, boolean isAdmin, com.darkness.common.enums.PromptMode mode);

    /**
     * 查询单条模板，做可见性校验。不存在抛 404，越权抛 403。
     *
     * @param id      模板 id
     * @param userId  当前用户 id（用于越权判定）
     * @param isAdmin 是否管理员（admin 可读任意模板）
     * @return 模板 VO
     */
    com.darkness.common.model.PromptTemplateVO getTemplate(Long id, Long userId, boolean isAdmin);

    /**
     * 手建模板。非 admin 建私有(user_id=当前)；admin 可 isPublic=true 建公共(user_id=NULL)。
     * 非 admin 置 isPublic=true 抛 403。
     *
     * @param req     模板内容（name/systemPrompt/mode/isPublic 等）
     * @param userId  当前用户 id（私有模板归属）
     * @param isAdmin 是否管理员
     * @return 创建后的模板 VO
     */
    com.darkness.common.model.PromptTemplateVO createTemplate(com.darkness.common.model.PromptTemplateRequest req, Long userId, boolean isAdmin);

    /**
     * 更新模板（owner 或 admin）。admin 可 isPublic=true 开放为公共。越权抛 403，不存在抛 404。
     *
     * @param id      模板 id
     * @param req     新模板内容
     * @param userId  当前用户 id
     * @param isAdmin 是否管理员
     * @return 更新后的模板 VO
     */
    com.darkness.common.model.PromptTemplateVO updateTemplate(Long id, com.darkness.common.model.PromptTemplateRequest req, Long userId, boolean isAdmin);

    /**
     * 逻辑删除模板（owner 或 admin）。越权抛 403，不存在抛 404。
     *
     * @param id      模板 id
     * @param userId  当前用户 id
     * @param isAdmin 是否管理员
     */
    void deleteTemplate(Long id, Long userId, boolean isAdmin);

    /**
     * 独立 moderation 校验，转发 Python。
     *
     * @param req    待校验请求（systemPrompt + mode）
     * @param userId 当前用户 id（透传 Python 做审计/限流）
     * @return moderation 裁决结果
     */
    com.darkness.common.model.ModerationVerdictVO moderate(com.darkness.common.model.PromptModerateRequest req, Long userId);

    /**
     * 独立消耗估算，转发 Python。
     *
     * @param req    待估算请求（systemPrompt 等）
     * @param userId 当前用户 id（透传 Python）
     * @return token 消耗估算结果
     */
    com.darkness.common.model.CostEstimateVO estimate(com.darkness.common.model.PromptEstimateRequest req, Long userId);

    /**
     * 把模板的 system_prompt 应用到指定 Agent 并同步 Python（admin-only，由 Controller @RequireRole 保证）。
     * 只搬 system_prompt，不动 mode/capabilities；复用 AgentService.updateAgent 的 Python 同步。
     * 模板/Agent 不存在分别抛 404。
     *
     * @param templateId 模板 id（不存在抛 404）
     * @param agentId    目标 Agent id（不存在抛 404）
     */
    void applyToAgent(Long templateId, Long agentId);
}
