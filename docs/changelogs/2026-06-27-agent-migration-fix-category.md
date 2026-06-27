# 2026-06-27 修正 agent 表迁移脚本遗漏的 category 列

> 日期：2026-06-27

## 变更原因

`acg-user/src/main/resources/db/2026-06-14-agent-python-sync.sql` 用于把【已存在的】旧 `agent` 表对齐到最新结构。但该脚本只 ALTER 了 11 列（Python 引擎相关），且 11 条 `ADD COLUMN` 全部以 `AFTER category` 定位，却**唯独没有给 `category` 这列写 `ADD COLUMN`**。

后果：若环境里的旧 `agent` 表连 `category` 都没有（`schema.sql` 用 `CREATE TABLE IF NOT EXISTS`，已存在的旧表不会被更新结构），执行该迁移脚本首条即报 `Unknown column 'category'`；服务运行时 `SELECT ... FROM agent`（`AgentDO` 实体含 `category` 字段）也报同样错误。

## 影响范围

- 仅迁移脚本与文档变更，**不改表结构本身**（`schema.sql` 第 80-105 行早含 `category` 列，`AgentDO` 实体未变）。
- 受影响：所有用旧库（缺 `category` 列）执行迁移的环境。新部署 / 全新建库不受影响。

## 变更前后对比

`2026-06-14-agent-python-sync.sql`：

- 改前：11 条 `ADD COLUMN`，首条为 `ADD COLUMN system_prompt ... AFTER category`，缺 `category` 的 `ADD COLUMN`。
- 改后：12 条 `ADD COLUMN`，首条补入
  `ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'CHAT' ... AFTER config_json`，
  其后 11 列 `AFTER` 引用顺次衔接，脚本自洽。

执行后，旧库 `agent` 表结构与 `schema.sql` 第 80-105 行定义一致。

## 注意

- 一次性 ALTER（MySQL < 8.0.29 不支持 `ADD COLUMN IF NOT EXISTS`），仅执行一次；列已存在会报错，执行前先 `DESCRIBE agent;` 确认。
- 全新建库仍直接用 `schema.sql`（已含全部列），无需此迁移脚本。
- 本修复不涉及 git 提交，由开发者 review 后自行提交。
