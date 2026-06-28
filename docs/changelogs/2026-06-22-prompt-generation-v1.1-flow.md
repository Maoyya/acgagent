# 2026-06-22 系统提示词生成 v1.1 流程重构

## 变更原因
v1.0（`693d098`）generate 自动落库、无润色、保存不校验。用户重新定义产品流程：generate 只出草稿 → 可选「选 Agent 用其 LLM 润色(beautify)」或「直接提交」→ **保存前统一 moderation 闸门**避免非法提示词传入。路由层确认放弃（用户改为手动选 Agent beautify）。

## 影响范围
- **API 行为变更（部分不兼容）**：
  - `POST /api/prompts/generate` —— **不再自动落库**，只返回草稿；响应**移除 `templateId`**。（不兼容：前端原依赖 templateId 需改）
  - `POST /api/prompts/beautify` —— **新增**：草稿 + agentId → 用所选 Agent 的 LLM 润色 → 返回文本（不校验/不落库）。
  - `POST/PUT /api/prompts/templates`（create/update）—— **保存前 moderation 闸门**：blocked 返回 `Result(403,"blocked",ModerationVerdict)` 不落库；service 返回类型 `PromptTemplateVO` → `Result<PromptTemplateVO>`。（不兼容：service/Controller 签名变更；前端需处理 403+verdict）
- **新依赖**：模板 create/update 现依赖 Python `/api/v1/prompts/moderate` 在线（强一致：Python 挂则保存失败，绝不放行未校验提示词）。CRUD 不再完全 Python 无关。
- **数据库**：无表结构变更（prompt_template 不变）。
- **网关**：无变更（/api/prompts/** 路由 v1.0 已加；/beautify 同前缀自动覆盖）。

## 变更前后对比
| 端点 | v1.0（693d098） | v1.1 |
|---|---|---|
| generate | 自动落库私有模板 + 返回 templateId | 只返回草稿，不落库，无 templateId |
| beautify | — | 新增：所选 Agent LLM 润色，不落库 |
| create/update | 直接存，不校验 | 保存前 moderate，blocked→403 不落库 |
| CRUD 与 Python | 无关（纯 MySQL） | create/update 依赖 Python /moderate（强一致） |

## 注意点
- **跨仓库依赖**：beautify 与保存闸门依赖 **Python spec v1.1**（acgagent-ai 新增 `/api/v1/prompts/beautify` + 接受 `llm_config`）。Python 侧尚未实现，故 generate/beautify/moderate/estimate 端到端 blocked；模板 create/update 的闸门也需 Python 在线。
- **Python spec v1.1 已修订**（在 acgagent-ai 仓库 `docs/superpowers/specs/2026-06-21-prompt-generation-design.md`），需在该仓库另行提交。
- **beautify 取真实 apiKey**：Java 用 `AgentMapper.selectById` 取原始 AgentDO（含真实 apiKey）构造 llm_config；**不可**用 `AgentService.getAgentById`（返回脱敏 VO，Python 会拿到 `******`）。
- 403-blocked 与 403-forbidden 同为 HTTP 200+code=403，前端按 `message`（"blocked" vs "权限不足"）+ `data`（verdict vs 无）区分。
- 测试：36 单测全绿（PythonAiClient 15 + PromptServiceImpl 21），mock PythonAiClient（Python 未实现）。
