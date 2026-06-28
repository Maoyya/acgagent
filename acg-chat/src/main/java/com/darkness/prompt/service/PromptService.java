package com.darkness.prompt.service;

import com.darkness.common.model.PromptBeautifyRequest;
import com.darkness.common.model.PromptBeautifyResponseVO;
import com.darkness.common.model.PromptGenerateRequest;
import com.darkness.common.model.PromptGenerateResponseVO;
import com.darkness.common.result.Result;

/**
 * 系统提示词生成与模板管理服务。
 * <p>
 * v1.1 流程：generate 只返回草稿（不落库）；beautify 用所选 Agent LLM 润色草稿（不校验/不落库）；
 * 落库统一到 create/update，二者落库前过 moderation 闸门（blocked→403 不落库）。
 */
public interface PromptService {

    /**
     * 生成系统提示词草稿（v1.1：不落库、无 templateId）。调 Python generate；
     * moderation 不通过(403)则返回裁决（不返回草稿）；通过则只返回草稿。
     *
     * @param req    生成请求（userHints/mode/targetCapabilities）
     * @param userId 当前用户 id（透传 Python 做审计/限流）
     * @return 200 成功带草稿；403 blocked 带裁决
     */
    Result<PromptGenerateResponseVO> generate(PromptGenerateRequest req, Long userId);

    /**
     * 润色草稿（v1.1 新增）。取所选 Agent 的原始 DO（含真实 apiKey）构造 llm_config，
     * 调 Python beautify 润色 system_prompt。不校验、不落库。
     *
     * @param req    润色请求（草稿 systemPrompt + agentId + mode）
     * @param userId 当前用户 id（透传 Python）
     * @return 200 带润色后文本
     */
    Result<PromptBeautifyResponseVO> beautify(PromptBeautifyRequest req, Long userId);

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
     * 统一「提交」落库（v1.1：覆盖 手写/generate/beautify 三来源）。
     * <p>
     * 保存闸门：落库前调 Python moderate；blocked → {@code Result(403,"blocked",verdict)} 不落库。
     * Python /moderate 不可用 → 抛 BizException(500)（强一致，绝不放行未校验提示词）。
     * 通过则按归属落库：非 admin 建私有(user_id=当前)；admin 可 isPublic=true 建公共(user_id=NULL)；
     * 非 admin 置 isPublic=true 抛 403。
     *
     * @param req     模板内容（name/systemPrompt/mode/isPublic 等）
     * @param userId  当前用户 id（私有模板归属）
     * @param isAdmin 是否管理员
     * @return 200 带创建后的模板 VO；403 blocked 带裁决
     */
    Result<com.darkness.common.model.PromptTemplateVO> createTemplate(com.darkness.common.model.PromptTemplateRequest req, Long userId, boolean isAdmin);

    /**
     * 更新模板（owner 或 admin，v1.1：落库前过 moderation 闸门）。
     * <p>
     * 先取模板 + 校验 owner-or-admin（越权 403、不存在 404）；再保存闸门 moderate 新 systemPrompt，
     * blocked → {@code Result(403,"blocked",verdict)} 不 update；Python 不可用 → 抛 500（强一致）。
     * 通过则 update（admin 可 isPublic=true 开放为公共）。
     *
     * @param id      模板 id
     * @param req     新模板内容
     * @param userId  当前用户 id
     * @param isAdmin 是否管理员
     * @return 200 带更新后的模板 VO；403 blocked 带裁决
     */
    Result<com.darkness.common.model.PromptTemplateVO> updateTemplate(Long id, com.darkness.common.model.PromptTemplateRequest req, Long userId, boolean isAdmin);

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
