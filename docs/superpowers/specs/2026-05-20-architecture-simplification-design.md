# 架构精简设计：单体应用 + 认证体系 + Agent SSE 流式对话

## 背景

当前项目是一个 Spring Cloud 微服务架构（网关 + Nacos + 多模块），存在以下问题：
- `User` / `UserDO` 两套实体，`UserMapper` / `UserDoMapper` 两套 Mapper，其中 User/UserMapper 是死代码
- Repository 层对 MyBatis-Plus BaseMapper 做了极薄包装，几乎没有增值
- Converter 层全是手写映射，可用更简洁的方式替代
- Schema 不一致：数据库名 `acgagentdb` vs `acg_agent`、`deleted` 列缺失、BaseEntity 缺少 deleted 字段
- RBAC 脚手架（Role/Permission/UserRole/RolePermission）全部搭好但没有 Service 和 Controller
- 测试过期，引用旧 User 实体
- 缺少 Agent 调用能力

## 目标

1. 合并为单体 Spring Boot 应用，去掉网关和 Nacos
2. 精简代码层次（砍掉 Repository 和 Converter 层）
3. 完整实现 RBAC（用户 + 角色 + 权限管理）
4. 新增 Agent 管理 + SSE 流式对话能力
5. 统一数据库 Schema，修复所有不一致
6. JWT 认证 + 微信开放平台登录 + 手机短信验证码登录 + 用户注册

## 项目结构

```
acgAgent/
├── pom.xml                        ← 单体 Spring Boot 应用
└── src/main/java/com/darkness/
    ├── Application.java
    ├── common/
    │   ├── result/Result.java
    │   ├── entity/BaseEntity.java
    │   ├── exception/BizException.java
    │   └── exception/GlobalExceptionHandler.java
    ├── user/
    │   ├── controller/UserController.java
    │   ├── controller/RoleController.java
    │   ├── controller/PermissionController.java
    │   ├── service/UserService.java
    │   ├── service/RoleService.java
    │   ├── service/PermissionService.java
    │   ├── mapper/UserMapper.java
    │   ├── mapper/RoleMapper.java
    │   ├── mapper/PermissionMapper.java
    │   ├── mapper/UserRoleMapper.java
    │   ├── mapper/RolePermissionMapper.java
    │   ├── entity/UserDO.java
    │   ├── entity/RoleDO.java
    │   ├── entity/PermissionDO.java
    │   ├── entity/UserRoleDO.java
    │   └── entity/RolePermissionDO.java
    ├── agent/
    │   ├── controller/AgentController.java
    │   ├── controller/ChatController.java
    │   ├── service/AgentService.java
    │   ├── service/ChatService.java
    │   ├── mapper/AgentMapper.java
    │   ├── mapper/ConversationMapper.java
    │   ├── mapper/MessageMapper.java
    │   ├── entity/AgentDO.java
    │   ├── entity/ConversationDO.java
    │   ├── entity/MessageDO.java
    │   └── client/AgentClient.java
    └── config/
        ├── MyBatisPlusConfig.java
        ├── WebConfig.java
        ├── SecurityConfig.java              ← Spring Security + JWT 过滤器
        └── WxConfig.java                    ← 微信开放平台配置
```

### 认证模块结构

```
auth/
├── controller/AuthController.java          ← 登录/注册/刷新 token
├── service/AuthService.java
├── service/SmsService.java                 ← 短信验证码发送/校验
├── service/WxAuthService.java              ← 微信 OAuth2 流程
├── filter/JwtAuthenticationFilter.java     ← JWT token 校验过滤器
├── util/JwtUtil.java                       ← JWT 生成/解析
├── util/PasswordUtil.java                  ← 密码加密/校验（BCrypt）
├── entity/SmsCodeDO.java                   ← 验证码记录
├── entity/WxUserDO.java                    ← 微信用户绑定
├── mapper/SmsCodeMapper.java
└── mapper/WxUserMapper.java
```

## 数据库设计

数据库名：`acg_agent`。所有有独立 id 的表都有 `deleted` 字段，关联表（user_role、role_permission）无 deleted。

### 认证相关（新增）

```sql
-- 微信用户绑定表：一个微信 openid 可能绑定一个 sys_user
CREATE TABLE wx_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid VARCHAR(128) NOT NULL UNIQUE,
    union_id VARCHAR(128),
    user_id BIGINT NOT NULL COMMENT '绑定的 sys_user.id',
    nickname VARCHAR(64),
    avatar_url VARCHAR(512),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 短信验证码记录表
CREATE TABLE sms_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    code VARCHAR(6) NOT NULL,
    used TINYINT NOT NULL DEFAULT 0,
    expired_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 用户相关

```sql
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) UNIQUE,
    password VARCHAR(256),
    nickname VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(20) UNIQUE,
    avatar VARCHAR(512),
    status TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(256),
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(128) NOT NULL,
    type TINYINT NOT NULL DEFAULT 1 COMMENT '1-menu 2-button',
    path VARCHAR(256),
    icon VARCHAR(64),
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);
```

### Agent 相关

```sql
CREATE TABLE agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    avatar VARCHAR(512),
    api_url VARCHAR(512) NOT NULL COMMENT 'Agent API endpoint',
    api_key VARCHAR(512) NOT NULL COMMENT 'API key for auth',
    model VARCHAR(128) COMMENT 'model name, e.g. gpt-4, claude-3-opus',
    status TINYINT NOT NULL DEFAULT 1,
    config_json TEXT COMMENT 'extra params: temperature, max_tokens etc.',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    title VARCHAR(256),
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL COMMENT 'user or assistant',
    content TEXT NOT NULL,
    tokens INT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

message 表不需要 deleted 和 updated_at，消息只增不改。

## API 设计

### 认证（公开接口，无需 token）

| Method | Path | Handler | Returns | 说明 |
|--------|------|---------|---------|------|
| POST | `/api/auth/register` | AuthController.register | `Result<UserVO>` | 用户名+密码注册 |
| POST | `/api/auth/login` | AuthController.login | `Result<TokenVO>` | 用户名+密码登录，返回 JWT |
| POST | `/api/auth/sms/send` | AuthController.sendSmsCode | `Result<Void>` | 发送短信验证码 |
| POST | `/api/auth/sms/login` | AuthController.smsLogin | `Result<TokenVO>` | 手机号+验证码登录（未注册自动注册） |
| POST | `/api/auth/wx/qrcode` | AuthController.wxQrcode | `Result<WxQrcodeVO>` | 获取微信扫码登录二维码 |
| POST | `/api/auth/wx/callback` | AuthController.wxCallback | `Result<TokenVO>` | 微信回调，换 JWT（未绑定自动注册） |
| POST | `/api/auth/refresh` | AuthController.refresh | `Result<TokenVO>` | 刷新 access_token |

### 用户管理

| Method | Path | Handler | Returns |
|--------|------|---------|---------|
| GET | `/api/users/{id}` | UserController.getUser | `Result<UserVO>` |
| GET | `/api/users` | UserController.listUsers | `Result<List<UserVO>>` |
| POST | `/api/users` | UserController.createUser | `Result<UserVO>` |
| PUT | `/api/users/{id}` | UserController.updateUser | `Result<UserVO>` |
| DELETE | `/api/users/{id}` | UserController.deleteUser | `Result<Void>` |

### 角色管理

| Method | Path | Handler | Returns |
|--------|------|---------|---------|
| GET | `/api/roles/{id}` | RoleController.getRole | `Result<RoleVO>` |
| GET | `/api/roles` | RoleController.listRoles | `Result<List<RoleVO>>` |
| POST | `/api/roles` | RoleController.createRole | `Result<RoleVO>` |
| PUT | `/api/roles/{id}` | RoleController.updateRole | `Result<RoleVO>` |
| DELETE | `/api/roles/{id}` | RoleController.deleteRole | `Result<Void>` |

### 权限管理

| Method | Path | Handler | Returns |
|--------|------|---------|---------|
| GET | `/api/permissions/{id}` | PermissionController.getPermission | `Result<PermissionVO>` |
| GET | `/api/permissions` | PermissionController.listPermissions | `Result<List<PermissionVO>>` |
| POST | `/api/permissions` | PermissionController.createPermission | `Result<PermissionVO>` |
| PUT | `/api/permissions/{id}` | PermissionController.updatePermission | `Result<PermissionVO>` |
| DELETE | `/api/permissions/{id}` | PermissionController.deletePermission | `Result<Void>` |

### 角色权限关联

| Method | Path | Handler | Returns |
|--------|------|---------|---------|
| POST | `/api/roles/{roleId}/permissions` | RoleController.assignPermissions | `Result<Void>` |
| GET | `/api/roles/{roleId}/permissions` | RoleController.getPermissions | `Result<List<PermissionVO>>` |

### 用户角色关联

| Method | Path | Handler | Returns |
|--------|------|---------|---------|
| POST | `/api/users/{userId}/roles` | UserController.assignRoles | `Result<Void>` |
| GET | `/api/users/{userId}/roles` | UserController.getRoles | `Result<List<RoleVO>>` |

### Agent 管理

| Method | Path | Handler | Returns |
|--------|------|---------|---------|
| GET | `/api/agents` | AgentController.listAgents | `Result<List<AgentVO>>` |
| GET | `/api/agents/{id}` | AgentController.getAgent | `Result<AgentVO>` |
| POST | `/api/agents` | AgentController.createAgent | `Result<AgentVO>` |
| PUT | `/api/agents/{id}` | AgentController.updateAgent | `Result<AgentVO>` |
| DELETE | `/api/agents/{id}` | AgentController.deleteAgent | `Result<Void>` |

### 对话（SSE 流式）

| Method | Path | Handler | Returns |
|--------|------|---------|---------|
| POST | `/api/chat/conversations` | ChatController.createConversation | `Result<ConversationVO>` |
| GET | `/api/chat/conversations` | ChatController.listConversations | `Result<List<ConversationVO>>` |
| GET | `/api/chat/conversations/{id}/messages` | ChatController.getMessages | `Result<List<MessageVO>>` |
| POST | `/api/chat/conversations/{id}/send` | ChatController.sendMessage | `SseEmitter` (text/event-stream) |
| DELETE | `/api/chat/conversations/{id}` | ChatController.deleteConversation | `Result<Void>` |

## Agent 调用流程

```
前端 → POST /api/chat/conversations/{id}/send (Accept: text/event-stream)
         │
         ▼
    ChatService.sendMessage(conversationId, content)
         │
         ├── 1. 保存 user message 到 message 表
         ├── 2. 查 conversation → 拿 agent_id → 查 AgentDO
         ├── 3. AgentClient.stream(agent, messages) → Flux<String>
         │      └── WebClient POST agent.api_url
         │          Header: Authorization: Bearer {api_key}
         │          Body: OpenAI chat completions 格式 (stream: true)
         │          逐 chunk 解析 SSE data → 提取 content delta
         ├── 4. 每个 chunk 通过 SseEmitter.event().data(content) 发给前端
         └── 5. 流结束后拼接完整回复，保存 assistant message 到 message 表
```

AgentClient 职责：
- 构建请求（api_url + Authorization header + OpenAI 兼容格式 body）
- 用 WebClient 接收流式响应
- 解析 SSE 事件，提取 content delta
- 通过 `Flux<String>` 返回给上层

未来换 Agent 提供商只需改 AgentClient，上层不变。

## 代码层次精简

### 砍掉 Repository 层
Service 直接注入 Mapper（MyBatis-Plus BaseMapper + ServiceImpl 已提供足够方法）。

### 砍掉 Converter 层
VO 用简单的静态工厂方法或 BeanUtils.copyProperties 做映射，不单独建 Converter 类。

### 砍掉旧的 User/UserMapper
统一使用 UserDO / sys_user 表，删除 User.java 和 UserMapper.java。

## 技术选型

| 组件 | 选择 | 说明 |
|------|------|------|
| Web 框架 | Spring Boot 3.3.5 + spring-boot-starter-web | Servlet 模式，SseEmitter |
| 安全框架 | Spring Security + JWT (jjwt) | 无状态认证，不使用 Session |
| ORM | MyBatis-Plus 3.5.9 | 保持不变 |
| 数据库 | MySQL 8 | 保持不变 |
| 连接池 | Druid (druid-spring-boot-3-starter) | 监控面板 + 慢 SQL 日志 |
| HTTP 客户端 | WebClient (Spring WebFlux) | 仅用于 AgentClient 的流式调用 |
| 密码加密 | BCrypt (Spring Security 内置) | |
| 短信服务 | 阿里云 SMS / 腾讯云 SMS | 通过 SmsService 接口抽象，可切换实现 |
| 微信登录 | 微信开放平台 OAuth2 | 扫码登录 + 小程序登录 |

## 认证流程

### JWT 双 Token 机制
- **Access Token**：有效期 2 小时，放在 `Authorization: Bearer xxx` header 中
- **Refresh Token**：有效期 7 天，用于刷新 access token
- JwtUtil 负责 token 生成、解析、验证过期
- JwtAuthenticationFilter 拦截所有 `/api/**` 请求（排除公开接口），校验 token 并设置 SecurityContext

### 用户名+密码注册/登录
1. 注册：POST `/api/auth/register`（username + password）→ 密码 BCrypt 加密后存库
2. 登录：POST `/api/auth/login`（username + password）→ 校验密码 → 返回 JWT

### 手机短信验证码登录
1. 发送：POST `/api/auth/sms/send`（phone）→ SmsService 调用短信服务商 → 存 sms_code 表
2. 登录：POST `/api/auth/sms/login`（phone + code）→ 校验验证码 → 若手机号未注册自动创建用户 → 返回 JWT

### 微信开放平台登录
1. 前端获取二维码：POST `/api/auth/wx/qrcode` → 返回二维码 URL + state
2. 用户扫码授权后微信回调：POST `/api/auth/wx/callback`（code + state）
3. 后端用 code 换 access_token + openid → 查 wx_user 表：
   - 已绑定：找到 user_id → 返回 JWT
   - 未绑定：自动创建 sys_user + wx_user 记录 → 返回 JWT

## 不在本次范围内

- 前端页面
- 文件上传
- Redis 缓存
- 部署方案
