# 创作工坊 LLM 化 — 设计文档（Spec）

> 版本：1.0.0 | 日期：2026-07-26 | 所属项目：acgagent（Java）+ acgagent-ai（Python）+ acgagent-web（前端）
> 状态：待评审 → 通过后进入 writing-plans
> 关联：prompt 生成功能 spec `docs/superpowers/specs/2026-06-21-prompt-generation-java-design.md`（复用其 Java→Python→LLM + SSE 基建）

---

## 1. 目标与背景

前端「创作工坊」(`acgagent-web/src/views/workshop/Index.vue`) 是一条 5 步创作流水线，目前 **②剧情/③分镜/④角色/⑤视频 全是 mock 数据**，`handleGenerate` 只是 `setTimeout` 假装生成。本 spec 把 **②③④ 接真实 LLM**，并新增**多项目落库**（创作内容持久化）。**⑤视频生成**（文生视频，非 LLM）是另一个独立子系统，单独立 spec（本文档不含）。

### 1.1 关键决策（已与用户确认）

| # | 决策点 | 选择 | 理由 |
|---|---|---|---|
| 1 | 范围 | **②③④（LLM）+ 多项目落库**；⑤视频下一轮 | 视频是另一类模型（非 LLM），且依赖③输出，单独立 spec |
| 2 | 架构 | **复用 Java→Python→LLM**（meta-LLM GLM，同 prompt 功能） | API key 留服务端、与既有约定一致 |
| 3 | ②剧情产出 | **SSE 流式**（content/done 事件，复用 `stripSseData` 修复链路） | 长文本流式体验好 |
| 4 | ③分镜/④角色产出 | **同步结构化 JSON**（Python `with_structured_output`，同 moderator） | 结构化数组，一次返回，前端转圈等完再渲染 |
| 5 | 生成触发 | **每步手动**：进入步骤不自动生成，必须点「生成」/「重新生成」才调 LLM | 用户明确要手动 |
| 6 | 落库 | **多项目**：用户有创作项目列表（新建/打开/删除），每项目存 story/plot/storyboard/characters | 用户明确要落库 + 多项目 |
| 7 | 生成与落库关系 | **生成端点无状态**（只返结果），前端拿到后 `PUT projects/{id}` 落库（②流式则前端累加完再 PUT） | 与 prompt 的 generate(出草稿)/create(落库) 分离一致；避免服务端流式落库的复杂度 |
| 8 | 角色 color | **前端按角色名自动配色**，不让 LLM 选 | LLM 选颜色不靠谱；纯显示细节 |
| 9 | moderation | **不做**（创作是纯生成，无合规闸门） | 区别于提示词模板的保存闸门 |

---

## 2. 范围

### 2.1 本期实现

1. **项目 CRUD**：多项目（每用户私有），存 story/plot/storyboard/characters。
2. **②剧情扩展**：`POST /api/workshop/plot`（SSE 流式），入参 story。
3. **③分镜生成**：`POST /api/workshop/storyboard`（同步结构化），入参 plot → `Shot[]`。
4. **④角色设计**：`POST /api/workshop/characters`（同步结构化），入参 plot → `Character[]`。
5. **前端**：项目选择器 + 5 步流程改为手动生成 + 落库。

### 2.2 不在本期范围

- **⑤视频生成**（文生视频 API、异步任务/轮询/存储）—— 下一轮独立 spec，依赖③输出。
- 「导出项目」按钮（保持禁用）。
- 项目协作/分享/公共库（创作是个人内容，仅 owner 可读写）。
- 创作内容的 moderation（纯生成，无闸门）。

---

## 3. 架构与文件分布（复用 prompt 功能基建）

```
Python(acgagent-ai)
  app/api/v1/workshop.py            （新；4 端点：plot/storyboard/characters + 复用 Result）
  app/services/workshop_service.py  （新；plot astream / storyboard / characters structured）
  app/core/workshop_builder.py      （新；3 类生成的元提示词 + with_structured_output 的 Shot/Character schema）
  app/models/workshop.py            （新；PlotRequest{story} / Shot / Character pydantic）
  app/api/v1/router.py              （改；挂载 workshop_router）
  app/config.py                     （不改；复用 meta_llm_* 配置）

Java(acgagent)
  acg-common: entity/WorkshopProjectDO、mapper/WorkshopProjectMapper、
              model/Workshop*Request + WorkshopProjectVO + ShotVO/CharacterVO + WorkshopPlotStreamEvent
  acg-chat:   prompt 外新增 workshop 域：
              agent/client/PythonAiClient （改：加 streamWorkshopPlot / workshopStoryboard / workshopCharacters）
              workshop/service/WorkshopService + impl
              workshop/controller/WorkshopController （/api/workshop）
  acg-user/src/main/resources/db/schema.sql （改：加 workshop_project 建表）

前端(acgagent-web)
  src/api/workshop.ts               （新；项目 CRUD + 3 生成，②用 fetch SSE）
  src/types/workshop.ts             （新；WorkshopProject/Shot/Character/PlotStreamEvent 类型）
  src/views/workshop/Index.vue      （改：去 mock；项目选择器；手动生成；落库；角色自动配色）

网关：Nacos acg-gateway.yaml 加路由 /api/workshop/** → lb://acg-chat（OPS）
```

---

## 4. 数据模型

### 4.1 建表（追加到 `acg-user/.../db/schema.sql`）

```sql
CREATE TABLE IF NOT EXISTS workshop_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    user_id BIGINT NOT NULL COMMENT '归属用户 ID',
    title VARCHAR(128) NOT NULL COMMENT '项目标题，默认取故事前 32 字，可改',
    story TEXT COMMENT '①故事梗概（用户输入）',
    plot TEXT COMMENT '②剧情（生成的 Markdown 正文）',
    storyboard JSON COMMENT '③分镜数组，如 [{shot,description,duration,movement,dialogue}]',
    characters JSON COMMENT '④角色数组，如 [{name,role,description}]',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) COMMENT '创作工坊项目表（每用户私有）';
```

> 沿用 DDL 约定：`BIGINT AUTO_INCREMENT`、列级中文 COMMENT、`deleted/created_at/updated_at` 标配、snake_case。`storyboard`/`characters` 为 JSON 列，`JacksonTypeHandler` 映射为 `List<...>`（镜像 AgentDO 的 capabilities）。

### 4.2 结构化子模型

**Shot**（分镜）：`{ shot: string(远景/中景/近景/特写), description: string, duration: string(如"3s"), movement: string, dialogue: string }`

**Character**（角色）：`{ name: string, role: string(主角/配角/引导者...), description: string }`
> **不含 color** —— color 由前端按 name 哈希自动配色（决策 #8）。

---

## 5. HTTP 契约

所有端点挂 `/api/workshop`，经网关鉴权（需登录）。`userId` 由 `UserContext.getUserId()` 取（Controller 透传）。

### 5.1 项目 CRUD（MySQL，acg-chat）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/workshop/projects` | 新建项目；body `{title?, story?}`（title 缺省取 story 前 32 字）；返回 `WorkshopProjectVO` |
| GET | `/api/workshop/projects` | 当前用户项目列表（不含已删）|
| GET | `/api/workshop/projects/{id}` | 项目详情；越权 403，不存在 404 |
| PUT | `/api/workshop/projects/{id}` | 局部更新（`title?/story?/plot?/storyboard?/characters?` 任选）；返回更新后 VO |
| DELETE | `/api/workshop/projects/{id}` | 逻辑删 |

> 鉴权：所有 CRUD 仅 owner（`user_id == 当前用户`）可读写；他人项目 403。

### 5.2 生成（Java→Python→LLM，无状态、不落库）

| 方法 | 路径 | 机制 | 入参 → 产出 |
|---|---|---|---|
| POST | `/api/workshop/plot` | **SSE 流式** (`text/event-stream`) | `{story}` → 事件流（见 5.3）|
| POST | `/api/workshop/storyboard` | 同步 | `{plot}` → `Result<Shot[]>` |
| POST | `/api/workshop/characters` | 同步 | `{plot}` → `Result<Character[]>` |

### 5.3 plot SSE 事件格式（与 prompt generate 同款，复用 `stripSseData`）

每行 `data: {json}`，事件：
- `{"type":"content","content":"<token>"}` —— 剧情逐 token
- `{"type":"done"}` —— 流结束（不带 estimate，剧情无需估算）
- `{"type":"error","message":"..."}` —— 失败

> Java `streamWorkshopPlot` 必须用 `stripSseData` + `filter(startsWith("{"))`（**不能**用旧的 `startsWith("data:")`，否则踩 SSE-reader 剥前缀的坑——见 prompt 那次 bug 修复）。

---

## 6. 核心逻辑

### 6.1 Python `workshop_service`

- `plot_stream(req)`: meta-LLM `astream`，按 build_messages（故事→剧情的元提示词）逐 token yield content；末尾 yield done；meta-LLM 未配置/失败 yield error。**镜像 prompt 的 generate_stream**。
- `storyboard(req)`: meta-LLM `with_structured_output(list[Shot])`，输入 plot，同步返回 Shot[]。
- `characters(req)`: meta-LLM `with_structured_output(list[Character])`，输入 plot，同步返回 Character[]。

### 6.2 Java `WorkshopService`

- 项目 CRUD：镜像 `PromptServiceImpl` 的 owner 鉴权 + `ServiceHelper.findOrThrow`（不存在 404）+ LambdaQueryWrapper（列表按 user_id）。`createProject` 取 title 默认值（story 前 32 字，空则「新建项目」）。
- 生成：`streamWorkshopPlot(req,userId)→Flux`（直接转发）；`storyboard/characters(req,userId)→Result<List<...>>`（转发，extractData/Flux）。

### 6.3 前端 `workshop/Index.vue`

- 顶部**项目选择器**（下拉/列表：新建、切换、删除）。
- 打开项目 → 加载已存 story/plot/storyboard/characters 到各步。
- ① 改故事 → 「保存」→ `PUT projects/{id}`(story)。
- ② 「生成」→ fetch SSE 流式剧情逐字渲染 → 完成后 `PUT`(plot)。
- ③ 「生成」→ POST storyboard 转圈 → 渲染 Shot[] → `PUT`(storyboard)。
- ④ 「生成」→ POST characters 转圈 → 渲染 Character[] → `PUT`(characters)。
- 「重新生成」= 重跑当前步并覆盖保存。
- 角色头像 color：前端按 name 哈希从固定色板取色（同名同色）。

---

## 7. 错误处理

| 场景 | 处理 |
|---|---|
| ②流式 meta-LLM 未配置/失败 | `{type:"error",message}` 事件 |
| ③④同步 meta-LLM 失败 | Python 返 500 → Java 抛 BizException(500) |
| 项目不存在 | `BizException(NOT_FOUND)` → 404 |
| 项目越权（读写他人项目） | `BizException(FORBIDDEN)` → 403 |
| `with_structured_output` 解析失败 | 重试一次（沿用 prompt moderator 约定）；仍失败 → 500 |

> 不做 moderation 闸门（创作纯生成）。

---

## 8. 测试（Rule 9：验证意图；mock meta-LLM）

| 用例 | 验证意图 |
|---|---|
| `plot_stream_emits_content_then_done` | 故事 → 流式吐剧情 token + done |
| `storyboard_returns_structured_shots` | 剧情 → 返回 Shot[]（结构正确，非空）|
| `characters_returns_structured_chars` | 剧情 → 返回 Character[] |
| `createProject_defaultsTitleFromStory` | 新建无 title → title 取 story 前 32 字 |
| `list_returnsOnlyOwnProjects` | 非他人项目出现在列表 |
| `get/update/delete_otherUserProject_forbidden_403` | 越权 403 |
| `plot_sseUsesStripSseData_notStartsWithData` | Java 转发用 stripSseData（防 SSE-reader bug 回归）|

> Python 侧用 conftest mock meta-LLM；Java 侧 mock PythonAiClient + Mapper；前端 mock 端点。

---

## 9. 对现有代码的改动（surgical）

| 文件 | 改动 | 性质 |
|---|---|---|
| `schema.sql` | 加 `workshop_project` 建表 | 纯新增 |
| `PythonAiClient` | 加 workshop 段（streamWorkshopPlot 复用 stripSseData / workshopStoryboard / workshopCharacters） | 纯新增方法 |
| `router.py`(Python) | 挂载 workshop_router | 1 行 |
| 其余 DO/Mapper/VO/枚举/Service/Controller/builder | 全新文件 | 纯新增 |
| `workshop/Index.vue` | 去 mock、加项目选择器/手动生成/落库 | 改（保留步骤骨架与样式）|

> 不动 prompt 功能、不动 Agent 模型、不改既有 SSE 链路。

---

## 10. 假设与待办

- **假设**：复用 Python meta-LLM 配置（`ACG_AI_META_LLM_*`，已配 GLM）；网关已注入 `X-User-Roles`/`X-User-Id`（既有链路）。
- **OPS 待办**：① MySQL 建 `workshop_project` 表（host/Docker 库均需）；② Nacos `acg-gateway.yaml` 给 acg-chat 路由加 `,/api/workshop/**`（线上需手动应用 + 重启 gateway）。
- **下一轮 spec**：⑤视频生成（文生视频 API、异步任务/轮询/存储），依赖③分镜输出。
- **changelog**：涉及新表 + 新 API，按 CLAUDE.md 规约五.3 需 `docs/changelogs/` 记录。
