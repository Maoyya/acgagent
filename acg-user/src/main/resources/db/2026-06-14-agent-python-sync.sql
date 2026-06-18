-- =====================================================================
-- 迁移：agent 表新增 11 列（接入 Python AI 引擎）
-- 日期：2026-06-14
-- 对应变更日志：docs/changelogs/2026-06-14-agent-python-sync.md
-- 说明：仅给【已存在的】acg_agent 库执行；全新建库请直接用 schema.sql（已含这些列）。
-- 一次性 ALTER：本环境 MySQL 8.0.x < 8.0.29（不支持 ADD COLUMN IF NOT EXISTS），故用普通 ADD COLUMN，仅执行一次。
--       如需在 MySQL 8.0.29+ 上幂等重复执行，可给每个 ADD COLUMN 加回 "IF NOT EXISTS"。
-- 数据：本次仅改表结构；无存量 agent 时无需回填数据（新 agent 创建时由 AgentServiceImpl 同步 python_agent_id）。
-- =====================================================================

USE acg_agent;

ALTER TABLE agent
    ADD COLUMN system_prompt      TEXT         COMMENT '系统提示词，传给 Python 作为 Agent system_prompt'             AFTER category,
    ADD COLUMN provider           VARCHAR(32)  COMMENT 'LLM 厂商：doubao/qwen/deepseek'                              AFTER system_prompt,
    ADD COLUMN temperature        DECIMAL(3,2) DEFAULT 0.70 COMMENT '生成温度'                                              AFTER provider,
    ADD COLUMN max_tokens         INT          DEFAULT 4096 COMMENT '最大输出 token'                                        AFTER temperature,
    ADD COLUMN top_p              DECIMAL(3,2) DEFAULT 0.90 COMMENT 'Top-P 采样'                                             AFTER max_tokens,
    ADD COLUMN memory_type        VARCHAR(32)  NOT NULL DEFAULT 'conversation_window' COMMENT '记忆类型：conversation_window/summary/none' AFTER top_p,
    ADD COLUMN memory_max_tokens  INT          DEFAULT 8000 COMMENT '上下文窗口 token 数'                                   AFTER memory_type,
    ADD COLUMN capabilities       JSON         COMMENT '能力标签数组，如 ["chat","rag","tool_use"]'                   AFTER memory_max_tokens,
    ADD COLUMN knowledge_base_ids JSON         COMMENT '关联知识库 id 列表（Python 侧 string id）'                    AFTER capabilities,
    ADD COLUMN tool_ids           JSON         COMMENT '关联工具 id 列表（Python 侧 string id）'                      AFTER knowledge_base_ids,
    ADD COLUMN python_agent_id    VARCHAR(64)  COMMENT '同步锚点：Python 侧 agent 的 string id，未同步时为 NULL'     AFTER tool_ids;
