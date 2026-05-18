# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 构建与运行

```bash
# 构建整个项目（跳过测试）
mvn clean package -DskipTests

# 构建单个模块
mvn clean package -pl acgagent-service-user -DskipTests

# 运行测试
mvn test

# 运行单个测试类
mvn test -pl acgagent-service-user -Dtest=UserControllerTest
```

项目没有 Maven Wrapper（`mvnw`），使用系统安装的 Maven。父 POM 跳过了 Spring Boot 插件（`<skip>true</skip>`），只有可运行的模块（gateway、service-user）通过 `<skip>false</skip>` 覆盖。

## 技术架构

Spring Boot 3.3.5 + Spring Cloud 2023.0.3 + Spring Cloud Alibaba 2023.0.3.4 微服务项目，Java 21，三个 Maven 模块：

### 模块依赖关系

```
acgagent-common（公共库，无启动类）
  ↑
acgagent-service-user（端口 8081，可运行）
  │  依赖：acgagent-common, spring-boot-starter-web, mybatis-plus, mysql-connector, nacos-discovery

acgagent-gateway（端口 8080，可运行）
  │  依赖：spring-cloud-starter-gateway, nacos-discovery, spring-cloud-loadbalancer
```

### 网关路由

网关监听 8080 端口，将 `/api/user/**` 请求通过 Nacos 服务发现路由到 `acgagent-service-user`（`lb://acgagent-service-user`）。CORS 允许所有来源。

### acgagent-common — 公共模块

可运行应用均使用 `@SpringBootApplication(scanBasePackages = "com.darkness")`，因此 common 模块的所有 Bean 会被自动扫描。

| 类 | 作用 |
|---|---|
| `Result<T>` | 统一 JSON 响应体 `{code, message, data}`。静态方法：`Result.success(data)`、`Result.error(code, msg)`。 |
| `BaseEntity` | MyBatis-Plus 实体基类。自增 `id`，自动填充 `createTime`/`updateTime`，逻辑删除字段 `deleted`（0/1）。 |
| `BizException` | 业务异常，携带 HTTP 风格的 `code` 状态码。 |
| `GlobalExceptionHandler` | `@RestControllerAdvice`，捕获 `BizException` 和 `Exception`，统一返回 `Result<Void>`。 |

### acgagent-service-user — 用户 CRUD 服务

分层架构：Controller → Service（接口 → 实现）→ Mapper。

- **Controller** `UserController`（`/api/user`）：标准 REST 接口 — GET `/{id}`、GET 列表、POST、PUT `/{id}`、DELETE `/{id}`。所有接口统一返回 `Result<T>`。
- **Service** `UserServiceImpl` 继承 MyBatis-Plus 的 `ServiceImpl<UserMapper, User>`。`getUserById` 在用户不存在时抛出 `BizException(404, ...)`。
- **Entity** `User` 继承 `BaseEntity`，映射到 `user` 表。字段：`username`、`password`、`email`。
- **Mapper** `UserMapper` 继承 `BaseMapper<User>` — 无自定义 SQL，直接继承 MyBatis-Plus 提供的 CRUD。
- **Config** `MyBatisPlusConfig` 提供 `MetaObjectHandler`，自动填充 `createTime`/`updateTime`/`deleted`。

### 外部基础设施

- **Nacos** 地址 `localhost:8848` — 服务注册与发现
- **MySQL** 地址 `localhost:3306` — 数据库 `acg_agent`，表 `user`（建表脚本位于 `acgagent-service-user/src/main/resources/db/schema.sql`）

### MyBatis-Plus 约定

- 逻辑删除：字段 `deleted`，0 = 未删除，1 = 已删除
- `map-underscore-to-camel-case: true` — 数据库字段 `create_time` 自动映射为 Java 属性 `createTime`
- SQL 日志已开启（`StdOutImpl`）
