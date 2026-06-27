# 2026-06-28 docx/ 文档与代码对齐

> 日期：2026-06-28 | 类型：docs | 关联代码：`docx/业务流程.md`、`docx/技术架构.md`、`docx/本地启动指南.md`、`docx/前端对接指南.md`

## 1. 背景

`docx/` 目录下的文档停留在早期「用户 CRUD 单表」阶段，已严重滞后于代码现状：

- `docx/业务流程.md` 旧版只描述 `user` 单表 CRUD，含 `createTime`/明文密码/"枚举：暂无"/"工具类：暂无" 等废弃表述。
- 项目实际已演化为 **Java 微服务 + 外部 Python AI 引擎（acgagent-ai）** 双后端，业务域扩展到认证、RBAC、个人中心、Agent、对话（SSE 流式）、知识库/文档、工具、提示词模板共 7 大域 + 个人中心。
- 对话/KB/文档/工具/提示词均经 `PythonAiClient` 与 Python 引擎交互；Agent 经 `python_agent_id` 同步锚点与 Python 双向同步。
- 架构层变更（Nacos 持久化迁回 `acg_agent` DB、agent 表迁移、提示词模板、Phase B 代理接口等）已有 changelog，但 `docx/` 文档未同步刷新。

本批次分 4 个子任务（task-1～task-4）依次重写/修补全部 4 篇 docx 文档，现已全部完成。

## 2. 影响范围

- **文档（共 4 篇，分批处理，现已全部完成）**：
  - `docx/业务流程.md` — 已重写（task-1，整体覆盖）。
  - `docx/技术架构.md` — 已按章节修补（task-2）。
  - `docx/本地启动指南.md` — 已增补（task-3）。
  - `docx/前端对接指南.md` — 已增补（task-4，本批）。
- **代码**：无改动（纯文档任务）。
- **数据库 / API / 架构**：无改动。

## 3. 变更清单

| 文件 | 改动 | 批次 |
|------|------|------|
| `docx/业务流程.md` | 整体重写：项目概述（双后端 + 8 业务域）→ 公共能力（Result/BizException/UserContext/RoleAuthAspect）→ 认证域（密码/短信/微信三方式 + 2h/7d 令牌）→ RBAC 域（用户/角色/权限 CRUD + 分配）→ 个人中心域（7 接口，含 `/api/user/profile` 单数 vs `/api/users` 复数路径差异提示）→ Agent 域（CRUD + AgentCategory + python_agent_id 同步 + AgentSyncMigrationRunner 注脚）→ 对话域（SSE 流 + ChatEvent 契约）→ 知识库/文档域（纯代理，Python 真相源）→ 工具域（纯代理）→ 提示词模板域（公共/私有 + generate/moderate/estimate/apply + PromptMode）→ 3 张 plantuml 时序图（登录鉴权/对话 SSE 含 Python 节点/Agent 创建同步）→ 枚举速查表。**不含「已知限制」章节**（代码缺陷见本 changelog 附录）。删除全部旧版 user-CRUD-only 内容。 | task-1（本批） |
| `docs/changelogs/2026-06-28-docx-sync-with-code.md` | 新建本骨架文件。 | task-1（本批） |
| `docx/技术架构.md` | 按章节修补：§2 包结构（`AgentClient`→`PythonAiClient` + kb/tool/prompt/migration/profile 包 + `AgentCategory`/`PromptMode` 枚举）；§5 SSE 改写为 Java→Python（6 类 ChatEvent 契约）；新增 §5.1「Java↔Python 集成边界」（Python 为 KB/工具真相源、Java 不落库、`python_agent_id` 同步锚点）；§7 补 `prompt_template` + agent 12 新列 + ER；§8 Nacos 持久化到 `acg_agent`；§9 拓扑加 Python 引擎节点。详见下方专节。 | task-2 |
| `docx/本地启动指南.md` | 增补（非重写）：在「初始化数据库」补存量库迁移提示（旧 `agent` 表执行 `2026-06-14-agent-python-sync.sql`，全新建库用 `schema.sql`）；在「初始化 Nacos 配置」前补 Nacos 持久化到 `acg_agent` 业务库说明；**新增「第二步：启动 Python AI 引擎（acgagent-ai）」**（对话/KB/文档/工具/提示词的硬性运行时依赖，置于微服务启动之前，给出 `python-agent.base-url` 默认 `http://127.0.0.1:8100` + `python-agent.api-key` 配置项，指向独立 Python 项目不展开内部）；原第二步~第五步顺延为第三步~第六步；第四步（微服务）补 Python 前置依赖提示；端口总览与检查服务状态补 Python `:8100`；第六步（验证）补创建会话 + SSE 发消息 + 查 KB 列表 3 个示例，验证 Java↔Python 联通。 | task-3 |
| `docx/前端对接指南.md` | 增补（非重写）：§4 枚举加 `AgentCategory`(CHAT/VIDEO/IMAGE 大写) + `PromptMode`(acg/compliant 小写) 两表；§5.5 Agent 的 AgentVO TypeScript 补全 14 个扩展字段（category/systemPrompt/provider/temperature/maxTokens/topP/memoryType/memoryMaxTokens/capabilities/knowledgeBaseIds/toolIds/pythonAgentId + configJson 标注废弃）+ 创建请求示例补可选扩展字段；新增 §5.7 知识库模块、§5.8 文档模块、§5.9 工具模块、§5.10 系统提示词模块四节（入参表+出参 VO+请求/响应示例，含 PromptMode 说明、KB/文档/工具标注「代理 Python、仅管理员、id 为 Python 侧 string」、文档上传 multipart + 异步轮询、内置工具不可删、公共/私有模板与 generate/moderate/estimate/apply 流程、moderation 403 走 code 字段）；§6 接口总览补 22 行；§7 TypeScript 类型参考补 10 个接口/VO 类型。§3 免认证清单与 Gateway 白名单一致（`/api/auth/**`、`GET /api/agents`、`GET /api/agents/{id}`），未改动。 | task-4（本批，末批） |

## 4. 设计决定

| 决定 | 理由 |
|------|------|
| 业务流程.md 采用「域」组织而非「接口逐条罗列」 | 当前接口已达数十个，逐条罗列会冗长且与《前端对接指南.md》重复；按域描述跨层流程与业务语义更符合「业务流程」定位 |
| 保留 3 张 plantuml 时序图，其中对话时序显式画出 Python 引擎节点 | 双后端架构下，Python 是关键参与方，时序图必须体现 Java↔Python 边界 |
| 业务流程.md 不设「已知限制」章节、不在行内堆砌代码缺陷告警 | 业务流程文档描述「流程」，不应承担「代码缺陷清单」职责。评审发现的代码缺陷移至本 changelog 附录留存（见文末），避免业务流程文档沦为 bug 目录 |
| 接口路径/字段/枚举值一律从源码逐字段核对，不凭记忆 | 文档准确性优先；AgentCategory 大写、PromptMode 小写、MessageRole 无 SYSTEM/tool 等细节均经源码确认 |

## 5. 验证（业务流程.md 自检 checklist）

- [x] 全文无 `user` 单表 / 明文密码 / `createTime` / "枚举：暂无" / "工具类：暂无" 等废弃表述（已 grep 确认）。
- [x] 7 个业务域（认证/RBAC/Agent/对话/KB-文档/工具/提示词）+ 个人中心域，共 8 域均有覆盖（§3-§10）。
- [x] 时序图 ≥3（§11.1 登录鉴权、§11.2 对话 SSE 含 Python 引擎节点、§11.3 Agent 创建同步 Python），对话时序图含 Python 引擎节点。
- [x] 接口路径/枚举值与源码一致（逐项核对各 Controller 与 enums 包；AgentCategory=CHAT/VIDEO/IMAGE 大写、PromptMode=acg/compliant 小写、PermissionType=MENU(1)/BUTTON(2)、ResultCode 7 个常量等）。

## 6. 范围（本次未做）

- `docx/技术架构.md`、`docx/本地启动指南.md`、`docx/前端对接指南.md` 三篇的修补（见 task-2/3/4）。
- 未改任何 Java/SQL/yml 代码（纯文档任务）。
- 未执行 `git commit`（项目规约：AI 只 `git add`，由开发者提交）。

## 7. 注意点

- 本 changelog 为「文档对齐」批次的汇总文件，**分 4 批（task-1~task-4）依次填充**，覆盖全部 4 篇文档（业务流程.md 重写、技术架构.md 修补、本地启动指南.md 增补、前端对接指南.md 增补），现已全部完成（见各专节及 §2 影响范围）。
- 业务流程.md **不再保留「已知限制」章节**：业务流程文档描述流程，代码缺陷清单移至本 changelog 附录（见下）如实留存（CLAUDE.md Rule 12 — fail loud），但不代表本次文档任务要修这些代码问题；如需修复应另开 changelog 与对应代码任务。

---

## 技术架构.md（task-2 修补）

> 日期：2026-06-28 | 批次：task-2 | 文件：`docx/技术架构.md`（按章节修补，非整体重写）

### 背景

`docx/技术架构.md` 主体已较新（覆盖 RBAC / Agent / 对话 / Dubbo / Gateway JWT），但 §2 包结构、§5 SSE、§7 表设计、§8 Nacos、§9 拓扑五处滞后于代码现状，主要表现为：旧 §2/§5 描述 acg-chat 通过 `AgentClient`（WebClient SSE 直连 LLM API）做对话，与当前「Java 经 `PythonAiClient` 调 Python AI 引擎」的双后端架构不符；新增的 kb/tool/prompt/profile 包、`agent` 表 12 个新列、`prompt_template` 表、`AgentCategory`/`PromptMode` 枚举、Nacos 持久化迁回 `acg_agent` 库等均未体现。

### 变更清单（按章节）

| 章节 | 改动 |
|------|------|
| §2 包结构 | `AgentDO` 实体注释补「LLM 参数 + system_prompt + python_agent_id 同步锚点」；`enums/` 补 `AgentCategory(CHAT/VIDEO/IMAGE)`、`PromptMode(acg/compliant)`；acg-user 子树补 `user/profile/`（ProfileController + ProfileService）；acg-chat 子树重写——`agent/client/AgentClient` → **`PythonAiClient`**（标注为「Java 调 Python 唯一出口」），新增 `agent/migration/AgentSyncMigrationRunner`、`kb/`（KnowledgeBase+Document）、`tool/`、`prompt/` 四个子树，`config/` 补 `WebClientConfig`（pythonWebClient）与 `PythonAgentProperties`。**删除「AgentClient — WebClient SSE 流式调用 LLM API」表述。** |
| §5 SSE 流式通信 | 流程改写为「前端 → ChatServiceImpl →（校验 python_agent_id + 落用户消息）→ `PythonAiClient.streamChat` → Python `/api/v1/chat/{agentId}/completions` → 结构化 `ChatEvent` 透传 → done 事件落助手消息」。事件表由旧的 `data:{content}` / `data:[DONE]` 替换为 6 类 `ChatEvent.type`（content/tool_call/tool_result/thinking/error/done）契约表（snake_case，镜像 Python schema）。补「会话上下文由 Python 按 conversation_id 自管，Java 不再查历史喂 LLM」说明。 |
| §5.1（新增）「Java ↔ Python AI 引擎集成边界」 | 新增整章：职责划分表（明确 KB/文档/工具以 **Python 为真相源、Java 不落库**）、`python_agent_id` 同步锚点约定（未同步则对话拒绝）、`AgentSyncMigrationRunner` 存量迁移（开关 `python-agent.migrate-existing` 默认关闭）、请求签名（X-API-Key + X-User-Id）、超时策略（read/stream 区分）、`PythonAgentProperties` 配置表（5 项）。 |
| §7 数据库设计 | 表清单：`agent` 行补齐 12 个新列（category/system_prompt/provider/temperature/max_tokens/top_p/memory_type/memory_max_tokens/capabilities/knowledge_base_ids/tool_ids/python_agent_id）+ 标注 config_json 已废弃；新增 `prompt_template` 行；表后补枚举速查说明（`AgentCategory` 大写、`PromptMode` 小写）。ER 图：`agent` 实体同步补 12 列并标注默认值/含义；新增 `prompt_template` 实体 + `user ||--o{ pt` 关系。 |
| §8 配置说明 / Nacos | Nacos 段补「持久化说明」：配置与服务元数据持久化到 `acg_agent` 业务库（MySQL），非嵌入式 Derby、非独立 `nacos` 库；12 张 nacos 表与业务表同库无冲突；命名空间为逻辑概念。引用 `docs/changelogs/2026-06-27-nacos-persistence-to-acg_agent-db.md`。 |
| §9 部署架构 | plantuml 拓扑图新增「Python AI 引擎 acgagent-ai :8100」节点（珊瑚色），`acg-chat →(HTTP/SSE)→ Python` 边（标注 PythonAiClient + X-API-Key/X-User-Id），`Nacos → DB` 持久化边；acg-chat 节点描述补「KB/Tool/Prompt」；DB 节点描述补「业务表 + Nacos 持久化」。调用链路文本的「外部 LLM API」改为「Python AI 引擎(8100)」，补 Nacos→MySQL 持久化链路。 |

### 关键修正（旧 → 新）

- §2 `agent/client/AgentClient — WebClient SSE 流式调用 LLM API` → **`PythonAiClient — Java 调 Python AI 引擎的唯一出口`**（核心纠错）。
- §5 流程 `WebClient POST agent.apiUrl (OpenAI ChatCompletion)` → **`PythonAiClient POST Python /api/v1/chat/{agentId}/completions`**。
- §5 SSE 事件 `data:{content}` / `data:[DONE]` → **6 类结构化 `ChatEvent` 契约表**。
- §7 `agent` 表 11 字段 → **23 字段**（+12 新列）。
- §7 新增 `prompt_template` 表（旧文档完全缺失）。
- §9 「外部 LLM API」节点 → **「Python AI 引擎 acgagent-ai :8100」** 节点。
- §8 隐含 Derby/独立 nacos 库 → **显式说明持久化到 acg_agent 业务库**。

### 验证（自检 checklist）

- [x] §2 含 `PythonAiClient`、`kb/tool/prompt`、`AgentSyncMigrationRunner`、`PythonAgentProperties`；全文无「AgentClient」「OpenAI ChatCompletion」「choices[0].delta」「外部 LLM API」「data:[DONE]」残留（grep 确认）。
- [x] 新增 §5.1「Java ↔ Python AI 引擎集成边界」章节，明确写明 KB/文档/工具以 Python 为真相源、Java 不落库。
- [x] §7 含 `prompt_template` 表 + `agent` 12 新列 + `AgentCategory`(CHAT/VIDEO/IMAGE) / `PromptMode`(acg/compliant) 两枚举；ER 图同步补列与新实体。
- [x] §8 含 Nacos 持久化到 `acg_agent` 业务库说明；§9 拓扑含 Python AI 引擎节点 + Nacos→DB 持久化边。

### 范围（本次未做）

- §1 技术选型表、§3 认证、§4 Dubbo、§6 规范、§10 Docker 未改动（已与代码一致或非本任务范围）。
- 未改任何 Java/SQL/yml 代码（纯文档任务）。
- 未执行 `git commit`（项目规约：AI 只 `git add`，由开发者提交）。

### 注意点

- acg-chat `agent/` 与 `kb/`/`tool/`/`prompt/` 同为 `com.darkness` 顶层包，文档包结构树按此平级展开（非 `agent/kb` 嵌套），与实际目录一致。
- `python_agent_id` 在文档中统一称为「同步锚点」，与 `AgentDO` Javadoc 措辞一致。
- `config_json` 列在文档中显式标注「已废弃，向前兼容保留」，避免读者误以为仍在用。


---

## 本地启动指南.md（task-3 增补）

> 日期：2026-06-28 | 批次：task-3 | 文件：`docx/本地启动指南.md`（增补，非整体重写）

### 背景

`docx/本地启动指南.md` 主体已与当前代码一致，但有三处关键遗漏，照原样启动会导致运行时失败：

1. **完全未提及外部 Python AI 引擎（acgagent-ai）**——对话 / 知识库 / 文档 / 工具 / 提示词模板流程均经 acg-chat 的 `PythonAiClient` 调用它（`PythonAgentProperties` 绑定 `python-agent.*`），是硬性运行时依赖；不启动则对话与 KB 接口运行时报错。
2. **未说明 Nacos 持久化目标库**——现已持久化到 `acg_agent` 业务库（非嵌入式 Derby、非独立 `nacos` 库），见 `docs/changelogs/2026-06-27-nacos-persistence-to-acg_agent-db.md`。
3. **未提示存量 `agent` 表迁移**——早期建库的 `agent` 表需执行 `acg-user/src/main/resources/db/2026-06-14-agent-python-sync.sql` 补齐 12 列（含 `category` + `python_agent_id`）；全新建库直接用 `schema.sql`。

### 变更清单（增补点）

| 位置 | 改动 |
|------|------|
| 「初始化数据库」节 | 新增存量库迁移提示 blockquote：旧 `agent` 表执行 `2026-06-14-agent-python-sync.sql`（12 列、仅执行一次），全新建库用 `schema.sql` |
| 「初始化 Nacos 配置」节开头 | 新增持久化说明 blockquote：Nacos 配置与服务元数据持久化到 `acg_agent` 业务库，12 张 nacos 表与业务表同库无冲突，命名空间为逻辑概念；Nacos 依赖 MySQL 就绪 |
| **新增「第二步：启动 Python AI 引擎（acgagent-ai）」** | 整节新增：说明对话/KB/文档/工具/提示词均经 `PythonAiClient` 调用外部 Python 引擎（FastAPI，独立项目不在此仓库）；**置于微服务启动之前**；给 `python-agent.base-url`（默认 `http://127.0.0.1:8100`）+ `python-agent.api-key` 配置表；给 `curl /api/v1/health` 自检命令；不展开 Python 内部 |
| 步骤顺延 | 原第二步（构建）→第三步，第三步（微服务）→第四步，第四步（检查）→第五步，第五步（验证）→第六步；无文档内交叉引用受影响 |
| 第四步（微服务启动） | 命令行与 IDEA 两处均补前置依赖提示（MySQL/Nacos/Python 引擎需就绪） |
| 端口总览 + 第五步端口表 | 补 Python `acgagent-ai :8100` 行（标注外部独立项目） |
| 第六步（验证接口） | 补 3 个示例：创建会话 `POST /api/chat/conversations`、SSE 发消息 `POST /api/chat/conversations/{id}/send`、查 KB 列表 `GET /api/knowledge-bases`；附 Python 引擎联通失败排查提示 |

### 验证（自检 checklist）

- [x] 有 Python 引擎启动步骤，且置于微服务启动之前（新增「第二步」位于「第三步：构建项目」与「第四步：启动微服务」流程上的基础设施位置，明确要求先于微服务启动）。
- [x] 有 Nacos 持久化说明（「初始化 Nacos 配置」节 blockquote，引用 `2026-06-27-nacos-persistence-to-acg_agent-db.md`）。
- [x] 有 agent 迁移提示（「初始化数据库」节 blockquote，区分存量库 vs 全新建库）。
- [x] 新增示例命令与实际接口一致（`POST /api/chat/conversations`、`POST /api/chat/conversations/{id}/send`、`GET /api/knowledge-bases` 均逐项核对 `ChatController` / `KnowledgeBaseController` 源码；请求体字段 `agentId`/`title`/`content` 与 `CreateConversationRequest` / `SendMessageRequest` 一致）。

### 范围（本次未做）

- 未改任何 Java/SQL/yml 代码（纯文档任务；`python-agent.*` 配置项已存在于 Nacos `acg-chat.yaml` 运行时配置，不在仓库 checked-in 样例中，本次不补样例文件）。
- 未改「常见问题」「停止服务」「环境变量」「配置加载说明」等已与代码一致的章节。
- 未执行 `git commit`（项目规约：AI 只 `git add`，由开发者提交）。

### 注意点

- Python 引擎默认端口 `:8100` 与 `python-agent.base-url` 默认值取自 `PythonAgentProperties` Javadoc 与 `docs/superpowers/specs/2026-06-14-java-python-ai-integration-design.md`（§2.2 / 配置示例）；Java 代码中 `baseUrl` 字段无硬编码默认，运行时由 Nacos 注入，文档已据此表述。
- Python 引擎是独立项目（acgagent-ai），不在本仓库 docker-compose 内（compose 仅含 mysql/nacos/zipkin/sentinel + 3 个 Java app profile），故端口总览将其标注为「外部独立项目」、检查服务状态表将其排除在 `netstat` 检查外。
- 步骤顺延（第二步→第六步）经 grep 确认文档内无「第N步」交叉引用，重编号无副作用。


---

## 前端对接指南.md（task-4 增补，末批）

> 日期：2026-06-28 | 批次：task-4（末批） | 文件：`docx/前端对接指南.md`（增补，非整体重写）

### 背景

`docx/前端对接指南.md` 前 120 行（认证 / Token / 统一响应 / 基础枚举 / 用户·角色·权限·Agent·对话接口详情）已与代码一致，但完全未覆盖后续新增的知识库 / 文档 / 工具 / 系统提示词模板四大模块，也未补 `AgentCategory` 分类枚举与 `AgentVO` 的扩展字段。

### 变更清单（增补点）

| 位置 | 改动 |
|------|------|
| §4 枚举值定义 | 在 `PermissionType` 后新增 `AgentCategory`(CHAT/VIDEO/IMAGE，大写字符串、`@JsonValue`) 与 `PromptMode`(acg/compliant，小写字符串、匹配 Python `PromptMode(str, Enum)`) 两张枚举表，含取值、含义、使用字段、反序列化行为 |
| §5.5 Agent 模块 | AgentVO TypeScript 接口补全 14 个扩展字段（`category`/`systemPrompt`/`provider`/`temperature`/`maxTokens`/`topP`/`memoryType`/`memoryMaxTokens`/`capabilities`/`knowledgeBaseIds`/`toolIds`/`pythonAgentId`/`configJson` 标注废弃）；补 `pythonAgentId` 同步锚点、`knowledgeBaseIds`/`toolIds` 为 Python 侧 string id 的说明；创建请求示例补可选扩展字段 |
| §5.7（新增）知识库模块 | 5 接口（GET 列表/详情、POST 创建、PUT 更新、DELETE）+ KnowledgeBaseVO 类型；标注「代理 Python、仅管理员、id 为 Python 侧 string」 |
| §5.8（新增）文档模块 | 4 接口（POST multipart 上传、GET 列表/详情、DELETE）+ DocumentVO；标注异步处理（status: processing→completed/failed，需轮询）、multipart 字段名 `file` |
| §5.9（新增）工具模块 | 4 接口（GET 列表/详情、POST 创建、DELETE）+ ToolVO；标注内置工具（calculator/web_search/knowledge_search）不可删、type=api/builtin、id 为 Python 侧 string |
| §5.10（新增）系统提示词模块 | 9 接口（generate/moderate/estimate + 模板 CRUD + apply-to-agent）+ 4 个 VO 类型；含 PromptMode 说明、公共/私有模板可见性规则（admin 见全部、普通见公共+自己私有）、moderation 不通过走 `code=403`（HTTP 仍 200）、`estCompletionTokens` 一期恒 0、apply 仅 admin |
| §6 接口总览 | 追加 22 行（知识库 5 + 文档 4 + 工具 4 + 提示词 9），认证列标注「admin」/「owner/admin」差异 |
| §7 TypeScript 类型参考 | AgentVO/CreateAgentRequest 补扩展字段；追加 10 个类型（KnowledgeBaseVO/Request、DocumentVO、ToolVO/ToolCreateRequest、PromptGenerateRequest/Response、PromptModerateRequest/ModerationVerdictVO、PromptEstimateRequest/CostEstimateVO、PromptTemplateVO/Request、`PromptMode` 类型别名） |

### 关键修正/补全（旧 → 新）

- §4 缺 `AgentCategory`/`PromptMode` 枚举 → 补两表（大小写、序列化值、反序列化行为均逐字核对 enums 包）。
- §5.5 AgentVO 11 字段（无 category/扩展）→ 补至 25 字段（+category +13 个 LLM/同步字段，configJson 标注废弃）。
- §5.5 缺 `pythonAgentId` 同步锚点说明 → 补「未同步 Agent 调对话被拒」。
- 完全缺失知识库/文档/工具/提示词四模块 → 新增 §5.7-§5.10 四节 + 入参/出参/示例。
- §6 接口总览缺 22 个新接口 → 补齐。
- §7 类型缺新模块类型 → 补 10 个类型 + Agent 扩展字段。

### 验证（自检 checklist）

- [x] 知识库/文档/工具/提示词模板接口齐全且与 Controller 一致（逐项核对 `KnowledgeBaseController`/`DocumentController`/`ToolController`/`PromptController`：路径、HTTP 方法、`@RequireRole("admin")`、入参 `@Valid`/`@RequestParam`、出参 VO 均一致；`GET /api/prompts/templates` 的 `mode` query 参数与源码 `@RequestParam(required=false) PromptMode mode` 一致）。
- [x] 含 `AgentCategory`(CHAT/VIDEO/IMAGE 大写) + `PromptMode`(acg/compliant 小写) 枚举；AgentVO 新字段（category 等 14 个）已补全（逐字段核对 `AgentVO.java`）。
- [x] 免认证接口清单（§3）与 Gateway 白名单一致（`/api/auth/**`、`/druid/**`、`GET /api/agents`、`GET /api/agents/{id}`；§3 列出全部 `/api/auth/*` 与两个 GET agents，`/druid/**` 为 Druid 监控非前端 API 故不在 §3 列出，与既有表述一致；本批**未改动 §3**）。

### 范围（本次未做）

- 未改任何 Java/SQL/yml 代码（纯文档任务）。
- 未改 §1-§3、§5.1-§5.6、§8 等已与代码一致的章节。
- 未执行 `git commit`（项目规约：AI 只 `git add`，由开发者提交）。

### 注意点

- KB/文档/工具的 `id` 均为 **Python 侧 string**（非 Java Long），已在各章节与 AgentVO `knowledgeBaseIds`/`toolIds` 注释中显式说明，避免前端误用数字 id。
- KB/文档/工具模块统一标注「代理 Python 引擎、仅管理员」，与 Controller 的 `@RequireRole("admin")` + 类 Javadoc（"仅管理员可访问"）一致。
- `PromptMode` 反序列化大小写不敏感、非法值抛 `IllegalArgumentException`（Controller 绑定转 400）；`AgentCategory` 反序列化非法值**回退为 CHAT**（不抛异常）——两者行为差异已在枚举表中如实区分。
- `moderation` 不通过走 `code=403` 而 HTTP 仍为 200，已在 §2 注意事项（HTTP 200 + code 区分）既有约定下、于 §5.10 generate 处补一条专项提示。
- 本批为末批，changelog §2 影响范围与 §3 变更清单已同步刷新为「4 篇全部完成」，无遗留占位。

---

## 附：评审中发现的代码现状（非契约，未在本任务修复）

以下 5 项为评审过程中核对源码发现的代码现状，**仅作记录、非本文档任务范围内修复**，需后续独立任务处理：

1. **`generateTokenPair.expiresIn` 硬编码**：`AuthServiceImpl.generateTokenPair` 中 `TokenVO.expiresIn` 硬编码为 `7200`，与 Nacos `common-jwt.yaml` 中 `jwt.access-token-expiration` 配置存在漂移风险（TODO：与配置同步）。
2. **`ProfileController.changePhone` 未校验 `sms_code.expired_at`**：`ProfileServiceImpl.changePhone` 校验短信码时仅查 `used=UNUSED`，**未检查 `expired_at`**，与 `SmsServiceImpl.login` 的过期校验逻辑不一致。
3. **微信解绑可能产生孤儿 `wx_user`/系统用户**：`ProfileServiceImpl.unbindWx` 仅删除 `wx_user` 绑定记录，不删除微信登录自动创建的系统用户，账号可能成为孤儿。
4. **`UserContext.getUsername()` 生产路径恒返回 null**：`UserContext.getUsername()` 读 `X-Username` 头，但 `JwtAuthFilter` 仅注入 `X-User-Id` 与 `X-User-Roles`，**不注入 `X-Username`**，故该方法在生产路径恒为 null。
5. **角色/权限 `code` 唯一性仅靠 DB 约束**：`RoleServiceImpl` / `PermissionServiceImpl` 未在 Service 层显式校验 `code` 唯一，依赖数据库唯一约束兜底。
