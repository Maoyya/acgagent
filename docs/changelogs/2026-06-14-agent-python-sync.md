# 2026-06-14 Agent 表扩展：接入 Python AI 引擎

> 日期：2026-06-14

## 变更原因

Java acg-chat 对话流改为调用 Python acgagent-ai 引擎（spec：`docs/superpowers/specs/2026-06-14-java-python-ai-integration-design.md`）。Java 成为 Agent 配置真相源，需存储 Python 所需的丰富配置与同步锚点。

## 影响范围

- 数据库：agent 表新增 11 列（description 已存在，未重复加）。
- API：无破坏性变更；Agent CUD 行为变为"同步推送 Python"。
- 架构：删除 Java 直连 LLM 的 AgentClient，对话流改走 Python。

## 变更前后对比

新增列：`system_prompt` / `provider` / `temperature` / `max_tokens` / `top_p` / `memory_type` / `memory_max_tokens` / `capabilities`(JSON) / `knowledge_base_ids`(JSON) / `tool_ids`(JSON) / `python_agent_id`。

`config_json` 标记废弃但保留（向前兼容，不删字段语义）。

## 存量迁移

对已存在的 agent 表执行（幂等），给出 ALTER 语句：

```sql
ALTER TABLE agent
  ADD COLUMN system_prompt TEXT COMMENT '系统提示词' AFTER category,
  ADD COLUMN provider VARCHAR(32) COMMENT 'LLM 厂商' AFTER system_prompt,
  ADD COLUMN temperature DECIMAL(3,2) DEFAULT 0.70 AFTER provider,
  ADD COLUMN max_tokens INT DEFAULT 4096 AFTER temperature,
  ADD COLUMN top_p DECIMAL(3,2) DEFAULT 0.90 AFTER max_tokens,
  ADD COLUMN memory_type VARCHAR(32) NOT NULL DEFAULT 'conversation_window' AFTER top_p,
  ADD COLUMN memory_max_tokens INT DEFAULT 8000 AFTER memory_type,
  ADD COLUMN capabilities JSON AFTER memory_max_tokens,
  ADD COLUMN knowledge_base_ids JSON AFTER capabilities,
  ADD COLUMN tool_ids JSON AFTER knowledge_base_ids,
  ADD COLUMN python_agent_id VARCHAR(64) AFTER tool_ids;
```

执行后由迁移程序为每条存量 agent 创建 Python agent 并回写 `python_agent_id`。

## 注意

- **MySQL 版本**：MySQL 8.0.29+ 才支持 `ADD COLUMN IF NOT EXISTS`；如目标版本不确定，上面的 ALTER 不带 `IF NOT EXISTS`，需人工确认未重复执行（重复执行会因列已存在而报错）。
- **未同步兜底**：存量 agent 在 `python_agent_id` 回写前，对话接口会返回"Agent 未同步到 AI 引擎"。
- **JSON 列映射**：`capabilities` / `knowledge_base_ids` / `tool_ids` 用 JSON 列，MyBatis-Plus 通过 JacksonTypeHandler 映射为 `List<String>`。
