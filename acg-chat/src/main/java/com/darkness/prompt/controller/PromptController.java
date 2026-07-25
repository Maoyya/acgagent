package com.darkness.prompt.controller;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.enums.PromptMode;
import com.darkness.common.model.CostEstimateVO;
import com.darkness.common.model.GenerateStreamEvent;
import com.darkness.common.model.ModerationVerdictVO;
import com.darkness.common.model.PromptBeautifyRequest;
import com.darkness.common.model.PromptBeautifyResponseVO;
import com.darkness.common.model.PromptEstimateRequest;
import com.darkness.common.model.PromptGenerateRequest;
import com.darkness.common.model.PromptModerateRequest;
import com.darkness.common.model.PromptTemplateRequest;
import com.darkness.common.model.PromptTemplateVO;
import com.darkness.common.result.Result;
import com.darkness.common.util.UserContext;
import com.darkness.prompt.service.PromptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 系统提示词生成与模板管理 REST 控制器，提供 /api/prompts 下的接口。
 * 普通用户：生成/手建私有模板、读公共+自己私有、moderate/estimate；
 * 管理员：额外可开放公共模板、apply-to-agent。userId/isAdmin 由 UserContext 取后透传 Service。
 */
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    /**
     * 流式生成系统提示词草稿（不落库、无 templateId、不做 moderation）。
     * 直接转发 Python generate 的 SSE 流：content 事件流式 token，done 事件带消耗估算，error 事件带错误信息。
     * POST /api/prompts/generate（需登录），produces text/event-stream
     *
     * @param req 生成请求（userHints/mode/targetCapabilities）
     * @return GenerateStreamEvent 流（content → ... → done/error）；合规性由后续 create/update 的保存闸门统一校验
     */
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<GenerateStreamEvent> generate(@Valid @RequestBody PromptGenerateRequest req) {
        return promptService.generate(req, UserContext.getUserId());
    }

    /**
     * 润色草稿（v1.1 新增）。用所选 Agent 的 LLM 润色草稿 system_prompt，返回润色后文本。
     * POST /api/prompts/beautify（需登录）
     *
     * @param req 润色请求（草稿 systemPrompt + agentId + mode）
     * @return 200 带润色后文本；不校验、不落库（合规性在后续 create 的保存闸门统一校验）
     */
    @PostMapping("/beautify")
    public Result<PromptBeautifyResponseVO> beautify(@Valid @RequestBody PromptBeautifyRequest req) {
        return promptService.beautify(req, UserContext.getUserId());
    }

    /**
     * 独立 moderation 校验（复检已存/手写 system_prompt）。
     * POST /api/prompts/moderate（需登录）
     *
     * @param req moderation 请求（systemPrompt 必填）
     * @return 裁决视图对象（allowed=true 通过；false 表示命中风险类别）
     */
    @PostMapping("/moderate")
    public Result<ModerationVerdictVO> moderate(@Valid @RequestBody PromptModerateRequest req) {
        return Result.success(promptService.moderate(req, UserContext.getUserId()));
    }

    /**
     * 独立消耗估算。
     * POST /api/prompts/estimate（需登录）
     *
     * @param req 估算请求（systemPrompt 必填，agentId 可选）
     * @return 消耗估算视图对象（token 数、估算费用等）
     */
    @PostMapping("/estimate")
    public Result<CostEstimateVO> estimate(@Valid @RequestBody PromptEstimateRequest req) {
        return Result.success(promptService.estimate(req, UserContext.getUserId()));
    }

    /**
     * 列出可见模板（非 admin=公共+自己私有；admin=全部），可按 mode 过滤。
     * GET /api/prompts/templates（需登录）
     *
     * @param mode 可选过滤条件（PROMPT_MODE 枚举），为 null 时不过滤
     * @return 当前用户可见的模板视图对象列表
     */
    @GetMapping("/templates")
    public Result<List<PromptTemplateVO>> list(@RequestParam(required = false) PromptMode mode) {
        return Result.success(promptService.listTemplates(UserContext.getUserId(), UserContext.isAdmin(), mode));
    }

    /**
     * 查询单条模板。越权 403，不存在 404。
     * GET /api/prompts/templates/{id}（需登录）
     *
     * @param id 模板主键
     * @return 模板视图对象
     */
    @GetMapping("/templates/{id}")
    public Result<PromptTemplateVO> get(@PathVariable Long id) {
        return Result.success(promptService.getTemplate(id, UserContext.getUserId(), UserContext.isAdmin()));
    }

    /**
     * 统一「提交」落库（v1.1：手写/generate/beautify 产物经此落库；用户建私有，admin 可 isPublic=true 建公共）。
     * 落库前过 moderation 闸门，blocked → code=403、message="blocked"、data=ModerationVerdict（不落库）。
     * POST /api/prompts/templates（需登录）
     *
     * @param req 模板请求（name、systemPrompt 必填；isPublic 仅 admin 可置 true）
     * @return 200 带创建后的模板视图对象；403 blocked 带裁决
     */
    @PostMapping("/templates")
    public Result<PromptTemplateVO> create(@Valid @RequestBody PromptTemplateRequest req) {
        // service 现返回 Result（可能含 403+verdict），直接透传，不再包裹 Result.success
        return promptService.createTemplate(req, UserContext.getUserId(), UserContext.isAdmin());
    }

    /**
     * 更新模板（owner/admin；admin 可 isPublic=true 开放为公共；落库前过 moderation 闸门）。
     * PUT /api/prompts/templates/{id}（需登录）
     *
     * @param id  模板主键
     * @param req 需要更新的模板字段
     * @return 200 带更新后的模板视图对象；403 blocked 带裁决
     */
    @PutMapping("/templates/{id}")
    public Result<PromptTemplateVO> update(@PathVariable Long id, @Valid @RequestBody PromptTemplateRequest req) {
        // service 现返回 Result（可能含 403+verdict），直接透传，不再包裹 Result.success
        return promptService.updateTemplate(id, req, UserContext.getUserId(), UserContext.isAdmin());
    }

    /**
     * 逻辑删除模板（owner/admin）。
     * DELETE /api/prompts/templates/{id}（需登录）
     *
     * @param id 模板主键
     * @return 无 data 的成功响应
     */
    @DeleteMapping("/templates/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptService.deleteTemplate(id, UserContext.getUserId(), UserContext.isAdmin());
        return Result.success();
    }

    /**
     * 把模板 system_prompt 应用到 Agent 并同步 Python。POST /api/prompts/templates/{id}/apply/{agentId}（仅管理员）
     *
     * @param id      模板 id
     * @param agentId 目标 Agent id
     */
    @PostMapping("/templates/{id}/apply/{agentId}")
    @RequireRole("admin")
    public Result<Void> apply(@PathVariable Long id, @PathVariable Long agentId) {
        promptService.applyToAgent(id, agentId);
        return Result.success();
    }
}
