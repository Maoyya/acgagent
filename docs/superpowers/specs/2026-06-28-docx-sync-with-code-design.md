# docx 文档全面对齐设计（2026-06-28）

## 1. 背景与目标

`docx/` 下 4 份文档严重滞后于代码。代码已演进到「Java + 外部 Python AI 引擎 + 知识库/工具/系统提示词模板」阶段，但文档基本停留在「纯 Java + 用户管理」阶段，部分文档（业务流程.md）仍属早期 user-CRUD-only 时代。

**目标：** 将 `docx/` 4 份文档全面对齐到当前代码实态；Python AI 引擎作为**外部依赖**对接（写集成边界 + 启动依赖，不深入 Python 内部）。本设计不改任何代码，只改文档。

## 2. 范围

**In scope（4 份文档）：**
- `docx/业务流程.md` — 重写
- `docx/技术架构.md` — 修补多个章节
- `docx/本地启动指南.md` — 增补
- `docx/前端对接指南.md` — 增补

**Out of scope：**
- Python acgagent-ai 引擎的内部架构/API/实现（独立项目，另有文档）
- 任何 Java/SQL/配置代码改动
- `docs/superpowers/` 下的 spec/plan、`docs/changelogs/` 历史日志本身

## 3. 成功标准（可验证）

| 文档 | Done 定义 |
|---|---|
| 业务流程.md | 覆盖 7 个业务域（认证、RBAC、Agent、对话、知识库/文档、工具、提示词模板）；含 ≥3 关键时序图（登录、对话 SSE、Agent 同步 Python）；**不含**任何已废弃表述（`user` 单表、明文密码、"枚举：暂无"、"工具类：暂无"） |
| 技术架构.md | §2 包结构含 `kb/tool/prompt` 包与 `PythonAiClient`；§5 SSE 描述 Java→Python；新增「Java↔Python 集成边界」章节；§7 表清单含 `prompt_template` + `agent` 全部新列 + `AgentCategory` 枚举；§8 Nacos 含持久化到 acg_agent |
| 本地启动指南.md | 含「启动 Python acgagent-ai 引擎」步骤；含 Nacos 持久化说明；含旧库 agent 迁移脚本提示 |
| 前端对接指南.md | 含 KB/文档/工具/提示词模板接口；含 Agent 分类（AgentCategory）枚举；AgentVO 新字段 |
| 通用 | 每份文档中的接口路径/字段/枚举均与当前代码逐一核对一致（不凭记忆） |

## 4. 执行策略：方案 A — 按文档分批

每批 = 一个文档，独立闭环：**读相关代码 → 更新文档 → code review → 提交（含 changelog 追加）**。

**顺序（按"过时程度 + 依赖"排）：**
1. 业务流程.md（最过时，重写；后续文档可引用其业务域划分）
2. 技术架构.md（架构真相源，前端/启动文档会引用）
3. 本地启动指南.md
4. 前端对接指南.md

**为何分批：** 符合 CLAUDE.md「小步提交」；每文档可独立验收；单批 token 可控；中断不产生散落半成品。

## 5. 各文档内容计划

### 5.1 业务流程.md（重写）
- 删除 user-CRUD-only 全部旧内容（表名 `user`、明文密码、createTime/updateTime 旧字段名、"枚举/工具类：暂无"）。
- 按业务域组织：认证（密码/短信验证码/微信，含自动注册）、RBAC（用户/角色/权限 CRUD + 分配）、Agent 管理（含 AgentCategory 分类、同步 Python）、对话（SSE 流式，Java→PythonAiClient→Python）、知识库与文档（代理 Python，Python 为存储真相源）、工具（代理 Python）、系统提示词模板（generate/apply）。
- 时序图：登录鉴权、对话 SSE 流（含 Python 引擎）、Agent 创建→同步 Python。
- 统一响应 `Result<T>`、`BizException`、`UserContext` 等公共能力说明。

### 5.2 技术架构.md（修补）
- §2 包结构：acg-chat 补 `agent/client/PythonAiClient`、`agent/migration/AgentSyncMigrationRunner`、`config/PythonAgentProperties`、`kb/`、`tool/`、`prompt/` 包；移除/订正「AgentClient — WebClient SSE 流式调用 LLM API」表述。
- §5 SSE：改为「ChatServiceImpl → PythonAiClient.streamChat → Python 引擎」；说明结构化 ChatEvent 透传（具体事件类型以代码为准）。
- **新增章节「Java ↔ Python AI 引擎集成边界」**：PythonAiClient 职责、Python 为知识库存储真相源（Java 不落库）、`python_agent_id` 同步锚点、AgentSyncMigrationRunner 存量同步、PythonAgentProperties 配置。
- §7 表清单：新增 `prompt_template`；`agent` 行补齐 12 个新列（category, system_prompt, provider, temperature, max_tokens, top_p, memory_type, memory_max_tokens, capabilities, knowledge_base_ids, tool_ids, python_agent_id）；ER 图 agent 实体同步更新；枚举补 `AgentCategory`。
- §8 Nacos：补「Nacos 配置持久化到 acg_agent 数据库」。
- §9 服务拓扑/调用链路：新增 Python AI 引擎节点（acg-chat → Python）。

### 5.3 本地启动指南.md（增补）
- **新增「启动 Python acgagent-ai 引擎」步骤**（作为对话/知识库/工具/文档的前置依赖；指向其项目，不展开内部）。
- Nacos 持久化说明（Nacos 使用 acg_agent 库持久化配置）。
- 旧库 agent 迁移提示：已存在的旧 `agent` 表需执行 `acg-user/src/main/resources/db/2026-06-14-agent-python-sync.sql`（含 category）对齐结构；全新建库直接用 schema.sql。
- 验证接口示例：补一条对话/知识库验证。

### 5.4 前端对接指南.md（增补）
- 先通读全文确认现有内容新旧程度。
- 补：知识库/文档接口、工具接口、系统提示词模板接口（路径/入参/出参以 Controller 为准）。
- 补 Agent 分类（AgentCategory：CHAT/VIDEO/IMAGE）枚举。
- 补 AgentVO 新字段（category 等）。

## 6. 信息来源（source of truth）

每批实现时按需读取，**不凭记忆**：
- 表结构：`acg-user/src/main/resources/db/schema.sql`
- 实体/枚举：`acg-common/.../entity/`、`enums/`（如 `AgentDO`、`AgentCategory`）
- 接口契约：各模块 Controller（`acg-chat` 的 agent/kb/tool/prompt、`acg-user` 的 auth/user/role/permission）
- 集成细节：`PythonAiClient`、`PythonAgentProperties`、`AgentSyncMigrationRunner`、`ChatServiceImpl`
- 历史变更：`docs/changelogs/`（尤其 2026-06-14-agent-python-sync、2026-06-15-phase-b-kb-doc-tool、2026-06-21-prompt-template、2026-06-27-nacos-persistence）、`docs/superpowers/specs/2026-06-14-java-python-ai-integration-design.md`

## 7. Changelog 处理

整个对齐作为一个逻辑变更，建 **`docs/changelogs/2026-06-28-docx-sync-with-code.md`**。
- 第 1 批（业务流程.md）时创建该文件骨架。
- 每批提交时，追加该文档的变更条目，与该批文档改动一起提交（保持小步整洁）。
- 末批完成时确保 changelog 覆盖全部 4 文档。

## 8. 风险与约束

- **不自动提交：** 依 CLAUDE.md 规约九.2，每批改完走 code review 后仅 `git add`（或交由用户），不自动 `git commit`。
- **准确性优先：** 接口/字段/枚举一律核对代码，宁缺毋错；不确定处标注而非臆造。
- **向前兼容表述：** 文档新增内容，不删除仍有效的旧信息（除非已废弃）。
- **token 预算：** 分批执行即为此；单批若逼近预算则做检查点、跨批续作。
