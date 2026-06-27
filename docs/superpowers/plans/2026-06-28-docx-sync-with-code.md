# docx 文档全面对齐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `docx/` 下 4 份文档全面对齐到当前「Java + 外部 Python AI 引擎 + 知识库/工具/提示词模板」代码实态。

**Architecture:** 按文档分 4 批（业务流程 → 技术架构 → 本地启动 → 前端对接），每批「读相关代码 → 更新文档 → code review → `git add`」；Python 引擎作为外部依赖对接（写集成边界 + 启动依赖，不深入 Python 内部）；整个变更随 `docs/changelogs/2026-06-28-docx-sync-with-code.md` 记录。

**Tech Stack:** Markdown 文档。信息源 = Java 源码（Controller/Entity/Service/Config）、`schema.sql`、已有 changelog/spec。

## Global Constraints

- 只改 `docx/` 下 4 份 `.md` + 1 份新建 changelog；**不改任何 Java/SQL/yml 代码**。
- 接口路径/字段/枚举一律以当前代码为准，**不凭记忆**；不确定处标注而不臆造。
- 中文撰写，匹配现有文档风格与术语（实体 `DO`、`Result<T>`、`BizException`、`UserContext` 等）。
- **不自动 commit**：每批 code review 通过后仅 `git add`，提交时机由用户决定（CLAUDE.md 九.2 优先于本 skill 的 commit 步骤）。
- 向前兼容：新增内容，不删仍有效的旧信息（已明确废弃者除外，如 `config_json` 标废弃但保留）。
- changelog 路径固定为 `docs/changelogs/`（复数）。
- 单批逼近 token 预算时做检查点、跨批续作（CLAUDE.md 规则六）。

---

## Task 1: 重写 业务流程.md + 建 changelog 骨架

**Files:**
- Rewrite（整体覆盖）: `docx/业务流程.md`
- Create: `docs/changelogs/2026-06-28-docx-sync-with-code.md`

**Sources to read（提取接口路径/入参/流程，不凭记忆）:**
- 认证：`acg-user/src/main/java/com/darkness/auth/controller/AuthController.java`、`auth/service/impl/AuthServiceImpl.java`
- RBAC：`acg-user/.../user/controller/{UserController,RoleController,PermissionController}.java` + 各 `service/impl`
- 个人中心：`acg-user/.../user/profile/controller/ProfileController.java`
- Agent：`acg-chat/.../agent/controller/AgentController.java`、`agent/service/impl/AgentServiceImpl.java`
- 对话：`acg-chat/.../agent/controller/ChatController.java`、`agent/service/impl/ChatServiceImpl.java`、`agent/client/PythonAiClient.java`
- 知识库/文档：`acg-chat/.../kb/controller/{KnowledgeBaseController,DocumentController}.java` + `service/impl`
- 工具：`acg-chat/.../tool/controller/ToolController.java`、`tool/service/impl/ToolServiceImpl.java`
- 提示词模板：`acg-chat/.../prompt/controller/PromptController.java`、`prompt/service/impl/PromptServiceImpl.java`
- 公共能力/枚举：`acg-common/.../result/Result.java`、`exception/BizException.java`、`util/UserContext.java`、`enums/{CommonStatus,MessageRole,AgentCategory,PromptMode,PermissionType,TokenType,UsedStatus}.java`

- [ ] **Step 1.1：读取上述源码，按业务域提取事实**

  每个域记录：接口（HTTP 方法 + 路径 + 认证要求）、关键入参/出参字段、核心流程、是否走 Python 引擎。整理成临时要点（不必落盘，供 Step 1.2 写作）。

- [ ] **Step 1.2：整体重写 `docx/业务流程.md`**

  目标结构（覆盖式重写，删除 user-CRUD-only 旧内容）：
  1. **项目概述** — 一段话：微服务 + Python 引擎双后端，业务域清单。
  2. **公共能力** — `Result<T>`（code/message/data）、`BizException` + `GlobalExceptionHandler`、`UserContext`（从 `X-User-Id` 取用户）。
  3. **认证域** — 密码登录 / 短信验证码（6 位 5 分钟、未注册自动注册）/ 微信（OAuth2 自动绑定）；Access(2h)+Refresh(7d) Token。
  4. **RBAC 域** — 用户 / 角色（code 唯一）/ 权限（树形 MENU/BUTTON）CRUD + 用户-角色、角色-权限分配。
  5. **个人中心域** — 当前用户信息查询/修改（以 ProfileController 实际接口为准）。
  6. **Agent 域** — Agent CRUD、`AgentCategory`(CHAT/VIDEO/IMAGE) 分类、创建/更新时经 `PythonAiClient` 同步到 Python、`python_agent_id` 同步锚点。
  7. **对话域** — 创建会话、SSE 流式发送：`ChatServiceImpl` → `PythonAiClient.streamChat` → Python 引擎，结构化 `ChatEvent` 透传前端，流结束落库助手消息。
  8. **知识库/文档域** — 代理 Python；**Python 为知识库存储真相源，Java 不落库**。
  9. **工具域** — 代理 Python（ToolServiceImpl 转发 PythonAiClient）。
  10. **提示词模板域** — 模板库（公共 user_id=NULL + 用户私有）、generate/apply、`PromptMode`(acg/compliant)。
  11. **关键时序图（plantuml，≥3）** — ① 登录鉴权（Gateway JWT）；② 对话 SSE 流（含 Python 引擎节点）；③ Agent 创建 → 同步 Python（AgentSyncMigrationRunner 存量同步可作注脚）。

- [ ] **Step 1.3：创建 changelog 骨架 `docs/changelogs/2026-06-28-docx-sync-with-code.md`**

  含：标题、日期、变更原因（docx 滞后于代码）、影响范围（4 文档）、各文档条目占位（本批填「业务流程.md 重写」一条，其余批次追加）。格式参照同目录既有 changelog。

- [ ] **Step 1.4：验证（doc 的「测试」）— 逐项核对**

  checklist：
  - [ ] 全文无 `user` 单表 / 明文密码 / `createTime` / "枚举：暂无" / "工具类：暂无" 等废弃表述。
  - [ ] 7 个业务域 + 个人中心均有覆盖。
  - [ ] 时序图 ≥3，且对话时序图含 Python 引擎节点。
  - [ ] 所有出现的接口路径/枚举值与 Step 1.1 读取的源码一致。

- [ ] **Step 1.5：code review + `git add`**

  调用 code-review skill 审 `docx/业务流程.md` + 新 changelog；修复后 `git add docx/业务流程.md docs/changelogs/2026-06-28-docx-sync-with-code.md`（**不 commit**，交用户）。

---

## Task 2: 修补 技术架构.md

**Files:**
- Modify: `docx/技术架构.md`（§2 包结构、§5 SSE、新增 Java↔Python 章节、§7 表清单/ER、§8 Nacos、§9 拓扑）

**Sources to read:**
- 包结构/集成：`acg-chat/.../agent/client/PythonAiClient.java`、`agent/migration/AgentSyncMigrationRunner.java`、`config/PythonAgentProperties.java`、`config/WebClientConfig.java`
- 表/枚举：`acg-user/src/main/resources/db/schema.sql`（agent 全列、`prompt_template`）、`acg-common/.../entity/AgentDO.java`、`enums/{AgentCategory,PromptMode}.java`
- Nacos 持久化：`docs/changelogs/2026-06-27-nacos-persistence-to-acg_agent-db.md`、`docx/nacos_config/` 相关

- [ ] **Step 2.1：§2 包结构订正**

  acg-chat 子树补：`agent/client/PythonAiClient`（替代旧的「AgentClient — WebClient SSE 调用 LLM」表述）、`agent/migration/AgentSyncMigrationRunner`、`config/PythonAgentProperties`、`kb/`(controller+service)、`tool/`、`prompt/`、`user/profile/`（acg-user 侧）。acg-common 枚举行补 `AgentCategory`、`PromptMode`。

- [ ] **Step 2.2：§5 SSE 流式通信改写**

  流程改为：前端 → `POST /api/chat/conversations/{id}/send` → `ChatServiceImpl`（落用户消息）→ `PythonAiClient.streamChat` → Python 引擎 → 结构化 `ChatEvent` 经 `SseEmitter` 透传 → 流结束落助手消息。事件类型/字段以 `PythonAiClient`/`ChatServiceImpl` 实际为准（读取后填入）。

- [ ] **Step 2.3：新增章节「Java ↔ Python AI 引擎集成边界」**

  插在 §5 之后或 §4 之后。内容：PythonAiClient 职责（HTTP 调 Python）；Python 为**知识库/文档存储真相源，Java 不落库**；`python_agent_id` 同步锚点（未同步则对话拒绝）；`AgentSyncMigrationRunner` 存量 agent 同步；`PythonAgentProperties` 配置（base url 等，以代码为准）；工具/提示词生成亦经 PythonAiClient。

- [ ] **Step 2.4：§7 数据库设计更新**

  - 表清单表新增 `prompt_template` 行（字段以 schema.sql 为准）。
  - `agent` 行字段补齐 12 新列：category, system_prompt, provider, temperature, max_tokens, top_p, memory_type, memory_max_tokens, capabilities, knowledge_base_ids, tool_ids, python_agent_id。
  - ER 图 `agent` 实体同步补这些列。
  - 技术规范/枚举处补 `AgentCategory`(CHAT/VIDEO/IMAGE)、`PromptMode`(acg/compliant)。

- [ ] **Step 2.5：§8 Nacos 补持久化**

  补一条：Nacos 配置/服务元数据**持久化到 `acg_agent` 数据库**（MySQL），非默认嵌入式 Derby。细节参照 `docs/changelogs/2026-06-27-nacos-persistence-to-acg_agent-db.md`。

- [ ] **Step 2.6：§9 服务拓扑/调用链路加 Python 节点**

  plantuml 拓扑图与调用链路文本新增「Python AI 引擎(acgagent-ai)」节点：`acg-chat →(HTTP/SSE)→ Python 引擎`。

- [ ] **Step 2.7：验证 checklist**

  - [ ] §2 含 `PythonAiClient`、`kb/tool/prompt`、`AgentSyncMigrationRunner`、`PythonAgentProperties`；无「AgentClient 调 LLM」残留。
  - [ ] 有「Java↔Python 集成边界」章节，且写明 Python 为 KB 真相源。
  - [ ] §7 含 `prompt_template` + agent 12 新列 + 两个新枚举。
  - [ ] §8 含 Nacos 持久化到 acg_agent；§9 拓扑含 Python 节点。

- [ ] **Step 2.8：code review + `git add`**

  审 `docx/技术架构.md`；追加 changelog「技术架构.md 修补」条目；`git add docx/技术架构.md docs/changelogs/2026-06-28-docx-sync-with-code.md`（不 commit）。

---

## Task 3: 增补 本地启动指南.md

**Files:**
- Modify: `docx/本地启动指南.md`

**Sources to read:**
- Python 依赖：`acg-chat/.../config/PythonAgentProperties.java`（base url 默认值）、`docs/superpowers/specs/2026-06-14-java-python-ai-integration-design.md`（Python 服务定位/启动，仅取对接所需）
- Nacos 持久化：`docs/changelogs/2026-06-27-nacos-persistence-to-acg_agent-db.md`
- agent 迁移：`acg-user/src/main/resources/db/2026-06-14-agent-python-sync.sql`

- [ ] **Step 3.1：新增「启动 Python AI 引擎」步骤**

  在「第一步：启动基础设施」之后、启动微服务之前，加一节：对话/知识库/工具/文档依赖外部 Python acgagent-ai 引擎，需先启动（指向其项目；给出 base url 配置项 `PythonAgentProperties` 默认值；不展开 Python 内部）。

- [ ] **Step 3.2：补 Nacos 持久化说明**

  在 Nacos 配置节补：Nacos 持久化到 `acg_agent` 库（启动顺序上 Nacos 依赖 MySQL 就绪已隐含，点明即可）。

- [ ] **Step 3.3：补 agent 迁移提示**

  在「初始化数据库」节补：**已存在的旧 `agent` 表**需执行 `acg-user/src/main/resources/db/2026-06-14-agent-python-sync.sql`（含 category 共 12 列）对齐；全新建库直接用 `schema.sql`。

- [ ] **Step 3.4：验证示例补对话/KB**

  「第五步：验证接口」补一条：创建会话并发送消息（或查 KB 列表），确认 Python 引擎联通。

- [ ] **Step 3.5：验证 checklist**

  - [ ] 有 Python 引擎启动步骤，且置于微服务启动之前。
  - [ ] 有 Nacos 持久化说明、agent 迁移提示。
  - [ ] 新增示例命令与实际接口一致。

- [ ] **Step 3.6：code review + `git add`**

  审 `docx/本地启动指南.md`；追加 changelog 条目；`git add`（不 commit）。

---

## Task 4: 增补 前端对接指南.md

**Files:**
- Modify: `docx/前端对接指南.md`

**Sources to read:**
- KB/文档：`acg-chat/.../kb/controller/{KnowledgeBaseController,DocumentController}.java`
- 工具：`acg-chat/.../tool/controller/ToolController.java`
- 提示词：`acg-chat/.../prompt/controller/PromptController.java`
- Agent/枚举/VO：`acg-chat/.../agent/controller/AgentController.java`、`acg-common/.../enums/{AgentCategory,PromptMode}.java`、`acg-common/.../model/AgentVO.java`、相关 Request DTO

- [ ] **Step 4.1：通读 `docx/前端对接指南.md` 全文**

  标记现有内容新旧程度，定位应插入新接口的章节位置。

- [ ] **Step 4.2：读取上述 Controller，提取接口契约**

  每个接口：HTTP 方法 + 路径 + 认证要求 + 入参字段 + 出参结构 + 错误码要点。

- [ ] **Step 4.3：补 KB/文档/工具/提示词模板接口章节**

  按现有接口详情体例（入参表 + 出参表 + 请求/响应示例）补齐。提示词模板域含 `PromptMode` 枚举说明。

- [ ] **Step 4.4：补 Agent 分类枚举 + AgentVO 新字段**

  枚举表加 `AgentCategory`(CHAT/VIDEO/IMAGE)；AgentVO 字段表补 category 等新字段（以 `AgentVO.java` 为准）。

- [ ] **Step 4.5：验证 checklist**

  - [ ] KB/文档/工具/提示词模板接口齐全且与 Controller 一致。
  - [ ] 含 `AgentCategory`、`PromptMode` 枚举；AgentVO 新字段已补。
  - [ ] 免认证接口清单（§3）与 Gateway 白名单一致。

- [ ] **Step 4.6：code review + `git add`**

  审 `docx/前端对接指南.md`；追加 changelog 末批条目，确保 changelog 覆盖全部 4 文档；`git add`（不 commit）。

---

## Self-Review（已执行）

- **Spec 覆盖：** spec §3 的 4 个 Done 定义 → Task 1-4 各自的验证 checklist 一一对应；spec §5 各文档计划 → Task 1-4 的 Steps 覆盖；spec §7 changelog → Task 1 建、Task 2-4 追加。无遗漏。
- **占位符扫描：** 无 TBD/TODO；凡「以代码为准」处均为「先读指定源码文件再填」的可执行指令（文件路径精确），非空占位。
- **一致性：** changelog 文件名 `2026-06-28-docx-sync-with-code.md` 全文统一；Python 引擎定位（外部依赖、KB 真相源）跨任务一致；枚举名 `AgentCategory`/`PromptMode` 统一。
