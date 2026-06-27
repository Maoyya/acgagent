# 2026-06-21 系统提示词模板功能（Java 侧）

## 变更原因
为 acgagent-ai「系统提示词生成」提供 Java 侧落地：调 Python prompt 接口 + 模板库（公共/私有双维度）CRUD + apply-to-agent。对应 spec `docs/superpowers/specs/2026-06-21-prompt-generation-java-design.md`。

## 影响范围
- **数据库**：新增表 `prompt_template`（acg-user/schema.sql）。
- **API（新增，/api/prompts）**：generate / moderate / estimate / templates CRUD / apply-to-agent。
- **网关**：`acg-gateway.yaml` 的 agent-service 路由新增 `/api/prompts/**`。
- **公共代码**：`UserContext` 新增 `getRoles()/isAdmin()`；`PythonAiClient` 新增 Prompt 段。

## 变更前后对比
- 路由 predicate：`/api/agents/**,/api/chat/**` → 追加 `,/api/prompts/**`。
- 新表 `prompt_template`：user_id=NULL 公共 / 非 NULL 私有。
- 403 语义：generate 被 moderation 拦截时返回 `Result(403,"blocked",ModerationVerdict)`，区别于鉴权 403(Forbidden)。

## 注意点
- **Python 侧（acgagent-ai）prompt 接口尚未实现**；generate/moderate/estimate 在 Python 落地前会返回 503/500，模板 CRUD 与 apply-to-agent 可独立使用。
- `/api/prompts/**` 需登录（非白名单）；apply-to-agent 仅 admin。
- `UserContext.isAdmin()` 依赖网关 `X-User-Roles` 头（JwtAuthFilter 已注入）。
- 部署时必须在 Nacos 更新 `acg-gateway.yaml`，否则 /api/prompts 网关 404。
