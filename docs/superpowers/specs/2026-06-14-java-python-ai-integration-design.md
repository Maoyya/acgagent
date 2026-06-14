# Java ↔ Python AI 引擎集成 · 系统设计文档

| 项 | 值 |
|---|---|
| 日期 | 2026-06-14 |
| 状态 | 待评审 |
| 范围 | acg-chat 模块改造 + acg-common/gateway 联动 + 前端 SSE 适配 |
| 关联项目 | Java：`C:\Users\10173\IdeaProjects\acgagent`；Python：`C:\Users\10173\PycharmProjects\acgagent-ai`（FastAPI :8100）；前端：`D:\vscodeproject\acgagent-web` |

---

## 1. 背景与目标

### 1.1 现状

Java acg-chat 模块当前**直接调用 LLM**（`AgentClient` 用 WebClient SSE 走 OpenAI 兼容协议），只能做"裸对话"，没有 RAG、工具调用、工作流编排能力。

Python `acgagent-ai`（FastAPI :8100）是一个**完整的 AI 引擎**：基于 LangChain/LangGraph，支持 RAG（ChromaDB 向量库）、工具调用（calculator/web_search/knowledge_search）、工作流编排、记忆管理。

### 1.2 目标

让 Python 成为**唯一的 AI 执行引擎**；Java acg-chat 升级为面向前端的**厚 BFF**，负责鉴权、会话/消息持久化、Agent 配置管理、并把 AI 请求转发给 Python。

### 1.3 非目标

- 不改 acg-user、acg-gateway 的鉴权与路由核心逻辑（仅 gateway 增路由）
- 不改 Python 侧 API（M1 决策，零改 Python）
- 本期不做"Python 不可用时降级回直连 LLM"的 fallback

---

## 2. 现状分析

### 2.1 Java acg-chat 接口盘点

| 接口 | 当前实现 | 是否"做 AI" |
|---|---|:---:|
| `POST /api/chat/conversations/{id}/send` | `AgentClient.stream()` 直连 LLM（SSE） | **是** |
| `POST/GET/DELETE /api/chat/conversations*` | MySQL 读写 | 否 |
| `GET /api/chat/conversations/{id}/messages` | MySQL 查（含 IDOR 校验） | 否 |
| `GET /api/agents`、`GET /api/agents/{id}` | MySQL 查（脱敏） | 否 |
| `POST/PUT/DELETE /api/agents` | MySQL CUD | 否（仅配置） |

### 2.2 Python acgagent-ai 能力盘点（:8100，`/api/v1`，`X-API-Key` 认证）

| 端点 | 能力 |
|---|---|
| `POST /api/v1/chat/{agent_id}/completions` | 流式对话（SSE 结构化 `ChatEvent`），按 capabilities 路由 chat/tool_use/workflow |
| `/api/v1/agents` CRUD | Agent 配置（JSON 存储，含 llm/memory/capabilities/kb/tool/prompt） |
| `/api/v1/knowledge-bases` CRUD | 知识库（embedding + 分块配置，ChromaDB） |
| `/api/v1/knowledge-bases/{kb_id}/documents` | 文档上传/列表/删除（RAG 摄取） |
| `/api/v1/tools` CRUD | 工具管理（内置 calculator/web_search/knowledge_search） |
| `GET /api/v1/health` | 健康检查（免认证） |

### 2.3 三个绕不开的冲突

1. **两套 Agent 存储**：Java MySQL（Long id，仅 apiUrl/apiKey/model）vs Python JSON（string id，含 capabilities/kb_ids/tool_ids/system_prompt/memory_config 等丰富字段）。
2. **会话记忆归属**：Java `message` 表全量历史 vs Python `ConversationMemory`（按 conversation_id 自管 + token 裁剪）。Python 的 `ChatRequest` 只收 `conversation_id`，不接受外部历史。
3. **SSE 协议差异**：Java 现给前端推**裸文本 token**；Python 推**结构化 `ChatEvent` JSON**（content/tool_call/tool_result/thinking/error/done）。

---

## 3. 设计决策

| 决策点 | 结论 | 理由 |
|---|---|---|
| 集成范围 | **③ Java 作统一 BFF，全部代理到 Python** | 前端只对接 Gateway，体感"高级" |
| Agent 配置真相源 | **做法 A：Java 为准，CUD 时推送 Python，Java 存 `python_agent_id`** | 前端统一入口，单一真相源 |
| 会话记忆 | **M1：双写（MySQL 给 UI，Python memory 给 LLM 上下文），零改 Python** | 外科手术式修改，最快落地；语义上各司其职不漂移 |
| SSE 协议 | **S2：透传结构化 `ChatEvent`，前端按 type 渲染** | 展示工具调用过程，对得起集成价值 |
| BFF 厚度 | **厚 BFF + 完整领域模型（VO/DO/Result/校验）** | 类型安全、契约稳定、符合项目规约三/十一 |
| 落地节奏 | **分两期：Phase A 核心闭环，Phase B 管理后台** | 降低单次改动风险 |
| Agent CUD 同步失败 | **`@Transactional` 包裹，Python 失败 → 抛异常 → MySQL 回滚** | 事务一致性（用户明确要求） |

---

## 4. 整体架构

```
前端 acgagent-web
    │  JWT
    ▼
Gateway :8080  (鉴权 + 白名单 + 路由，本期仅增路由)
    │
    ├──▶ acg-user :8081   (用户/认证/RBAC，不变)
    │
    └──▶ acg-chat :8082   (本期改造)
              │
              │  新增 PythonAiClient (WebClient)
              │  Header: X-API-Key  +  X-User-Id(透传)
              ▼
         Python acgagent-ai :8100  (AI 引擎：LLM/RAG/工具/工作流)
              │
              ▼
         各家 LLM + ChromaDB
```

**职责划分**：
- Java acg-chat：鉴权、会话/消息持久化（MySQL）、Agent 配置真相源（MySQL + 同步）、AI 请求转发、`Result<T>`/VO/校验
- Python：LLM 调用、RAG、工具、工作流、KB/Doc/Tool 存储

### 4.1 acg-chat 组件变更

| 组件 | 类型 | 职责 |
|---|---|---|
| `agent/client/PythonAiClient` | 新增 | 所有调 Python 的 HTTP（含 SSE 流式），`@ConfigurationProperties("python-agent")` 注入配置 |
| `AgentClient`（现有） | **删除** | 直连 LLM 逻辑被 Python 取代 |
| `AgentServiceImpl` | 改造 | CUD 加 `@Transactional` + Python 同步 |
| `ChatServiceImpl.sendMessage` | 改造 | SSE 源改为 Python，透传结构化事件 |
| `kb/`、`tool/` 新包（Phase B） | 新增 | KB/Document/Tool 代理 Controller + Service |

---

## 5. 接口契约总表（哪些接口调下游 Python）

| Java 接口 | 调 Python? | Python 端点 | 改造说明 |
|---|:---:|---|---|
| **对话流** | | | |
| `POST /api/chat/conversations/{id}/send` | ✅ 改 | `POST /api/v1/chat/{python_agent_id}/completions`（SSE） | 原直连 LLM → 改调 Python，透传 `ChatEvent` |
| `POST/GET/DELETE /api/chat/conversations*`、`GET .../messages` | ❌ | — | Java MySQL，不变 |
| **Agent 配置** | | | |
| `GET /api/agents`、`GET /api/agents/{id}` | ❌ | — | Java MySQL（脱敏），不变 |
| `POST /api/agents` | ✅ 同步 | `POST /api/v1/agents` | Java 写 MySQL + 建 Python agent，回写 `python_agent_id` |
| `PUT /api/agents/{id}` | ✅ 同步 | `PUT /api/v1/agents/{python_agent_id}` | Java 更新 MySQL + 同步 Python |
| `DELETE /api/agents/{id}` | ✅ 同步 | `DELETE /api/v1/agents/{python_agent_id}` | Java 删 MySQL + 删 Python |
| **知识库 / 文档 / 工具（Phase B 新增）** | | | |
| `/api/knowledge-bases/**` | ✅ 新 | `/api/v1/knowledge-bases/**` | 代理，admin 鉴权 |
| `/api/knowledge-bases/{kbId}/documents/**` | ✅ 新 | `/api/v1/knowledge-bases/{kbId}/documents/**` | 代理，含 multipart 文件上传 |
| `/api/tools/**` | ✅ 新 | `/api/v1/tools/**` | 代理，admin 鉴权 |

**一句话**：调 Python 的 Java 接口 = **1 个对话流（改）+ 3 个 Agent CUD（同步）+ KB/Doc/Tool 一组（Phase B 新增代理）**；其余会话/消息/查询接口全留 Java MySQL，不动。

---

## 6. Phase A 设计（核心闭环）

### 6.1 Agent 表结构变更（向前兼容，只加字段）

`agent` 表新增列：

| 新列 | 类型 | 说明 |
|---|---|---|
| `description` | VARCHAR(255) | Agent 描述 |
| `system_prompt` | TEXT | 系统提示词 |
| `provider` | VARCHAR(32) | LLM 厂商：doubao/qwen/deepseek |
| `temperature` | DECIMAL(3,2) | 默认 0.7 |
| `max_tokens` | INT | 默认 4096 |
| `top_p` | DECIMAL(3,2) | 默认 0.9 |
| `memory_type` | VARCHAR(32) | 默认 conversation_window |
| `memory_max_tokens` | INT | 默认 8000 |
| `capabilities` | JSON | 如 `["chat","rag","tool_use"]` |
| `knowledge_base_ids` | JSON | 关联知识库 id 列表 |
| `tool_ids` | JSON | 关联工具 id 列表 |
| `python_agent_id` | VARCHAR(64) | **同步锚点**，同步成功后回写，初始 NULL |

**Java AgentDO ↔ Python AgentConfig 字段映射**：

| Java AgentDO | Python AgentConfig |
|---|---|
| apiUrl | llm_config.base_url |
| apiKey（真实值，非脱敏） | llm_config.api_key |
| model / provider / temperature / max_tokens / top_p | llm_config.* |
| memory_type / memory_max_tokens | memory_config.type / memory_config.max_tokens |
| system_prompt, capabilities, knowledge_base_ids, tool_ids, status, name | 同名 |

> JSON 列存列表字段（用户已确认）。需同步更新 `acg-user/src/main/resources/db/schema.sql`，并在 `docs/changelogs/` 写变更日志（项目规约五.3）。

### 6.2 Agent 配置同步流程（做法 A，事务一致）

**CREATE**：插 MySQL（`python_agent_id=NULL`）→ 调 Python `POST /agents`（带真实 apiKey）→ 回写 `python_agent_id`。Python 失败 → 抛异常 → `@Transactional` 回滚（删除刚插入的行）。

**UPDATE**：取旧 `python_agent_id`（为空报错"先重建"）→ 更新 MySQL（apiKey 掩码则保留原值）→ 调 Python `PUT`。Python 失败 → 抛异常 → `@Transactional` 回滚到旧值。

**DELETE**：取 `python_agent_id` → 删 MySQL → 调 Python `DELETE`。Python 失败 → 抛异常 → `@Transactional` 回滚（恢复行）。

> 同步逻辑放 `AgentServiceImpl`（不新建类，沿用现有 CUD 方法扩展，Rule 3）。失败一律抛 `BizException` + 日志（Rule 12）。
>
> **注意**：Python HTTP 调用在 `@Transactional` 内，DB 事务会在 HTTP 往返期间保持打开。Agent CUD 是低频管理操作可接受，但 `PythonAiClient` 必须配超时（见 8.1）。

### 6.3 对话流改造（`POST /api/chat/conversations/{id}/send`）

`ChatServiceImpl.sendMessage` 新流程：

1. 校验会话归属（不变，防 IDOR）
2. 加载 AgentDO → **校验 `python_agent_id` 非空**（空则 `BizException`"Agent 未同步到 AI 引擎"）
3. 持久化用户消息到 MySQL（不变，给前端历史 UI）
4. **不再查历史喂 LLM**（Python 按 conversation_id 自管 memory，M1）
5. `SseEmitter` + 现有线程池 → `PythonAiClient.streamChat(python_agent_id, conversation_id, content, userId)`
6. `doOnNext`：透传 `ChatEvent` JSON 给前端（S2）；累计 `type=content` 文本
7. 收 `type=done` → 持累计文本持久化助手消息 + 完成 emitter
8. 收 `type=error` → `completeWithError`，**不持久化残缺回复**

**记忆映射（M1）**：Java 会话 `id`(Long) → 字符串作 Python `conversation_id`；Python 维护自己的 memory；Java 每轮各存 user/assistant 一条到 MySQL。两边按"每轮 user+assistant"对齐，不漂移。

### 6.4 Phase A 改动文件清单

| 文件 | 动作 |
|---|---|
| `acg-common/entity/AgentDO.java` | 加字段 |
| `acg-common/model/AgentVO.java` | 加字段 + `toEntity/from` 映射 |
| `acg-common/model/ChatEvent.java` | 新增 DTO（镜像 Python） |
| `acg-common/constant/` | 新增 Python 路径/请求头常量 |
| `acg-chat/agent/client/PythonAiClient.java` | 新增 |
| `acg-chat/agent/client/AgentClient.java` | 删除 |
| `acg-chat/agent/service/impl/AgentServiceImpl.java` | CUD 加同步 |
| `acg-chat/agent/service/impl/ChatServiceImpl.java` | sendMessage 改造 |
| `acg-user/src/main/resources/db/schema.sql` | agent 表加列 |
| `docs/changelogs/2026-06-14-agent-python-sync.md` | 变更日志 |
| `acg-chat` 单元测试 | AgentServiceImpl 同步、ChatServiceImpl SSE 转发、PythonAiClient 解析 |
| 存量数据迁移脚本 | 为存量 agent 创建 Python agent 并回写 `python_agent_id` |

---

## 7. Phase B 设计（KB / Doc / Tool 代理）

### 7.1 存储策略：纯代理，不落 MySQL

Agent 是"Java 真相源 + 推送 Python"。但 **KB/Doc/Tool 不复刻**——Python 侧已有完整存储（ChromaDB、JSON 配置、embedding/分块参数），Java 再建 MySQL 是冗余且易漂移。

Phase B 是**纯代理**：`Java Controller → Java Service（校验 + VO 映射）→ PythonAiClient → Python`，无 MySQL。Python 是 KB/Doc/Tool 的存储真相源。

### 7.2 新增包结构（acg-chat）

```
com.darkness
├── agent/        (现有，Phase A 改造)
├── kb/           (新增)
│   ├── controller/  KnowledgeBaseController, DocumentController
│   └── service/     KnowledgeBaseService(+Impl), DocumentService(+Impl)
├── tool/         (新增)
│   ├── controller/  ToolController
│   └── service/     ToolService(+Impl)
└── config/
```

### 7.3 接口契约（全部 `@RequireRole("admin")`）

| Java 接口 | 方法 | Python 端点 |
|---|---|---|
| `/api/knowledge-bases` | GET / POST | `/api/v1/knowledge-bases` |
| `/api/knowledge-bases/{id}` | GET / PUT / DELETE | `/api/v1/knowledge-bases/{id}` |
| `/api/knowledge-bases/{kbId}/documents` | POST(multipart) / GET | `/api/v1/knowledge-bases/{kbId}/documents` |
| `/api/knowledge-bases/{kbId}/documents/{docId}` | GET / DELETE | `.../documents/{docId}` |
| `/api/tools` | GET / POST | `/api/v1/tools` |
| `/api/tools/{id}` | GET / DELETE | `/api/v1/tools/{id}` |

**Java VO（厚 BFF，控制 schema）**：
- `KnowledgeBaseVO` / `KnowledgeBaseRequest`：name, description, embeddingConfig{provider,model,baseUrl,apiKey}, chunkConfig{chunkSize,chunkOverlap}
- `DocumentVO`：id, kbId, filename, status, chunkCount
- `ToolVO` / `ToolCreateRequest`：name, description, type（内置工具不可删）

> Python 的 `document` / `tool` 模型精确字段，实现阶段先读 `app/models/document.py`、`app/models/tool.py` 再对齐 Java VO。

### 7.4 Gateway 路由

`acg-gateway.yaml`（Nacos）新增路由：`/api/knowledge-bases/**`、`/api/tools/**` → `lb://acg-chat`。**不在白名单**（需 JWT 认证），Java Controller `@RequireRole("admin")`。

### 7.5 PythonAiClient 扩展

新增方法（均带 `X-API-Key`）：`listKb/getKb/createKb/updateKb/deleteKb`、`uploadDocument`(multipart 转发)/`listDocuments/getDocument/deleteDocument`、`listTools/getTool/createTool/deleteTool`。

### 7.6 Phase B 改动文件清单

| 文件 | 动作 |
|---|---|
| `acg-common/model/` | 新增 KnowledgeBaseVO/Request、DocumentVO、ToolVO/Request |
| `acg-chat/kb/**` | 新增 Controller + Service(+Impl) |
| `acg-chat/tool/**` | 新增 Controller + Service(+Impl) |
| `acg-chat/agent/client/PythonAiClient.java` | 扩展 KB/Doc/Tool 方法 |
| Nacos `acg-gateway.yaml` | 新增路由 |
| `docs/changelogs/` | API 新增日志 |

---

## 8. 横切关注点

### 8.1 配置（Nacos `acg-chat.yaml`）

实际属性前缀 **`python-agent`**：

```yaml
python-agent:
  base-url: http://127.0.0.1:8100        # Python 服务地址（按部署环境调整）
  api-key: 4l26****Waw                    # X-API-Key（真实值见 Nacos / Python .env，禁止提交明文）
  connect-timeout: 5000                   # 连接超时 ms
  read-timeout: 10000                     # 普通请求读超时 ms
  stream-read-timeout: 300000             # SSE 流式读超时 ms，对齐 chat.sse-timeout(5min)
```

> `api-key` 与 Python `acgagent-ai/.env` 的 `ACG_AI_API_KEY` 一致。`@ConfigurationProperties("python-agent")` 注入 `PythonAiClient`。api-key 仅服务端使用，绝不返回前端。

### 8.2 鉴权链路

```
前端 → Gateway(JWT 鉴权, 注入 X-User-Id) → acg-chat → Python(X-API-Key + X-User-Id 透传)
```

- Java→Python：`X-API-Key`（服务端持有）
- `X-User-Id`：从 `UserContext` 取透传 Python（多租户预留）
- KB/Doc/Tool：Gateway JWT + Java `@RequireRole("admin")`

### 8.3 错误处理矩阵

| 场景 | 处理 |
|---|---|
| Agent `python_agent_id` 为空（未同步） | `BizException`："Agent 未同步到 AI 引擎，请联系管理员重建" |
| 普通请求 Python 超时/不可达 | `PythonAiClient` 抛 → `BizException(503)` → `Result.error` |
| Python 返回 `Result.error(code≠200)` | `PythonAiClient` 解析后抛 `BizException(code, message)` 透传 |
| 对话 SSE 流中收 `type=error` | `emitter.completeWithError`，不持久化残缺回复 |
| Agent CUD 时 Python 同步失败 | `@Transactional` 回滚 MySQL + 抛 `BizException` |

### 8.4 SSE 转发实现要点

- `PythonAiClient.streamChat()` 用 `WebClient.bodyToFlux(ServerSentEvent<String>)` 解析 Python 标准 SSE，每个 `event.data()` 反序列化为 Java `ChatEvent`
- `ChatServiceImpl` 在现有线程池订阅 Flux，`doOnNext` 透传 `ChatEvent` JSON 给 `SseEmitter`，累计 `content`，`done` 时持久化 + complete
- 复用现有 `chat.sse-timeout`（5 分钟）与 `@PreDestroy` 线程池关闭逻辑

### 8.5 存量数据迁移（Phase A 必做）

现有 MySQL `agent` 记录没有 `python_agent_id` 和丰富字段。上线前跑一次性脚本：对每条存量 agent，按现有 apiUrl/apiKey/model + 默认 `capabilities=["chat"]` 调 Python `POST /agents` 创建，回写 `python_agent_id`。新列给默认值（memory 默认、kb/tool 空列表）。**否则存量 Agent 的对话全报"未同步"。**

### 8.6 ChatEvent Schema（前端契约）

Java `ChatEvent` 镜像 Python，固化如下（前端 SSE 解析据此实现）。

**线上报文为 snake_case**（Python pydantic `model_dump_json()` 默认输出）。Java `ChatEvent` DTO 用 `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` 或逐字段 `@JsonProperty("tool_name")` 对齐，避免反序列化失败。

```jsonc
// type ∈ { content, tool_call, tool_result, thinking, error, done }
{ "type": "content",     "content": "你好" }
{ "type": "tool_call",   "tool_name": "knowledge_search", "tool_input": { ... } }
{ "type": "tool_result", "tool_name": "...", "tool_output": "..." }
{ "type": "thinking",    "content": "..." }
{ "type": "done",        "usage": { "prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0 } }
{ "type": "error",       "code": 500, "message": "..." }
```

### 8.7 测试策略（项目规约七）

| 测试 | 覆盖意图 |
|---|---|
| `AgentServiceImplTest` | CREATE 成功（`python_agent_id` 回写）、CREATE Python 失败（回滚）、UPDATE Python 失败（回滚）、DELETE Python 失败（回滚）、apiKey 掩码保留原值 |
| `ChatServiceImplTest` | 正常流（content 累计 + done 持久化）、error 流（不持久化）、`python_agent_id` 空（报错）、会话越权（403） |
| `PythonAiClientTest` | SSE 报文解析（喂模拟 `data:` 行）、普通响应 `Result.error` 解析、超时 |
| Phase B Service 测试 | 代理正确转发 + Python 错误透传（`PythonAiClient` Mock） |

均用 Mock 隔离 Python（CI 不依赖真实 Python）；可选加一个真实 Python 的集成测试（本地手动跑）。

### 8.8 风险与取舍

| 风险 | 取舍 |
|---|---|
| Python 成为对话可用性硬依赖（Python 挂 → 全部对话不可用） | 本期不做降级 fallback（`AgentClient` 已删）。列为已知风险，后续可加"Python 不可用时降级"开关 |
| apiKey 经 HTTP 传 Python | 要求 Python 在可信内网，不暴露公网 |
| 前端依赖 Python `ChatEvent` schema | schema 在本文档与前端对接指南固化，变更走版本化 |
| `@Transactional` 包含 HTTP 调用 | 仅用于低频 Agent CUD；`PythonAiClient` 配超时避免事务长挂 |

---

## 9. 分期上线计划

### Phase A（核心闭环，可独立上线）

1. agent 表加列 + 变更日志
2. `ChatEvent` DTO + `PythonAiClient`（streamChat + Agent CUD 方法）
3. `AgentServiceImpl` 加 `@Transactional` 同步逻辑
4. `ChatServiceImpl.sendMessage` 改造
5. 删除 `AgentClient`
6. 存量 Agent 迁移脚本
7. 后端测试 → 后端上线 → 前端 SSE 解析改造上线

### Phase B（管理后台，可独立上线）

1. KB/Doc/Tool VO + Service/Controller
2. `PythonAiClient` 扩展 KB/Doc/Tool 方法
3. Gateway 路由 + admin 鉴权
4. 测试 → 上线

---

## 10. 验收标准（Definition of Done）

**Phase A**
- [ ] agent 表新增 12 列，存量数据迁移完成（所有 agent 有 `python_agent_id`）
- [ ] `POST /api/chat/conversations/{id}/send` 经 Python 返回结构化 SSE，前端能渲染 content/tool_call/done
- [ ] Agent CUD 同步 Python，Python 失败时 MySQL 回滚（单测验证）
- [ ] `AgentClient` 已删除，无残留引用
- [ ] 单元测试全部通过，无跳过

**Phase B**
- [ ] `/api/knowledge-bases/**`、`/api/tools/**` 经 Java 代理 Python，admin 鉴权生效
- [ ] 文档上传 multipart 正确转发
- [ ] 单元测试全部通过
