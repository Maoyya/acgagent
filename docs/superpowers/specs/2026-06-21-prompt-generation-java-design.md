# 系统提示词生成功能 — Java 侧设计文档（Spec）

> 版本：1.1.0 | 日期：2026-06-21（v1.1 修订 2026-06-22） | 所属项目：acgagent（Java）
> 状态：待评审 → 通过后进入 writing-plans
> 关联：Python 侧 spec `acgagent-ai/docs/superpowers/specs/2026-06-21-prompt-generation-design.md`

> **v1.1 修订（2026-06-22）—— 流程重构，权威 delta：**
> 1. **generate 不再自动落库**：只返回草稿（system_prompt + moderation + estimate），**不带 templateId、不写 DB**。落库统一到「提交(create)」。
> 2. **新增 beautify（润色）**：`POST /api/prompts/beautify`，入参 草稿 system_prompt + agent_id → 取该 Agent 的 LLM 配置 → 调 Python `/api/v1/prompts/beautify`（用传入 llm_config）润色 → 返回润色后文本。**不校验、不落库**。
> 3. **保存前 moderation 闸门**：`create`/`update` 在落库前调 Python `/moderate`；blocked → `Result(403,"blocked",verdict)` **不落库**。这是「避免非法提示词传入」的统一闸门，覆盖 generate/beautify/手写三条来源。
> 4. **Python 不可用时强一致**：保存闸门依赖 Python `/moderate`；Python 挂 → 保存失败（明确报错），**绝不放行未校验提示词**（best-effort 会漏过非法内容，违背闸门初衷）。
> 5. **「直接提交」= 手建 create**：用户手写 system_prompt（不经任何 LLM）→ create 走 moderation 闸门 → 落库。
> 6. **路由层明确不做**（之前 §2.2 已是未来项，现确认放弃）：用户手动选 Agent 来 beautify，不做「后端路由 Agent」。
>
> 修订影响端点行为：generate（去 templateId/不落库）、新增 beautify、create/update（加 403 闸门）。决策 #8 失效（generate 不再落库），新增决策 #12–#15。

---

## 1. 目标与背景

Python 侧（acgagent-ai）spec 定义了「系统提示词生成」能力：用零散自然语言要求，经 meta-LLM 组装成 Agent `system_prompt`，并做内容合规校验、消耗估算、用户偏好记录。该 spec 明确把**模板 CRUD + MySQL + 后台管理**划归 Java 侧，Python 只暴露生成/校验/估算/偏好能力。

本文档设计 Java 侧（acgagent）对应逻辑：调用 Python 的 prompt 接口、把生成物落库为模板、提供双维度（公共/私有）模板库的 CRUD、以及把模板应用到 Agent。

### 1.1 关键决策（已与用户确认）

| # | 决策点 | 选择 | 理由 |
|---|---|---|---|
| 1 | 范围 | **全链路 + apply-to-agent**：generate + 模板 CRUD + /moderate /estimate 封装 + apply 到 Agent | 用户选最大范围 |
| 2 | 模板归属 | **混合双维度**：公共库(user_id=NULL，管理员开放) + 用户私有(user_id=非空) | 用户明确要两个维度 |
| 3 | Agent 归属 | **永远管理员配好，用户不感知，后端路由 Agent** | 用户明确产品模型 |
| 4 | apply-to-agent 授权 | **一律 admin-only** | Agent 是 admin-only，改 system_prompt + 同步 Python 是管理员操作；不扩范围到 per-user Agent |
| 5 | 403 blocked 返回 | **镜像 Python**：`Result(403,"blocked",ModerationVerdict)`，不抛、不落库 | Python spec 1.1#5 写死 |
| 6 | service 层判 admin | 给 `UserContext` 加 `getRoles()/isAdmin()`（读 `X-User-Roles` 头） | 复用，不重复 RoleAuthAspect 逻辑 |
| 7 | 手建端点 | **保留** `POST /api/prompts/templates`（不经 LLM 直接存） | 用户确认保留 |
| 8 | ~~generate 落库归属~~ | **v1.1 失效**：generate 不再自动落库（见 #12）。落库统一到 create | 流程重构 |
| 9 | apply 搬运字段 | **只搬 system_prompt**，不动 mode/capabilities；复用 `AgentService.updateAgent` 同步 Python | 模板=提示词库，最小改动 |
| 10 | 持久化范围 | 只缓存 `est_prompt_tokens`，不存完整 moderation 裁决（复检走 /moderate） | YAGNI |
| 11 | Python 侧状态 | **尚未实现**（spec 待评审、`app/**/prompt*.py` 不存在）→ Java 按契约写 + 单测 mock PythonAiClient | Fail Loud，已与用户确认 |
| 12 | generate 落库（v1.1） | **generate 不自动落库**，只返回草稿（无 templateId）；落库统一到 create | 流程图落库在最末；解耦生产与保存 |
| 13 | beautify（v1.1） | 新增 `POST /api/prompts/beautify`：草稿 + agent_id → 用该 Agent 的 LLM（传 llm_config 给 Python）润色 → 返回文本；不校验/不落库 | 用户选 Agent 美化；apply 仍 admin-only |
| 14 | 保存前 moderation 闸门（v1.1） | create/update 落库前必调 Python `/moderate`；blocked → 403 不落库。覆盖 generate/beautify/手写三来源 | 「避免非法提示词传入」统一闸门 |
| 15 | Python 不可用（v1.1） | **强一致**：保存闸门依赖 Python `/moderate`，Python 挂 → 保存失败报错，不放行 | 闸门为安全控制，best-effort 会漏过非法内容 |

---

## 2. 范围

### 2.1 本期实现（Java 侧）

1. **生成**：`POST /api/prompts/generate` → 调 Python generate → 处理 403 → **只返回草稿（不落库、无 templateId）**
2. **润色 beautify（v1.1）**：`POST /api/prompts/beautify` → 用所选 Agent 的 LLM 润色草稿 → 返回文本（不校验/不落库）
3. **模板 CRUD（含保存前 moderation 闸门）**：list / get / create / update / delete；**create/update 落库前调 `/moderate`，blocked→403 不落库**；admin 可开放为公共
4. **moderate / estimate 封装**：转发 Python（moderate = 保存闸门；estimate 独立估算）
5. **apply-to-agent**（admin）：把模板 system_prompt 写入 Agent + 同步 Python

### 2.2 不在本期范围

- Python 侧任何实现（归属 acgagent-ai）
- 「后端路由 Agent」路由层（用户无感、后端选 Agent）—— **v1.1 确认放弃**；用户改为**手动选 Agent 做 beautify**
- Agent 改为 per-user（明确不动）
- moderation 多裁判投票、生成后真实 usage、偏好推荐接口（Python 二期）
- 前端模板选择 UI

---

## 3. 架构与文件分布（严格沿用现有包结构）

```
acg-common
├─ entity/   PromptTemplateDO              （新，映射 prompt_template 表）
├─ mapper/   PromptTemplateMapper          （新，继承 BaseMapper）
├─ enums/    PromptMode {ACG, COMPLIANT}   （新，@JsonValue 序列化为 "acg"/"compliant"）
├─ model/    PromptGenerateRequest         （新，→ Python）
│            PromptModerateRequest         （新，→ Python）
│            PromptEstimateRequest         （新，→ Python）
│            PromptGenerateResponseVO      （新，← Python；v1.1 起**不带 templateId**）
│            PromptBeautifyRequest         （新 v1.1，→ Python beautify：草稿 + 所选 Agent 的 llm_config）
│            PromptBeautifyResponseVO      （新 v1.1，← Python beautify：润色后 system_prompt）
│            ModerationVerdictVO           （新，← Python）
│            CostEstimateVO                （新，← Python）
│            PromptGenerateOutcome         （新，generate 的成功/ blocked 值对象）
│            PromptTemplateVO              （新，← DO，对外）
│            PromptTemplateRequest         （新，手建/更新入参，含可选 public）
│            PromptApplyRequest            （新，apply 入参，预留）
├─ util/     UserContext                   （改：加 getRoles()/isAdmin()）
└─ result/   Result                        （不改：403 用 public 全参构造 new Result<>(403,"blocked",verdict)）

acg-chat
├─ agent/client/PythonAiClient             （改：加 Prompt 段 —— generatePrompt/beautifyPrompt/moderatePrompt/estimatePrompt）
├─ prompt/service/PromptService            （新，接口）
├─ prompt/service/impl/PromptServiceImpl   （新）
└─ prompt/controller/PromptController      （新，/api/prompts）

acg-user/src/main/resources/db/schema.sql  （改：加 prompt_template 建表）
```

> 新建 `com.darkness.prompt` 域包（与 `tool`/`kb` 平级，符合 CLAUDE.md「按业务域分包」）。DO/Mapper/VO/枚举落 acg-common（与 AgentDO 一致）。

---

## 4. 数据模型

### 4.1 建表（追加到 `acg-user/.../db/schema.sql`）

```sql
CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    user_id BIGINT COMMENT '归属用户 ID；NULL=公共模板(管理员开放)，非 NULL=该用户的私有模板',
    name VARCHAR(128) NOT NULL COMMENT '模板名称，生成时自动取首条 hint 截断，可后续修改',
    system_prompt TEXT NOT NULL COMMENT '生成的系统提示词正文',
    mode VARCHAR(16) NOT NULL DEFAULT 'acg' COMMENT '生成模式：acg-二次元, compliant-合规',
    target_capabilities JSON COMMENT '能力标签数组，用于"不超能力"约束，如 ["chat","rag"]',
    est_prompt_tokens INT COMMENT '生成时估算的 prompt token 数(缓存)，未估算为 NULL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) COMMENT '系统提示词模板表：公共库(user_id=NULL)+用户私有(user_id=非空)';
```

> 沿用现有 DDL 约定：`BIGINT AUTO_INCREMENT`、列级中文 COMMENT、`status/deleted/created_at/updated_at` 标配、snake_case（经 `map-underscore-to-camel-case` 映射为 camelCase）。

### 4.2 PromptMode 枚举（`acg-common.enums`）

```java
public enum PromptMode {
    ACG("acg"),
    COMPLIANT("compliant");
    // Jackson @JsonValue 输出小写字符串，匹配 Python 的 PromptMode(str, Enum)
    private final String value;
    // 含 @JsonValue getValue()，及 @JsonCreator 反序列化
}
```

DB 存 `value` 字符串（VARCHAR(16)）。mode 非法时 Jackson 绑定失败 → 400。

---

## 5. PythonAiClient 契约层（关键：403 不抛）

现有 `extractData` 在 `code != 200` 时抛 `BizException(code)`。但 generate 的 **403 是合法业务结果**，必须带 data 返回。故新增**专用方法**，不复用 extractData：

```java
// ==================== Prompt ====================

/**
 * 调 Python POST /api/v1/prompts/generate。
 * code=200 → success 响应；code=403 → blocked 裁决(不抛)；其余 → BizException(code)。
 * body 用 snake_case（沿用 buildCreateBody 的 LinkedHashMap 显式构造方式）。
 */
public PromptGenerateOutcome generatePrompt(PromptGenerateRequest req, Long userId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("user_hints", req.getUserHints());
    body.put("mode", req.getMode().getValue());
    if (req.getTargetCapabilities() != null) body.put("target_capabilities", req.getTargetCapabilities());

    String json = pythonWebClient.post()
            .uri("/api/v1/prompts/generate")
            .header("X-API-Key", props.getApiKey())
            .header("X-User-Id", String.valueOf(userId))
            .header("Content-Type", "application/json")
            .bodyValue(body)
            .retrieve().bodyToMono(String.class)
            .timeout(Duration.ofMillis(props.getReadTimeout())).block();

    JsonNode root = readTree(json);
    int code = root.path("code").asInt(200);
    JsonNode data = root.path("data");
    if (code == 200) {
        return PromptGenerateOutcome.success(treeToValue(data, PromptGenerateResponseVO.class));
    } else if (code == 403) {
        return PromptGenerateOutcome.blocked(treeToValue(data, ModerationVerdictVO.class));
    }
    throw new BizException(code, root.path("message").asText("Python generate prompt failed"));
}

/**
 * 调 Python POST /api/v1/prompts/beautify（v1.1）。
 * 用所选 Agent 的 LLM（llm_config 来自 agent）润色草稿；code != 200 走 extractData 抛。
 * 不校验、不落库（保存闸门在 Java create/update 的 moderate）。
 */
public PromptBeautifyResponseVO beautifyPrompt(PromptBeautifyRequest req, AgentDO agent, Long userId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("system_prompt", req.getSystemPrompt());
    body.put("mode", req.getMode() != null ? req.getMode().getValue() : PromptMode.ACG.getValue());
    body.put("llm_config", buildLlmConfigMap(agent));   // 复用 buildCreateBody 里 llm_config 的构造逻辑
    return extractData(postJson("/api/v1/prompts/beautify", body, userId), PromptBeautifyResponseVO.class);
}

/** 调 Python POST /api/v1/prompts/moderate，返回裁决（code != 200 走 extractData 抛）。 */
public ModerationVerdictVO moderatePrompt(PromptModerateRequest req, Long userId) { ... extractData ... }

/** 调 Python POST /api/v1/prompts/estimate，返回消耗估算（code != 200 走 extractData 抛）。 */
public CostEstimateVO estimatePrompt(PromptEstimateRequest req, Long userId) { ... extractData ... }
```

### 5.1 Python-facing VO 的命名映射

- **请求体**：PythonAiClient 用 `LinkedHashMap` 显式构造 snake_case 键（与 `buildCreateBody` 一致），Java VO 保持 camelCase。
- **响应解析**：从 Python 解析的 VO（`PromptGenerateResponseVO`/`ModerationVerdictVO`/`CostEstimateVO`）字段用 `@JsonProperty` 显式映射 snake_case → camelCase（如 `@JsonProperty("user_hints") List<String> userHints`、`prompt_tokens`/`est_completion_tokens`/`violated_rules`），不依赖全局命名策略，最稳妥。

### 5.2 PromptGenerateOutcome（`acg-common.model`）

```java
/** generate 的结果：要么成功带响应，要么被 moderation 拦截带裁决。 */
public class PromptGenerateOutcome {
    private final boolean blocked;
    private final PromptGenerateResponseVO success;   // blocked=false 时有值
    private final ModerationVerdictVO verdict;        // blocked=true 时有值
    public static PromptGenerateOutcome success(PromptGenerateResponseVO s) { ... }
    public static PromptGenerateOutcome blocked(ModerationVerdictVO v) { ... }
    public boolean isBlocked() { return blocked; }
    // getters
}
```

---

## 6. 权限模型（双维度落地）

### 6.1 UserContext 增强（`acg-common.util`，surgical）

```java
/** 读取 Gateway 注入的 X-User-Roles 头（逗号分隔），与 RoleAuthAspect 同源。 */
public static Set<String> getRoles() {
    HttpServletRequest request = getCurrentRequest();
    if (request == null) return Set.of();
    String header = request.getHeader("X-User-Roles");
    if (header == null || header.isBlank()) return Set.of();
    return Arrays.stream(header.split(",")).map(String::trim)
            .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
}
/** 当前用户是否含 admin 角色。 */
public static boolean isAdmin() { return getRoles().contains("admin"); }
```

> `X-User-Roles` 头由 Gateway 注入（RBAC 既有链路依赖它，可安全假设）。

### 6.2 可见性与操作矩阵

| 操作 | 公共模板(user_id=NULL) | 别人的私有 | 自己的私有 |
|---|---|---|---|
| 读 / 列表 | 所有登录用户 | 仅 admin | 自己 |
| 创建(create = 统一提交) | — | — | 自己；admin 可置 `public=true` 建公共（v1.1：generate 不再创建，落库统一到 create） |
| 改 / 删 | 仅 admin | 仅 admin | 自己 |

**owner-or-admin 判断**（service 内）：`currentUserId.equals(tpl.getUserId()) || UserContext.isAdmin()`，否则抛 `BizException(FORBIDDEN)`。

> 公共模板(user_id=NULL)的改/删仅 admin；普通用户对公共模板只读。

### 6.3 列表查询逻辑

- 非 admin：`WHERE deleted=0 AND (user_id = ?当前用户 OR user_id IS NULL)`（自己的私有 + 全部公共）
- admin：`WHERE deleted=0`（全部，含所有人私有 + 公共）
- 可按 mode 过滤、按 created_at 倒序

---

## 7. API 端点（全链路）

所有端点挂在 `/api/prompts`，经 Gateway 鉴权（需登录）。`userId` 由 `UserContext.getUserId()` 取（Gateway 注入 `X-User-Id`）。

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/prompts/generate` | 登录 | 生成**草稿**（不落库、无 templateId）；403 返回 verdict |
| POST | `/api/prompts/beautify` | 登录 | **v1.1**：草稿 + agent_id → 用该 Agent LLM 润色 → 返回文本（不校验/不落库） |
| POST | `/api/prompts/moderate` | 登录 | 封装 Python，复检 system_prompt（亦供前端独立调用） |
| POST | `/api/prompts/estimate` | 登录 | 封装 Python，独立消耗估算 |
| GET | `/api/prompts/templates` | 登录 | 列表：公共 + 自己私有（admin 看全部） |
| GET | `/api/prompts/templates/{id}` | 登录 | 单条（可见性校验：越权 403，不存在 404） |
| POST | `/api/prompts/templates` | 登录 | **统一「提交」**：手写/generate/beautify 产物经此落库；**落库前 moderate，blocked→403 不落库**；用户建私有/admin 可 `public=true` 公共 |
| PUT | `/api/prompts/templates/{id}` | owner/admin | 改；**落库前 moderate，blocked→403**；admin 可 `public=true` 开放为公共 |
| DELETE | `/api/prompts/templates/{id}` | owner/admin | 逻辑删 |
| POST | `/api/prompts/templates/{id}/apply/{agentId}` | **admin** | apply-to-agent |

> `public` 是 `PromptTemplateRequest` 的可选布尔字段，仅 admin 可置 true（→ `user_id=NULL`）；普通用户置 true 直接 403。把「开放为公共」折进 create/update，少一个 promote 端点。

### 7.1 generate 请求 / 响应示例

请求：
```jsonc
{ "userHints": ["要一个毒舌但专业的客服","回答偏简洁"], "mode": "acg", "targetCapabilities": ["chat","rag"] }
// Header: Authorization, X-User-Id(网关注入)
```

成功响应（code=200，**只返回草稿，不落库、无 templateId**）：
```jsonc
{
  "code": 200, "message": "success",
  "data": {
    "systemPrompt": "你是一名...",
    "mode": "acg",
    "moderation": { "passed": true, "violatedRules": [], "reasons": [], "confidence": 0.95, "mode": "acg" },
    "estimate": { "promptTokens": 120, "estCompletionTokens": 0, "model": "deepseek-chat" }
  }
}
```

被拦截响应（code=403，**不落库**）：
```jsonc
{
  "code": 403, "message": "blocked",
  "data": { "passed": false, "violatedRules": ["禁止二次元风格..."], "reasons": ["包含动漫夸张人设"], "confidence": 0.9, "mode": "compliant" }
}
```

---

## 8. 核心逻辑

### 8.1 generate 编排（`PromptServiceImpl.generate`，v1.1：**不落库**）

```
generate(req, userId):
  outcome = pythonAiClient.generatePrompt(req, userId)  // LLM 生成 + 内部 moderation，403 不抛
  if outcome.isBlocked():
      return new Result<>(403, "blocked", outcome.getVerdict())   // 草稿本身违规 → 不返回草稿
  return Result.success(outcome.getSuccess())           // 只返回草稿，不落库、无 templateId
```

> 落库统一到 create（§8.4）：前端拿草稿（或 beautify 后/手写）→ 调 `POST /api/prompts/templates`。
> `autoName` 辅助方法保留，改由 create 在落库时取名（首条 hint 截断 32 字，空则「提示词-{mode}」）。

### 8.2 beautify 编排（`PromptServiceImpl.beautify`，v1.1 新增）

```
beautify(req, userId):   // req: 草稿 systemPrompt + agentId + mode
  agent = agentMapper.selectById(req.getAgentId())         // 不存在 → BizException(NOT_FOUND)
  resp = pythonAiClient.beautifyPrompt(req, agent, userId) // 用该 Agent 的 LLM 润色，不校验
  return Result.success(resp)                              // 返回润色后文本，不落库
```

> beautify 不做 moderation（Python 端也不校验）；合规性在后续 create/update 的保存闸门统一校验。
> Agent 仍 admin 配置、apply 仍 admin-only；用户此处只是「借用 Agent 的 LLM 做润色」，不改变 Agent 归属模型。

### 8.3 apply-to-agent（admin-only，`PromptServiceImpl.applyToAgent`）

```
applyToAgent(templateId, agentId):   // Controller 标 @RequireRole("admin")
  tpl = loadTemplate(templateId)                         // admin 可取任意
  AgentVO vo = agentService.getAgentById(agentId)        // 返回脱敏 VO，不存在抛 404
  vo.setSystemPrompt(tpl.getSystemPrompt())              // 只搬 system_prompt
  agentService.updateAgent(agentId, vo)                  // 复用既有 Python 同步路径
  return Result.success()
```

> 复用 `AgentServiceImpl.updateAgent` 自带的 `pythonAiClient.updateAgent` 同步，不另写同步逻辑。
> `apiKey` 脱敏值回传问题：`AgentService.updateAgent` 已处理 "******" 保留原值，故 from(脱敏 VO) 回写安全。

### 8.4 CRUD（`PromptServiceImpl`，带归属鉴权 + **保存前 moderation 闸门**）

- `listTemplates()`：按 §6.3 查询，DO → VO（`PromptTemplateVO`）。
- `getTemplate(id)`：取出后做可见性校验（公共 / 自己 / admin），越权 `FORBIDDEN`，不存在 `NOT_FOUND`。
- `createTemplate(req)`（= 统一「提交」，覆盖 手写/generate/beautify 三来源）：
  - **保存闸门**：`moderate(systemPrompt, mode)` → 若 `!passed` → `return new Result<>(403,"blocked",verdict)`，**不落库**。
  - 通过则：非管理员 `user_id=当前用户`（`public==true`→`FORBIDDEN`）；管理员 `public==true`→`user_id=NULL`（公共）。
  - name 缺省时 autoName 取名；必填校验 name、systemPrompt 非空。
- `updateTemplate(id, req)`：先取模板，校验 owner-or-admin；**保存闸门**：moderate 新 systemPrompt，blocked→403 不 update；通过则 update（admin 可改 `public`）。
- `deleteTemplate(id)`：校验 owner-or-admin；逻辑删（deleted=1）。删除不引入新内容，不走闸门。

> 保存闸门使 create/update 依赖 Python `/moderate` 在线；Python 不可用 → 保存失败（强一致 #15）。moderate 复用 `pythonAiClient.moderatePrompt`；Python 故障抛 500。

### 8.5 moderate / estimate 转发

直接转发 Python，`userId` 透传，`extractData` 解析。无落库。（moderate 端点亦供前端独立复检；保存闸门内部复用它。）

---

## 9. 错误处理

| 场景 | 处理 |
|---|---|
| moderation 不通过（Python code=403） | `new Result<>(403,"blocked",verdict)`，**不落库、不抛** |
| mode 非法 | Java 枚举绑定失败 → GlobalExceptionHandler → 400 |
| Python api_key 缺失 / LLM 失败 / structured 解析失败 | Python 返回 500 → PythonAiClient 抛 `BizException(500)` → 500 |
| create/update 保存闸门时 Python `/moderate` 不可用（v1.1） | **强一致**：PythonAiClient 抛 `BizException(500)` → 保存失败，**绝不放行未校验提示词** |
| 模板不存在 | `BizException(NOT_FOUND)` → 404 |
| 越权访问/改/删他人私有、普通用户置 public | `BizException(FORBIDDEN)` → 403 |
| apply 目标 Agent 不存在 | 复用 `getAgentById` → 404 |

> 403-blocked 与 403-forbidden 同为 403 但 message 不同（"blocked" vs "权限不足"），前端可据 message/data 区分。

---

## 10. 测试（Rule 9：验证意图，非仅行为；mock PythonAiClient）

Python 未实现，所有 Python 交互通过 mock `PythonAiClient` 验证。关键用例：

| 用例 | 验证意图 |
|---|---|
| `generate_success_returnsDraft_doesNotPersist`（v1.1 改） | 成功 → 返回草稿、**无 templateId、模板表无新增** | generate 不落库 |
| `generate_blocked_returns403` | mock blocked → 返回 403 + verdict | 草稿违规不返回 |
| `generate_python500_throwsBiz` | mock 抛 BizException(500) → 透出 500 | Fail Loud |
| `beautify_usesAgentLlm_returnsRefined`（v1.1 新） | 传 agentId → 取该 Agent LLM 调 Python beautify、返回润色文本、**不落库、不 moderate** | beautify 用所选 Agent LLM |
| `beautify_agentNotFound_404`（v1.1 新） | agentId 不存在 → 404 | 边界 |
| `create_moderationBlocked_returns403_doesNotPersist`（v1.1 新） | moderate 返回 blocked → create 返 403、**不 insert** | 保存闸门拦截非法提示词 |
| `create_moderationPassed_persists`（v1.1 新） | moderate 通过 → 正常 insert | 闸门放行合法提示词 |
| `update_moderationBlocked_returns403_doesNotUpdate`（v1.1 新） | 改后 moderate blocked → 403、不 update | 改也要过闸门 |
| `create_pythonModerateDown_failsLoud`（v1.1 新） | moderate 抛 500 → create 抛 500 不落库 | 强一致 #15 |
| `estimate_scalesWithPromptLength` | prompt 越长 promptTokens 越大 | 估算是真的 |
| `apply_updatesAgentSystemPromptAndSyncsPython` | apply → agent.systemPrompt 被改 + 触发 updateAgent | apply 生效，只搬 system_prompt |
| `list_userSeesPublicAndOwnPrivateOnly` | 非admin 列表不含他人私有；admin 含全部 | 双维度可见性 |
| `update_otherUserPrivate_forbidden` | 非 owner 非 admin 改/删他人私有 → 403 | 隔离生效 |
| `create_nonAdminSetPublic_forbidden` | 普通用户 public=true → 403；admin public=true → user_id=NULL | 「开放公共」仅 admin |
| `get_templateNotFound_404` / `get_otherPrivate_forbidden_403` | 边界 | 错误语义 |

> 用 `@MockBean PythonAiClient` + H2/真实 MySQL 测 Mapper 层（参考现有测试约定）。

---

## 11. 对现有代码的改动（surgical）

| 文件 | 改动 | 性质 |
|---|---|---|
| `UserContext` | 加 `getRoles()/isAdmin()`（~6 行） | 小改 |
| `schema.sql` | 加 `prompt_template` 建表 | 纯新增 |
| `PythonAiClient` | 加 Prompt 段（3 方法 + 复用 readTree/treeToValue） | 纯新增方法 |
| 其余 DO/Mapper/VO/枚举/Service/Controller | 全新文件 | 纯新增 |

> 不改动任何现有业务逻辑、不改 Agent 模型、不改 Gateway 路由（`/api/prompts/**` 走 acg-chat 既有 `lb://acg-chat` 规则；若 Gateway 路径白名单需调整，在 plan 阶段确认）。

---

## 12. 假设与待办

- **假设**：Gateway 已注入 `X-User-Roles` 头（RBAC 既有链路依赖）。若未注入，`isAdmin()` 恒 false → admin 端点全 403；plan 阶段验证 Gateway 配置。
- **假设**：Python 侧 prompt 接口按其 spec 实现后，契约（路径、字段、Result 信封、403 语义）与本文一致；Python 侧尚未实现，Java 暂以 mock 单测覆盖。
- **假设**：`/api/prompts/**` 经 acg-chat 既有路由可达，无需新增 Gateway 路由；GET 类模板查询是否需免认证由 Gateway 白名单决定（默认需登录，与私有模板语义一致）。
- **待办（plan 阶段确认）**：Gateway 路由/白名单是否需调整；是否需 changelog（涉及新表 + 新 API，按 CLAUDE.md 变更管理「数据库表结构变更/接口新增」需 `docs/changelogs/` 记录）。
- **未来扩展（不在本期）**：Agent per-user 化；Python 二期多裁判/真实 usage/推荐接口。（「后端路由 Agent」路由层 **v1.1 已确认放弃**；用户改用手动选 Agent 做 beautify。）
- **v1.1 跨仓库依赖**：beautify 与保存闸门依赖 **Python spec v1.1**（新增 `/api/v1/prompts/beautify` 端点 + 接受 `llm_config`）。Python 侧未实现前，generate/beautify/moderate/estimate 端到端 blocked；**模板 CRUD 的保存闸门也依赖 Python `/moderate` 在线（强一致 #15）**——这是 v1.1 引入的新依赖，CRUD 不再完全 Python 无关。
