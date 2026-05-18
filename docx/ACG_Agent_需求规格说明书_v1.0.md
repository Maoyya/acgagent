# ACG Agent 个人网站 — 需求规格说明书

**版本**: 1.0  
**日期**: 2026-05-18  
**状态**: 初稿

---

## 修订记录

| 版本 | 日期 | 修订人 | 修订内容 |
|------|------|--------|----------|
| 1.0 | 2026-05-18 | - | 初稿创建 |

---

## 目录

1. [项目概述](#1-项目概述)
2. [功能需求](#2-功能需求)
3. [非功能需求](#3-非功能需求)
4. [数据库设计概要](#4-数据库设计概要)
5. [API 接口设计概要](#5-api-接口设计概要)
6. [流式通信设计](#6-流式通信设计)
7. [部署架构](#7-部署架构)
8. [项目风险与待定事项](#8-项目风险与待定事项)
9. [开发计划建议](#9-开发计划建议)
10. [附录](#10-附录)

---

## 1. 项目概述

### 1.1 项目背景

本项目旨在搭建一个面向个人使用的 AI 对话助手网站。系统采用前后端分离架构，前端使用 Vue 3 构建用户界面，后端基于 Spring Boot 微服务架构（在现有 acgAgent 项目基础上扩展），提供用户管理、对话管理、请求转发等功能。实际的 AI Agent 推理与模型调用由另一个独立的 Agent 系统负责，本系统作为中间层，负责用户侧的交互管理与接口转发。

### 1.2 项目定位

本需求文档聚焦于后端服务（acgAgent）的功能定义。前端（Vue 3）和 Agent 系统不在本文档范围内，但会描述其与本系统的接口关系。

### 1.3 整体架构

系统由三个独立组件构成：

- **前端 (Vue 3)**：用户交互界面，对话 UI、管理后台
- **后端 (acgAgent / Spring Boot)**：用户认证、对话管理、请求转发、数据持久化
- **Agent 系统 (独立服务)**：实际 AI 推理引擎，对接国产大模型，处理 LLM 调用

**通信链路**：`用户浏览器 ↔ Vue 3 前端 ↔ acgAgent 后端 ↔ Agent 系统 ↔ 国产大模型`

### 1.4 技术栈总览

| 层次 | 技术选型 | 说明 |
|------|----------|------|
| 前端 | Vue 3 + TypeScript + Vite | 独立项目，不在本文档范围 |
| 后端框架 | Spring Boot 3.3.5 (Java 21) | 基于现有 acgAgent 项目扩展 |
| 微服务治理 | Spring Cloud 2023.0.3 + Spring Cloud Alibaba 2023.0.3.4 | Nacos 服务注册与发现 + Gateway 网关 |
| 数据库 | MySQL 8.x | 持久化存储：用户、对话、会话等结构化数据 |
| 缓存 | Redis 7.x | 热点数据缓存：JWT 黑名单、会话列表、限流计数 |
| ORM | MyBatis-Plus 3.x | 现有技术栈，继承 BaseMapper 进行 CRUD |
| 认证 | Spring Security + JWT + OAuth2 | JWT Token + 微信 OAuth2 + 验证码登录 |
| 通信协议 | HTTP REST + SSE + WebSocket | REST 常规接口，SSE 对话流式输出，WebSocket 实时推送 |
| 容器编排 | Kubernetes | 生产环境部署与弹性伸缩 |
| 多语言 | Spring i18n (MessageSource) | 中英文双语 |

---

## 2. 功能需求

### 2.1 用户模块

#### 2.1.1 用户注册

| 注册方式 | 说明 | 必填字段 |
|----------|------|----------|
| 用户名 + 密码注册 | 传统注册方式 | 用户名、密码、邮箱（可选） |
| 手机验证码注册 | 短信/邮箱验证码，验证后完成注册 | 手机号/邮箱、验证码、密码 |
| 微信 OAuth2 注册 | 微信扫码授权登录，首次授权自动创建账号 | 微信授权的 openId、昵称、头像 |

#### 2.1.2 用户登录

| 登录方式 | 认证机制 | 说明 |
|----------|----------|------|
| 用户名 + 密码 | JWT Token | 登录成功后签发 Access Token + Refresh Token |
| 手机验证码 | JWT Token | 验证码验证通过后签发 Token |
| 微信扫码 | OAuth2 + JWT | 微信授权回调后签发 Token |

**JWT Token 设计**：
- Access Token：有效期 2 小时，携带用户 ID、角色、权限
- Refresh Token：有效期 7 天，用于无感刷新 Access Token
- Token 黑名单（Redis）：登出或 Token 吊销时加入黑名单

#### 2.1.3 用户信息管理

- 查看/编辑个人信息（昵称、头像、邮箱、手机号）
- 修改密码
- 账号绑定/解绑（微信、手机号、邮箱）
- 账号注销（软删除，30 天冷静期）

### 2.2 对话模块

#### 2.2.1 会话管理

| 功能 | 说明 |
|------|------|
| 创建会话 | 用户新建一个对话会话，可自定义标题 |
| 会话列表 | 显示用户的所有会话，支持搜索、分页，缓存热点会话至 Redis |
| 重命名会话 | 修改会话标题 |
| 删除会话 | 软删除（逻辑删除字段），可恢复 |
| 置顶/归档 | 支持会话置顶和归档操作 |
| 会话分页 | 按更新时间倒序，分页加载 |

#### 2.2.2 消息管理

| 功能 | 说明 |
|------|------|
| 发送消息 | 用户输入文本消息，提交到后端，后端转发至 Agent 系统 |
| 流式接收 | 后端通过 SSE 将 Agent 的逐字生成结果推送到前端 |
| 消息历史 | 加载当前会话的历史消息列表，支持分页（向上滚动加载更多） |
| 消息角色 | 支持 System / User / Assistant 三种角色消息 |
| 重新生成 | 用户可请求重新生成最后一条 Assistant 回复 |
| 消息反馈 | 点赞/点踩，记录用户对回复的评价 |

#### 2.2.3 多角色对话

- 系统预置多个 Agent 角色（如：通用助手、代码助手、翻译助手、写作助手等）
- 每个角色有独立的 System Prompt
- 用户创建会话时可选择角色，切换角色即切换对话上下文
- 管理员可在后台新增/编辑/删除角色

#### 2.2.4 文件上传与分析

- 支持文件类型：图片（png, jpg, gif）、文档（pdf, txt, docx, md）、代码文件
- 文件大小限制：图片 10MB，文档 20MB
- 上传流程：前端上传文件 → 后端存储（本地/OSS）→ 生成文件 URL → 随消息发送至 Agent 系统
- Agent 系统负责文件内容解析，本系统仅负责存储与转发

### 2.3 Agent 转发模块

#### 2.3.1 通信协议（待定）

本系统与 Agent 系统之间的通信协议尚未最终确定，候选方案如下：

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| HTTP REST | 实现简单，调试方便，无状态 | 每次请求需建立连接，流式支持需 SSE | 常规请求-响应模式 |
| gRPC | 高性能，支持双向流，强类型 | 调试不如 HTTP 直观，需 proto 定义 | 高吞吐、低延迟场景 |
| 消息队列 (MQ) | 解耦彻底，削峰填谷，异步处理 | 实时性较差，增加运维复杂度 | 异步任务、批量处理 |

> **推荐方案**：HTTP REST + SSE（与前后端通信协议一致，技术栈统一，后续可升级为 gRPC）。

#### 2.3.2 转发接口设计概要

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/chat/send` | POST | 发送消息到 Agent，返回 SSE 流式响应 |
| `/api/chat/regenerate` | POST | 重新生成上一条回复 |
| `/api/chat/roles` | GET | 获取可用角色列表（从 Agent 系统同步） |

### 2.4 管理后台

#### 2.4.1 用户管理

- 用户列表：搜索、分页、按状态筛选
- 用户详情：查看基本信息、登录记录、配额使用情况
- 启用/禁用用户
- 手动调整用户 Token 配额

#### 2.4.2 角色管理

- 角色列表：查看所有 Agent 角色
- 新增/编辑角色：配置角色名称、System Prompt、图标、排序
- 启用/禁用角色

#### 2.4.3 运营数据

- 核心指标：日活用户数、总对话数、消息数、Token 消耗
- 趋势图表：近 7/30 天的活跃趋势
- 用户排行：按对话数/Token 消耗排名

#### 2.4.4 系统配置

- 全局参数：每日 Token 配额上限、文件上传大小限制、验证码有效期
- 模型配置：对接的模型名称、API 地址、超时时间
- 公告管理：发布系统公告（前端展示）

---

## 3. 非功能需求

### 3.1 安全需求

| 安全措施 | 说明 | 实现方式 |
|----------|------|----------|
| API 限流 (Rate Limit) | 按用户/IP 限制 API 调用频率，防止恶意请求 | 令牌桶算法，Redis 计数器，每用户每分钟 60 次 |
| Token 配额 | 按用户日/月配额限制 Token 使用量 | 每次请求记录 Token 消耗，超过配额拒绝服务 |
| XSS 防护 | 对用户输入进行 HTML 转义，防止跨站脚本攻击 | Spring 全局过滤器 + 输出编码 |
| CSRF 防护 | 防止跨站请求伪造 | Spring Security CSRF Token + SameSite Cookie |
| SQL 注入防护 | 防止恶意 SQL 注入 | MyBatis-Plus 参数化查询（`#{}` 占位符） |
| 密码加密 | 用户密码不可逆加密存储 | BCrypt 加密 |
| JWT 安全 | Token 防泄漏与吊销 | 短有效期 Access Token + Refresh Token + Redis 黑名单 |
| HTTPS | 全站加密传输 | TLS 1.3 证书 |
| 文件上传安全 | 防止恶意文件上传 | 文件类型白名单 + 大小限制 |
| 日志脱敏 | 敏感信息不记录到日志 | 手机号、邮箱、密码等字段在日志中脱敏 |

### 3.2 性能需求

| 指标 | 目标值 | 说明 |
|------|--------|------|
| API 响应时间 (P99) | < 500ms | 非 Agent 调用的常规接口 |
| 并发用户数 | 支持 1000 并发 | 单实例支撑 |
| SSE 连接数 | 支持 500 并发流式连接 | 长连接复用 |
| 数据库查询时间 | < 100ms | 单条 SQL，利用索引和 Redis 缓存 |
| 系统可用性 | 99.9% | 年停机时间 < 8.76 小时 |

### 3.3 可扩展性

- 无状态服务设计：JWT 认证，服务实例可水平扩展
- 会话数据通过 Redis 共享，支持多实例部署
- Agent 系统解耦：通过抽象接口层隔离，方便切换不同的 Agent 后端
- Kubernetes 部署：支持 HPA 自动伸缩

### 3.4 多语言支持

- 支持中文（zh_CN）和英文（en_US）
- 后端：Spring i18n MessageSource，根据请求头 `Accept-Language` 自动切换
- 错误消息、验证提示、邮件通知均需双语
- 数据库存储的内容（如角色 System Prompt）按用户语言返回对应版本

### 3.5 可观测性

- 日志：统一日志格式（JSON），包含 TraceId 实现全链路追踪
- 指标：集成 Micrometer + Prometheus，暴露 JVM 和业务指标
- 健康检查：Spring Actuator `/health`、`/readiness`、`/liveness` 端点

---

## 4. 数据库设计概要

### 4.1 核心表结构

| 表名 | 说明 | 核心字段 |
|------|------|----------|
| `user` | 用户表 | id, username, password, email, phone, avatar, open_id, status, quota_total, quota_used, create_time, update_time, deleted |
| `user_session` | 登录会话表 | id, user_id, refresh_token, device_info, ip, expire_time, create_time |
| `conversation` | 对话会话表 | id, user_id, role_id, title, is_pinned, is_archived, message_count, create_time, update_time, deleted |
| `message` | 消息表 | id, conversation_id, role, content, token_count, feedback, create_time |
| `agent_role` | Agent 角色表 | id, name, name_en, system_prompt, system_prompt_en, icon, sort_order, is_enabled, create_time, update_time, deleted |
| `file_upload` | 文件上传记录表 | id, user_id, file_name, file_type, file_size, file_url, create_time, deleted |
| `user_quota_log` | 配额消耗日志表 | id, user_id, conversation_id, tokens_used, quota_date, create_time |
| `system_config` | 系统配置表 | id, config_key, config_value, description, create_time, update_time |
| `announcement` | 公告表 | id, title, title_en, content, content_en, is_published, publish_time, create_time, update_time, deleted |

### 4.2 MyBatis-Plus 约定

- 所有实体继承 `BaseEntity`（id 自增、createTime、updateTime、deleted 逻辑删除）
- 数据库字段使用下划线命名（snake_case），Java 实体使用驼峰命名（camelCase）
- 逻辑删除字段 `deleted`：0 = 未删除，1 = 已删除
- 创建时间/更新时间由 `MetaObjectHandler` 自动填充

---

## 5. API 接口设计概要

### 5.1 统一响应格式

所有接口统一返回 `Result<T>` 格式：

```json
{
  "code": 200,
  "message": "OK",
  "data": {}
}
```

- `code`：200 成功，4xx 客户端错误，5xx 服务端错误
- `message`：提示信息（中英文根据 `Accept-Language` 切换）
- `data`：业务数据

### 5.2 接口模块划分

| 模块 | 前缀 | 认证 | 说明 |
|------|------|------|------|
| 用户认证 | `POST /api/auth/*` | 否 | 注册、登录、刷新 Token、登出 |
| 用户信息 | `/api/user/*` | 是 | 个人信息 CRUD、密码修改、账号绑定 |
| 会话管理 | `/api/conversation/*` | 是 | 会话 CRUD、置顶、归档 |
| 消息 | `/api/message/*` | 是 | 发送消息(SSE)、历史消息、反馈 |
| 角色 | `/api/role/*` | 否(读) / 是(写) | 获取角色列表、角色详情 |
| 文件 | `/api/file/*` | 是 | 文件上传、文件列表 |
| 管理后台 | `/api/admin/*` | 是(ADMIN) | 用户管理、角色管理、运营数据、系统配置 |
| 公告 | `/api/announcement/*` | 否(读) / 是(写) | 获取公告列表 |

### 5.3 网关路由

Gateway (8080) 路由配置：

- `/api/user/**` → `lb://acgAgent-service-user`
- `/**` (新增模块路由) → 按路径前缀分发到对应服务模块
- CORS：允许所有来源（开发阶段），生产环境需限制域名

---

## 6. 流式通信设计

### 6.1 SSE (Server-Sent Events) — 对话流式输出

**用途**：Agent 生成回复时，后端通过 SSE 将生成内容逐字推送到前端，实现打字机效果。

**流程**：

1. 前端发起 `POST /api/message/send`，携带会话 ID 和消息内容
2. 后端保存用户消息到数据库
3. 后端转发请求到 Agent 系统，Agent 系统以流式方式返回
4. 后端建立 SSE 连接（`Content-Type: text/event-stream`），逐片转发至前端
5. 流结束时，后端保存完整的 Assistant 消息到数据库

**SSE 事件格式**：

| 事件 | 说明 |
|------|------|
| `event: message` | 携带生成的文本片段 |
| `event: error` | 发生错误时发送错误信息 |
| `event: done` | 标记流结束，附带完整消息 ID 和 Token 消耗统计 |

### 6.2 WebSocket — 实时通知

**用途**：实时推送系统通知、会话更新、Agent 状态等非对话类消息。

**使用场景**：

- 系统公告实时推送
- 会话标题自动生成完成通知
- Token 配额即将用尽提醒
- 管理后台数据实时刷新

**连接**：`ws://host/api/ws/notifications?token={jwt}`，连接时进行 JWT 鉴权。

---

## 7. 部署架构

### 7.1 Kubernetes 部署拓扑

所有服务容器化部署在 Kubernetes 集群中：

| 组件 | 副本建议 | 说明 |
|------|----------|------|
| acgAgent-gateway | 2+ | 通过 Ingress 对外暴露 |
| acgAgent-service-user | 2+ | 内部服务 |
| MySQL | StatefulSet | 持久化存储卷 |
| Redis | StatefulSet | 或使用外部托管 Redis |
| Nacos | 2 | 服务注册与配置管理 |

**关键配置**：

- **HPA**：基于 CPU 和内存使用率自动伸缩
- **Liveness Probe**：`/actuator/health/liveness`
- **Readiness Probe**：`/actuator/health/readiness`
- **滚动更新策略**：maxSurge=1, maxUnavailable=0

---

## 8. 项目风险与待定事项

### 8.1 待定事项

| 编号 | 事项 | 影响范围 | 优先级 | 建议 |
|------|------|----------|--------|------|
| TD-01 | 与 Agent 系统通信协议 | Agent 转发模块 | 高 | 建议先采用 HTTP REST + SSE，后续按需升级 gRPC |
| TD-02 | 国产大模型具体选型 | Agent 系统（不在本系统范围） | 中 | 待 Agent 系统确定后，本系统仅需确认接口格式 |
| TD-03 | 文件存储方案 | 文件上传模块 | 中 | 开发阶段本地存储，生产环境建议使用 OSS |
| TD-04 | 验证码服务提供商 | 用户认证模块 | 低 | 短信验证码需选择服务商（如阿里云短信、腾讯云短信） |

### 8.2 技术风险

| 风险 | 级别 | 缓解措施 |
|------|------|----------|
| SSE 长连接资源消耗 | 中 | 设置连接超时 + 并发连接数限制 + Tomcat 异步 Servlet |
| JWT Token 泄漏 | 高 | 短有效期 + Refresh Token 轮换策略 + HTTPS |
| Agent 系统不可用 | 高 | 熔断降级 + 重试机制 + 友好的错误提示 |
| 数据库性能瓶颈 | 中 | Redis 缓存热点数据 + 读写分离 + 索引优化 |
| Kubernetes 运维复杂度 | 中 | 编写完善的 Helm Chart + 监控告警体系 |

---

## 9. 开发计划建议

| 阶段 | 内容 | 预估工作量 |
|------|------|------------|
| Phase 1: 基础设施 | 数据库表设计与建表、Redis 配置、现有模块结构调整 | 2-3 天 |
| Phase 2: 用户认证 | Spring Security + JWT + OAuth2 集成、验证码登录、刷新 Token | 5-7 天 |
| Phase 3: 对话管理 | 会话 CRUD、消息管理、SSE 流式输出、WebSocket 实时通知 | 7-10 天 |
| Phase 4: Agent 转发 | Agent 系统对接、角色同步、文件上传与转发 | 3-5 天 |
| Phase 5: 管理后台 | 用户管理、角色管理、运营数据、系统配置 | 5-7 天 |
| Phase 6: 安全与优化 | API 限流、Token 配额、多语言、安全防护、性能优化 | 3-5 天 |
| Phase 7: 容器化部署 | Dockerfile、K8s 部署文件、CI/CD 流程 | 3-5 天 |
| Phase 8: 测试与文档 | 单元测试、集成测试、API 文档(Swagger)、部署文档 | 3-5 天 |

> 预估总工作量：约 **6-8 周**（1 人全职）。

---

## 10. 附录

### 10.1 术语表

| 术语 | 说明 |
|------|------|
| acgAgent | 本项目后端服务名称，基于 Spring Boot 微服务架构 |
| Agent 系统 | 独立的 AI 推理服务，负责实际的 LLM 调用与 Agent 逻辑 |
| SSE | Server-Sent Events，服务端向客户端推送事件的 HTTP 长连接技术 |
| JWT | JSON Web Token，用于无状态用户认证的令牌 |
| OAuth2 | 开放授权协议，用于第三方登录 |
| HPA | Horizontal Pod Autoscaler，Kubernetes 水平自动伸缩 |
| Token 配额 | 限制单个用户可消耗的 LLM Token 数量 |

### 10.2 需求决策记录

| 决策项 | 最终选择 |
|--------|----------|
| 网站用途 | AI 对话助手（功能性 Agent 网站） |
| 前端技术 | Vue 3（独立项目，不在本需求范围） |
| 后端技术 | Java Spring Boot（基于现有 acgAgent 项目） |
| 大模型 | 国产大模型 |
| 用户系统 | 完整注册/登录/认证 |
| 数据存储 | MySQL 持久化 + Redis 热点缓存 |
| Agent 通信 | 待定（建议 HTTP REST + SSE） |
| 对话功能 | 标准对话 + 多角色 + 文件上传 |
| 认证方式 | JWT + OAuth2（微信登录 + 验证码登录） |
| 流式输出 | SSE + WebSocket 混合 |
| 管理后台 | 需要 |
| 安全措施 | API 限流 + Token 配额 + XSS/CSRF/SQL 注入防护 |
| 多语言 | 中英文双语 |
| 部署方式 | Kubernetes |
