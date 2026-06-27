CREATE DATABASE IF NOT EXISTS acg_agent DEFAULT CHARACTER SET utf8mb4;
USE acg_agent;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    username VARCHAR(64) UNIQUE COMMENT '登录用户名，唯一',
    password VARCHAR(256) COMMENT '登录密码，BCrypt 加密存储',
    nickname VARCHAR(64) COMMENT '用户昵称，用于前端展示',
    email VARCHAR(128) COMMENT '邮箱地址',
    phone VARCHAR(20) UNIQUE COMMENT '手机号码，唯一',
    avatar VARCHAR(512) COMMENT '头像图片 URL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '系统用户表';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    name VARCHAR(64) NOT NULL COMMENT '角色名称，如"管理员"、"普通用户"',
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色编码，唯一标识，如"admin"、"user"，用于程序中权限判断',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    remark VARCHAR(256) COMMENT '备注说明',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '系统角色表';

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父权限 ID，顶级权限为 0，用于构建树形权限层级',
    name VARCHAR(64) NOT NULL COMMENT '权限名称，如"用户管理"、"新增用户"',
    code VARCHAR(128) NOT NULL COMMENT '权限编码，唯一标识，如"user:list"、"user:create"，用于程序中权限校验',
    type TINYINT NOT NULL DEFAULT 1 COMMENT '权限类型：1-菜单，2-按钮',
    path VARCHAR(256) COMMENT '前端路由路径，菜单类型时使用',
    icon VARCHAR(64) COMMENT '菜单图标标识',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '系统权限表，支持树形结构';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户 ID，关联 sys_user 表',
    role_id BIGINT NOT NULL COMMENT '角色 ID，关联 sys_role 表',
    PRIMARY KEY (user_id, role_id),
    INDEX idx_role_id (role_id)
) COMMENT '用户-角色关联表，多对多中间表';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL COMMENT '角色 ID，关联 sys_role 表',
    permission_id BIGINT NOT NULL COMMENT '权限 ID，关联 sys_permission 表',
    PRIMARY KEY (role_id, permission_id),
    INDEX idx_permission_id (permission_id)
) COMMENT '角色-权限关联表，多对多中间表';

CREATE TABLE IF NOT EXISTS wx_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    openid VARCHAR(128) NOT NULL UNIQUE COMMENT '微信用户唯一标识（openid），由微信 OAuth2 授权后返回',
    union_id VARCHAR(128) COMMENT '微信开放平台 unionid，用于跨应用识别同一用户，可为空',
    user_id BIGINT NOT NULL COMMENT '关联的系统用户 ID，对应 sys_user 表的主键',
    nickname VARCHAR(64) COMMENT '微信昵称，首次绑定时从微信用户信息中获取',
    avatar_url VARCHAR(512) COMMENT '微信头像 URL，首次绑定时从微信用户信息中获取',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '微信用户绑定关系表';

CREATE TABLE IF NOT EXISTS sms_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    phone VARCHAR(20) NOT NULL COMMENT '接收验证码的手机号',
    code VARCHAR(6) NOT NULL COMMENT '6 位数字验证码',
    used TINYINT NOT NULL DEFAULT 0 COMMENT '使用状态：0-未使用，1-已使用',
    expired_at DATETIME NOT NULL COMMENT '过期时间，超过此时间后验证码失效',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_phone_code (phone, code, used, expired_at)
) COMMENT '短信验证码记录表';

CREATE TABLE IF NOT EXISTS agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    name VARCHAR(128) NOT NULL COMMENT '智能体名称，用于前端展示',
    description VARCHAR(512) COMMENT '智能体功能描述',
    avatar VARCHAR(512) COMMENT '头像图片 URL',
    api_url VARCHAR(512) NOT NULL COMMENT '外部 LLM API 的完整地址，如 https://api.openai.com/v1/chat/completions',
    api_key VARCHAR(512) NOT NULL COMMENT '调用外部 API 的密钥',
    model VARCHAR(128) COMMENT '模型标识，如 gpt-4、claude-3-sonnet 等',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    config_json TEXT COMMENT '额外配置参数，JSON 格式，如 temperature、max_tokens 等（已废弃，保留以向前兼容）',
    category VARCHAR(32) NOT NULL DEFAULT 'CHAT' COMMENT 'Agent分类：CHAT-对话,VIDEO-视频,IMAGE-生图',
    system_prompt TEXT COMMENT '系统提示词，传给 Python 作为 Agent system_prompt',
    provider VARCHAR(32) COMMENT 'LLM 厂商：doubao/qwen/deepseek',
    temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT '生成温度',
    max_tokens INT DEFAULT 4096 COMMENT '最大输出 token',
    top_p DECIMAL(3,2) DEFAULT 0.90 COMMENT 'Top-P 采样',
    memory_type VARCHAR(32) NOT NULL DEFAULT 'conversation_window' COMMENT '记忆类型：conversation_window/summary/none',
    memory_max_tokens INT DEFAULT 8000 COMMENT '上下文窗口 token 数',
    capabilities JSON COMMENT '能力标签数组，如 ["chat","rag","tool_use"]',
    knowledge_base_ids JSON COMMENT '关联知识库 id 列表（Python 侧 string id）',
    tool_ids JSON COMMENT '关联工具 id 列表（Python 侧 string id）',
    python_agent_id VARCHAR(64) COMMENT '同步锚点：Python 侧 agent 的 string id，未同步时为 NULL',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT 'AI 智能体配置表';

CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    user_id BIGINT NOT NULL COMMENT '发起会话的用户 ID，关联 sys_user 表',
    agent_id BIGINT NOT NULL COMMENT '对话的 Agent ID，关联 agent 表',
    title VARCHAR(256) COMMENT '会话标题，通常由第一条消息自动生成或用户自定义',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) COMMENT '对话会话表';

CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    conversation_id BIGINT NOT NULL COMMENT '所属会话 ID，关联 conversation 表',
    role VARCHAR(16) NOT NULL COMMENT '消息角色：USER-用户发送，ASSISTANT-AI 回复',
    content TEXT NOT NULL COMMENT '消息正文内容',
    tokens INT COMMENT '消息消耗的 token 数，用于用量统计',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
    INDEX idx_conversation_id_created_at (conversation_id, created_at)
) COMMENT '对话消息表';

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
