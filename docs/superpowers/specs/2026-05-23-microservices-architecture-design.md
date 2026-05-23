# 微服务架构重构设计

> 日期：2026-05-23
> 状态：已确认，待实施
> 前置设计：`2026-05-20-architecture-simplification-design.md`（单体架构简化，已完成）

---

## 1. 重构目标

将当前 Spring Boot 单体应用拆分为微服务架构：

- `agent` 和 `user` 拆分为独立服务模块
- `auth` 合入 `user` 模块
- 引入 Gateway 网关统一鉴权与路由
- Dubbo RPC 实现服务间调用
- Nacos 服务发现与配置中心
- Docker Compose 容器化部署
- Sentinel 熔断降级 + 限流
- Micrometer Tracing + Zipkin 链路追踪

## 2. 关键决策

| 决策项 | 选择 | 理由 |
|---|---|---|
| 仓库策略 | Maven 多模块单仓库 | 小团队，代码集中管理，统一构建 |
| 模块划分 | 5 模块（common + api + gateway + user + agent） | Dubbo 接口契约层独立，服务间调用规范清晰 |
| 数据库 | 共享 `acg_agent` 数据库 | 当前规模不需要分库，降低复杂度 |
| 网关鉴权 | Gateway 本地 JWT 验签 | 简单高效，无额外 RPC 开销 |
| 限流方案 | Sentinel | 与 Nacos/Dubbo 同生态，熔断 + 限流统一组件 |

## 3. 技术版本

| 组件 | 版本 | 说明 |
|---|---|---|
| Java | 21 | 不变 |
| Spring Boot | 3.3.5 | 不变 |
| Spring Cloud | 2023.0.3 | 兼容 Spring Boot 3.3.x |
| Spring Cloud Alibaba | 2023.0.1.2 | 兼容 Spring Cloud 2023.0.x |
| Dubbo | 3.3.x | 支持 Spring Boot 3 + Java 21 |
| Nacos | 2.4.x | 服务发现 + 配置中心 |
| Sentinel | 1.8.x | 熔断降级 + 限流 |
| Micrometer Tracing | 最新稳定版 | 链路追踪（替代 Sleuth） |
| Zipkin | 最新稳定版 | 链路追踪 UI |

## 4. 模块结构

```
acgagent/
├── pom.xml                          — 父POM，packaging=pom，dependencyManagement
├── acg-common/                      — 公共层（不启动Spring上下文）
│   ├── result/                      — Result, ResultCode
│   ├── exception/                   — BizException, GlobalExceptionHandler
│   ├── enums/                       — CommonStatus, MessageRole, TokenType...
│   ├── constants/                   — AuthConstants, SseConstants...
│   ├── entity/                      — 所有DO（BaseEntity, UserDO, AgentDO...）
│   ├── mapper/                      — 所有Mapper接口（统一管理DAO层）
│   ├── model/                       — 所有VO、DTO、Request
│   └── util/                        — ServiceHelper
│
├── acg-api/                         — Dubbo 接口契约层（不启动Spring上下文）
│   ├── dto/                         — UserDTO, AgentDTO（跨服务传输对象）
│   └── facade/                      — UserFacade, AgentFacade（Dubbo接口定义）
│
├── acg-gateway/                     — Spring Cloud Gateway 网关服务 :8080
│   ├── filter/                      — JwtAuthFilter（本地验签）
│   ├── config/                      — 路由配置、CORS、Sentinel
│   └── handler/                     — 鉴权失败处理
│
├── acg-user/                        — 用户 + 认证服务 :8081
│   ├── config/                      — MyBatisPlusConfig, WxConfig
│   ├── auth/controller/             — AuthController
│   ├── auth/service/                — AuthService, SmsService, WxAuthService
│   ├── auth/util/                   — JwtUtil, PasswordUtil
│   ├── user/controller/             — UserController, RoleController, PermissionController
│   ├── user/service/                — UserService, RoleService, PermissionService
│   ├── dubbo/                       — UserFacadeImpl（@DubboService）
│   └── Application.java            — 启动类
│
├── acg-agent/                       — Agent + 对话服务 :8082
│   ├── config/                      — MyBatisPlusConfig, WebClientConfig
│   ├── controller/                  — AgentController, ChatController
│   ├── service/                     — AgentService, ChatService
│   ├── client/                      — AgentClient（WebClient SSE）
│   ├── dubbo/                       — AgentFacadeImpl（@DubboService，预留）
│   └── Application.java            — 启动类
│
└── docker/
    ├── docker-compose.yml
    ├── gateway/Dockerfile
    ├── user/Dockerfile
    └── agent/Dockerfile
```

### 依赖关系

```
acg-common ← acg-api ← acg-user（实现Facade）
                      ← acg-agent（引用Facade）
         ← acg-gateway（JWT工具类）
```

- `acg-common` 被所有模块依赖
- `acg-api` 被 `acg-user`、`acg-agent` 依赖
- `acg-gateway` 仅依赖 `acg-common`
- 各服务之间无直接依赖，通过 Dubbo RPC 通信

## 5. Dubbo RPC 接口设计

### 跨服务调用场景

```
agent 服务 → user 服务：校验用户是否存在、获取用户基本信息
user 服务 → agent 服务：预留，当前无调用需求
gateway → 下游服务：纯转发，不通过 Dubbo
```

### 接口定义

```java
// acg-api: UserFacade.java
public interface UserFacade {
    /** 校验用户是否存在，存在则返回基本信息 */
    UserDTO getUserById(Long userId);

    /** 批量查询用户信息 */
    List<UserDTO> listUsersByIds(List<Long> userIds);
}

// acg-api: AgentFacade.java
public interface AgentFacade {
    /** 查询 Agent 是否存在且可用 */
    boolean isAgentAvailable(Long agentId);
}
```

### DTO 设计

```java
// acg-api: UserDTO.java
public class UserDTO implements Serializable {
    private Long id;
    private String username;
    private String nickname;
    private Integer status;
}
```

- DTO 只包含跨服务调用必需的字段，不暴露内部细节
- 各服务内部仍使用自己的 DO/VO，DTO 仅在 Facade 实现层做转换
- agent 服务通过 `@DubboReference` 注入 `UserFacade`
- user 服务通过 `@DubboService` 暴露 `UserFacade` 实现

## 6. Gateway 网关设计

### 路由规则

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://acg-user
          predicates:
            - Path=/api/auth/**,/api/users/**,/api/roles/**,/api/permissions/**

        - id: agent-service
          uri: lb://acg-agent
          predicates:
            - Path=/api/agents/**,/api/chat/**

        - id: druid-monitor
          uri: lb://acg-user
          predicates:
            - Path=/druid/**
```

### 鉴权流程

```
请求进入 Gateway (:8080)
  │
  ├─ 匹配白名单路径 → 直接放行
  │
  ├─ 提取 Authorization Bearer Token
  │   └─ 无 Token → 返回 401
  │
  ├─ JwtUtil 本地验签（密钥从 Nacos 配置读取）
  │   └─ 签名无效/过期 → 返回 401
  │
  └─ 验证通过 → 写入 header 转发
      ├─ X-User-Id: {userId}
      └─ X-Username: {username}
```

### 白名单

- `/api/auth/**` — 登录、注册、短信、微信登录
- `GET /api/agents/**` — Agent 列表/详情公开访问
- `/druid/**` — 监控页面

### 下游服务获取用户身份

- 拆分后各服务不再自己做 JWT 解析，通过 `HttpServletRequest.getHeader("X-User-Id")` 获取
- 封装 `UserContext` 工具类统一提取，替换当前 `SecurityContextHolder` 用法

### SSE 流式响应

- Gateway 默认支持 SSE 转发（`/api/chat/**`）
- 配置路由过滤器保留 `Content-Type: text/event-stream`，不做缓冲

## 7. Nacos 配置中心与服务发现

### 配置结构

```
Nacos namespace: acg_agent
│
├── 共享配置
│   ├── common-mysql.yaml        — 数据库连接、Druid 连接池
│   ├── common-dubbo.yaml        — Dubbo 注册中心、协议、超时
│   └── common-jwt.yaml          — JWT 密钥、过期时间
│
├── acg-gateway.yaml             — 网关独立配置（路由规则、白名单）
├── acg-user.yaml                — 用户服务独立配置（SMS、微信）
└── acg-agent.yaml               — Agent 服务独立配置（外部 API 地址）
```

### 本地最小配置（bootstrap.yml）

```yaml
spring:
  application:
    name: acg-gateway
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:localhost:8848}
      config:
        namespace: acg_agent
        shared-configs:
          - common-jwt.yaml
        refresh-enabled: true
      discovery:
        namespace: acg_agent
```

### 敏感配置

- JWT 密钥、数据库密码、API Key 通过环境变量注入（`${JWT_SECRET:default}`）
- Docker 部署时通过 `docker-compose.yml` 的 `environment` 传入

### 服务端口规划

| 服务 | 端口 | 说明 |
|---|---|---|
| Nacos | 8848 | 注册中心 + 配置中心 |
| MySQL | 3306 | 共享数据库 |
| acg-gateway | 8080 | 唯一对外入口 |
| acg-user | 8081 | 用户 + 认证服务 |
| acg-agent | 8082 | Agent + 对话服务 |
| Dubbo RPC | 20881/20882 | 各服务 Dubbo 端口 |

## 8. 可观测性与容错

### 链路追踪：Micrometer Tracing + Zipkin

- 各服务引入 `micrometer-tracing-bridge-brave` + `zipkin-reporter`
- 采样率：生产 10%，开发 100%
- traceId 自动透传 HTTP header 和 Dubbo RpcContext
- Zipkin UI（端口 9411）查看完整调用链和耗时

### 熔断降级 + 限流：Sentinel

```
请求 → Gateway Sentinel Filter（网关限流）
         → Dubbo Provider Sentinel（服务端限流）
         → Dubbo Consumer Sentinel（调用端熔断降级）
```

- **网关层**：按路由限流（如 `/api/chat/**` QPS 上限 50）
- **Dubbo Provider**：`UserFacade` 接口限流（防止被突发调用压垮）
- **Dubbo Consumer**：agent 调用 user 服务时配置熔断（失败率超 50% 触发降级）
- Sentinel Dashboard（端口 8858）通过 Nacos 持久化规则，动态调整不重启

## 9. Docker 部署方案

### 容器清单

| 容器 | 端口 | 说明 |
|---|---|---|
| mysql | 3306 | 数据库 |
| nacos | 8848 | 注册中心 + 配置中心 |
| zipkin | 9411 | 链路追踪 UI |
| sentinel-dashboard | 8858 | 限流/熔断规则管理 |
| acg-gateway | 8080 | 网关 |
| acg-user | 8081 | 用户服务 |
| acg-agent | 8082 | Agent 服务 |

### Docker Compose 编排

```yaml
services:
  mysql:
    image: mysql:8.0
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: acg_agent
    volumes:
      - mysql-data:/var/lib/mysql
      - ../src/main/resources/db/schema.sql:/docker-entrypoint-initdb.d/init.sql

  nacos:
    image: nacos/nacos-server:v2.4.3
    ports: ["8848:8848"]
    environment:
      MODE: standalone
      SPRING_DATASOURCE_PLATFORM: mysql
      MYSQL_SERVICE_HOST: mysql
      MYSQL_SERVICE_DB_NAME: nacos_config
    depends_on: [mysql]

  zipkin:
    image: openzipkin/zipkin
    ports: ["9411:9411"]
    depends_on: [mysql]

  sentinel-dashboard:
    image: bladex/sentinel-dashboard:1.8.8
    ports: ["8858:8858"]

  acg-gateway:
    build: { context: .., dockerfile: docker/gateway/Dockerfile }
    ports: ["8080:8080"]
    environment:
      NACOS_ADDR: nacos:8848
      JWT_SECRET: ${JWT_SECRET}
    depends_on: [nacos, acg-user, acg-agent]

  acg-user:
    build: { context: .., dockerfile: docker/user/Dockerfile }
    environment:
      NACOS_ADDR: nacos:8848
      MYSQL_HOST: mysql
      JWT_SECRET: ${JWT_SECRET}
    depends_on: [nacos, mysql]

  acg-agent:
    build: { context: .., dockerfile: docker/agent/Dockerfile }
    environment:
      NACOS_ADDR: nacos:8848
      MYSQL_HOST: mysql
    depends_on: [nacos, mysql]

volumes:
  mysql-data:
```

### Dockerfile 模板

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl acg-agent -am -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/acg-agent/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

各服务只需替换 `-pl` 参数中的模块名。

### 启动顺序

```
MySQL → Nacos → acg-user, acg-agent（并行） → acg-gateway
```

### 开发环境

- 本地开发可用 IDE 直接启动各服务，Nacos 和 MySQL 走本地或 Docker
- `docker-compose up mysql nacos` 即可只启动基础设施

## 10. 迁移策略与代码变更清单

### 父 POM 改造

- 当前根 `pom.xml` 改为 `packaging=pom`，作为父工程
- 新增 `dependencyManagement` 统一管理 Spring Cloud、Spring Cloud Alibaba、Dubbo、Sentinel 版本
- 新增 5 个 `<module>` 声明

### acg-common（从现有代码提取）

- **移入**：`result/`、`exception/`、`enums/`、`constants/`、`util/ServiceHelper`
- **移入**：所有 DO 实体类（`BaseEntity`、`UserDO`、`AgentDO` 等）
- **移入**：所有 Mapper 接口（统一管理 DAO 层）
- **移入**：所有 VO、DTO、Request 类
- **移出**：`PasswordUtil` → 归属 `acg-user`（认证专属工具）
- **保留**：`JwtUtil` → 保留在 acg-common（Gateway 和 user 服务共享 JWT 验签逻辑）
- 保留 `GlobalExceptionHandler` 在 acg-common（各服务共用同一异常处理逻辑）

### acg-api（新建）

- 新建 `UserFacade`、`AgentFacade` 接口
- 新建 `UserDTO`、`AgentDTO` 跨服务传输对象

### acg-user（auth 合入 user）

- 移入 `auth/` 下 Controller、Service、Util（Entity、Mapper、Model 已在 common）
- 移入 `user/` 下 Controller、Service（Entity、Mapper、Model 已在 common）
- 移入 `config/MyBatisPlusConfig`、`config/WxConfig`
- **删除 Spring Security 过滤器链**（鉴权已由 Gateway 处理，不再需要 `JwtAuthenticationFilter`）
- **删除 `SecurityConfig`**（不再需要 Spring Security，权限校验由 Gateway + 自定义拦截器处理）
- 新增 `UserFacadeImpl`（`@DubboService`）
- Controller 中获取当前用户改为从 `X-User-Id` header 读取
- 新增 `UserContext` 工具类

### acg-agent

- 移入 `agent/` 下 Controller、Service、Client（Entity、Mapper、Model 已在 common）
- 移入 `config/MyBatisPlusConfig`（每个服务独立配置）
- `ChatService` 中查询用户逻辑改为调用 `UserFacade`
- `AgentClient`（WebClient）保持不变
- 新增 `AgentFacadeImpl`（预留）

### acg-gateway（新建）

- 新建 `JwtAuthFilter`（从现有 `JwtAuthenticationFilter` 简化改造）
- 新建路由配置、CORS 配置、鉴权失败处理
- 集成 Sentinel 网关限流

### 各服务独立配置

- 每个服务有自己的 `bootstrap.yml` + Nacos 远程配置
- 每个服务有独立的 Spring Boot 启动类
- MyBatis-Plus 的 `@MapperScan` 各服务扫各自的 Mapper 包

### 影响评估

| 类别 | 改动量 | 风险 |
|---|---|---|
| 父 POM + 模块搭建 | 新建文件 | 低 |
| acg-common 提取 | 移动文件 + 改包路径（含全部 Mapper/Entity/Model） | 中 |
| acg-api 新建 | 新建文件 | 低 |
| auth 合入 user | 移动文件 + 删 Security 过滤器 | 中 |
| agent 移出 | 移动文件 + 改用户查询为 RPC | 中 |
| acg-gateway 新建 | 新建文件 | 中 |
| Docker 配置 | 新建文件 | 低 |
| Nacos 配置 | 新建文件 | 低 |
| Sentinel 集成 | 新建文件 + 配置 | 中 |
| Zipkin 集成 | 引入依赖 + 配置 | 低 |

### 不做的事情（YAGNI）

- 不拆分数据库——共享数据库满足当前需求
- 不引入 API 版本管理（/api/v2）——当前不需要
- 不引入消息队列——当前无异步解耦场景
- 不做 Kubernetes 编排——Docker Compose 满足当前规模
