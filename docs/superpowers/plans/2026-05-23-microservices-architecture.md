# 微服务架构重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Spring Boot 单体应用拆分为 5 模块微服务架构（gateway + user + agent + api + common），引入 Dubbo RPC、Nacos 注册配置、Sentinel 熔断限流、Zipkin 链路追踪，Docker Compose 部署。

**Architecture:** Maven 多模块单仓库，Gateway 本地 JWT 验签 + 路由转发，Dubbo 3.3.x 做服务间 RPC，Nacos 做服务发现与配置中心，共享 acg_agent 数据库，acg-common 统一管理所有 DO/Mapper/Model。

**Tech Stack:** Spring Boot 3.3.5, Spring Cloud 2023.0.3, Spring Cloud Alibaba 2023.0.3.2, Dubbo 3.3.x, Nacos 2.4.x, Sentinel 1.8.x, Spring Cloud Gateway, Micrometer Tracing + Zipkin, Docker Compose

**Design Spec:** `docs/superpowers/specs/2026-05-23-microservices-architecture-design.md`

---

## File Structure

### 新建文件清单

```
acgagent/
├── pom.xml                                          — 父POM（改造现有）
├── acg-common/
│   ├── pom.xml
│   └── src/main/java/com/darkness/common/
│       ├── entity/BaseEntity.java                   — 保留
│       ├── entity/UserDO.java                       — 从 user.entity 移入
│       ├── entity/RoleDO.java                       — 从 user.entity 移入
│       ├── entity/PermissionDO.java                 — 从 user.entity 移入
│       ├── entity/UserRoleDO.java                   — 从 user.entity 移入
│       ├── entity/RolePermissionDO.java             — 从 user.entity 移入
│       ├── entity/SmsCodeDO.java                    — 从 auth.entity 移入
│       ├── entity/WxUserDO.java                     — 从 auth.entity 移入
│       ├── entity/AgentDO.java                      — 从 agent.entity 移入
│       ├── entity/ConversationDO.java               — 从 agent.entity 移入
│       ├── entity/MessageDO.java                    — 从 agent.entity 移入
│       ├── mapper/UserMapper.java                   — 从 user.mapper 移入
│       ├── mapper/RoleMapper.java                   — 从 user.mapper 移入
│       ├── mapper/PermissionMapper.java             — 从 user.mapper 移入
│       ├── mapper/UserRoleMapper.java               — 从 user.mapper 移入
│       ├── mapper/RolePermissionMapper.java         — 从 user.mapper 移入
│       ├── mapper/SmsCodeMapper.java                — 从 auth.mapper 移入
│       ├── mapper/WxUserMapper.java                 — 从 auth.mapper 移入
│       ├── mapper/AgentMapper.java                  — 从 agent.mapper 移入
│       ├── mapper/ConversationMapper.java           — 从 agent.mapper 移入
│       ├── mapper/MessageMapper.java                — 从 agent.mapper 移入
│       ├── model/UserVO.java                        — 从 user.model 移入
│       ├── model/RoleVO.java                        — 从 user.model 移入
│       ├── model/PermissionVO.java                  — 从 user.model 移入
│       ├── model/TokenVO.java                       — 从 auth.model 移入
│       ├── model/LoginRequest.java                  — 从 auth.model 移入
│       ├── model/RegisterRequest.java               — 从 auth.model 移入
│       ├── model/RefreshTokenRequest.java           — 从 auth.model 移入
│       ├── model/SmsLoginRequest.java               — 从 auth.model 移入
│       ├── model/SmsSendRequest.java                — 从 auth.model 移入
│       ├── model/WxLoginRequest.java                — 从 auth.model 移入
│       ├── model/AgentVO.java                       — 从 agent.model 移入
│       ├── model/ConversationVO.java                — 从 agent.model 移入
│       ├── model/MessageVO.java                     — 从 agent.model 移入
│       ├── model/CreateConversationRequest.java     — 从 agent.model 移入
│       ├── model/SendMessageRequest.java            — 从 agent.model 移入
│       ├── result/Result.java                       — 保留
│       ├── result/ResultCode.java                   — 保留
│       ├── exception/BizException.java              — 保留
│       ├── exception/GlobalExceptionHandler.java    — 保留
│       ├── enums/CommonStatus.java                  — 保留
│       ├── enums/MessageRole.java                   — 保留
│       ├── enums/PermissionType.java                — 保留
│       ├── enums/TokenType.java                     — 保留
│       ├── enums/UsedStatus.java                    — 保留
│       ├── constant/AuthConstants.java              — 从 constants 移入（重命名目录）
│       ├── constant/AgentConstants.java             — 从 agent.constant 移入
│       ├── constant/SseConstants.java               — 从 agent.constant 移入
│       ├── util/ServiceHelper.java                  — 保留
│       └── util/JwtUtil.java                        — 从 auth.util 移入（Gateway 共享）
├── acg-api/
│   ├── pom.xml
│   └── src/main/java/com/darkness/api/
│       ├── dto/UserDTO.java                         — 新建
│       ├── dto/AgentDTO.java                        — 新建
│       ├── facade/UserFacade.java                   — 新建
│       └── facade/AgentFacade.java                  — 新建
├── acg-gateway/
│   ├── pom.xml
│   └── src/main/java/com/darkness/gateway/
│       ├── GatewayApplication.java                  — 新建
│       ├── filter/JwtAuthFilter.java                — 新建
│       └── config/CorsConfig.java                   — 新建
├── acg-user/
│   ├── pom.xml
│   └── src/main/java/com/darkness/
│       ├── UserApplication.java                     — 新建
│       ├── config/MyBatisPlusConfig.java            — 从 config 移入
│       ├── config/WxConfig.java                     — 从 config 移入
│       ├── auth/controller/AuthController.java      — 从 auth.controller 移入
│       ├── auth/service/AuthService.java            — 从 auth.service 移入
│       ├── auth/service/SmsService.java             — 从 auth.service 移入
│       ├── auth/service/WxAuthService.java          — 从 auth.service 移入
│       ├── auth/service/impl/AuthServiceImpl.java   — 从 auth.service.impl 移入
│       ├── auth/service/impl/SmsServiceImpl.java    — 从 auth.service.impl 移入
│       ├── auth/service/impl/WxAuthServiceImpl.java — 从 auth.service.impl 移入
│       ├── auth/util/PasswordUtil.java              — 从 auth.util 移入
│       ├── user/controller/UserController.java      — 从 user.controller 移入
│       ├── user/controller/RoleController.java      — 从 user.controller 移入
│       ├── user/controller/PermissionController.java— 从 user.controller 移入
│       ├── user/service/UserService.java            — 从 user.service 移入
│       ├── user/service/RoleService.java            — 从 user.service 移入
│       ├── user/service/PermissionService.java      — 从 user.service 移入
│       ├── user/service/impl/UserServiceImpl.java   — 从 user.service.impl 移入
│       ├── user/service/impl/RoleServiceImpl.java   — 从 user.service.impl 移入
│       ├── user/service/impl/PermissionServiceImpl.java— 从 user.service.impl 移入
│       └── dubbo/UserFacadeImpl.java                — 新建
├── acg-agent/
│   ├── pom.xml
│   └── src/main/java/com/darkness/
│       ├── AgentApplication.java                    — 新建
│       ├── config/MyBatisPlusConfig.java            — 从 config 移入（副本）
│       ├── config/WebClientConfig.java              — 新建（WebClient bean）
│       ├── agent/controller/AgentController.java    — 从 agent.controller 移入
│       ├── agent/controller/ChatController.java     — 从 agent.controller 移入
│       ├── agent/service/AgentService.java          — 从 agent.service 移入
│       ├── agent/service/ChatService.java           — 从 agent.service 移入
│       ├── agent/service/impl/AgentServiceImpl.java — 从 agent.service.impl 移入
│       ├── agent/service/impl/ChatServiceImpl.java  — 从 agent.service.impl 移入
│       ├── agent/client/AgentClient.java            — 从 agent.client 移入
│       └── dubbo/AgentFacadeImpl.java               — 新建
└── docker/
    ├── docker-compose.yml                           — 新建
    ├── gateway/Dockerfile                           — 新建
    ├── user/Dockerfile                              — 新建
    └── agent/Dockerfile                             — 新建
```

### 删除文件清单

```
src/                                    — 整个旧单体源码目录（移完后删除）
pom.xml 中旧的 <dependencies>           — 改为父POM
com.darkness.Application.java           — 替换为各模块独立启动类
com.darkness.config.SecurityConfig.java — 不再需要 Spring Security
com.darkness.config.WebConfig.java      — CORS 改由 Gateway 处理
com.darkness.auth.filter.JwtAuthenticationFilter.java — 替换为 Gateway JWT Filter
com.darkness.auth.model.LoginUserDetails.java         — 不再需要 Spring Security
```

---

## Task 1: 父 POM + 模块目录结构

**Files:**
- Modify: `pom.xml`（改造为父POM）
- Create: `acg-common/pom.xml`
- Create: `acg-api/pom.xml`
- Create: `acg-gateway/pom.xml`
- Create: `acg-user/pom.xml`
- Create: `acg-agent/pom.xml`

### Step 1: 创建模块目录

- [ ] 创建模块目录结构

```bash
mkdir -p acg-common/src/main/java/com/darkness/common
mkdir -p acg-common/src/main/resources
mkdir -p acg-api/src/main/java/com/darkness/api
mkdir -p acg-gateway/src/main/java/com/darkness/gateway
mkdir -p acg-gateway/src/main/resources
mkdir -p acg-user/src/main/java/com/darkness
mkdir -p acg-user/src/main/resources
mkdir -p acg-agent/src/main/java/com/darkness
mkdir -p acg-agent/src/main/resources
mkdir -p docker/gateway docker/user docker/agent
```

### Step 2: 改造根 pom.xml 为父 POM

- [ ] 用以下内容替换根 `pom.xml`（注意保留 `<parent>` 继承 spring-boot-starter-parent）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.darkness</groupId>
    <artifactId>acgagent</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>acg-common</module>
        <module>acg-api</module>
        <module>acg-gateway</module>
        <module>acg-user</module>
        <module>acg-agent</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.3.2</spring-cloud-alibaba.version>
        <mybatis-plus.version>3.5.9</mybatis-plus.version>
        <druid.version>1.2.23</druid.version>
        <jjwt.version>0.12.6</jjwt.version>
        <dubbo.version>3.3.2</dubbo.version>
        <nacos.client.version>2.4.3</nacos.client.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Spring Cloud Alibaba BOM -->
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 项目内部模块 -->
            <dependency>
                <groupId>com.darkness</groupId>
                <artifactId>acg-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.darkness</groupId>
                <artifactId>acg-api</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- MyBatis-Plus -->
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>

            <!-- Druid -->
            <dependency>
                <groupId>com.alibaba</groupId>
                <artifactId>druid-spring-boot-3-starter</artifactId>
                <version>${druid.version}</version>
            </dependency>

            <!-- JJWT -->
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
                <scope>runtime</scope>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
                <scope>runtime</scope>
            </dependency>

            <!-- Dubbo -->
            <dependency>
                <groupId>org.apache.dubbo</groupId>
                <artifactId>dubbo-spring-boot-starter</artifactId>
                <version>${dubbo.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### Step 3: 创建 acg-common/pom.xml

- [ ] 创建公共模块 POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.darkness</groupId>
        <artifactId>acgagent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>acg-common</artifactId>
    <description>公共层：实体、Mapper、Model、枚举、常量、工具类</description>

    <dependencies>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

### Step 4: 创建 acg-api/pom.xml

- [ ] 创建 API 契约层 POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.darkness</groupId>
        <artifactId>acgagent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>acg-api</artifactId>
    <description>Dubbo 接口契约层：跨服务 DTO 和 Facade 接口定义</description>

    <dependencies>
        <dependency>
            <groupId>com.darkness</groupId>
            <artifactId>acg-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

### Step 5: 创建 acg-gateway/pom.xml

- [ ] 创建网关模块 POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.darkness</groupId>
        <artifactId>acgagent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>acg-gateway</artifactId>
    <description>API 网关：JWT 鉴权、路由转发、限流</description>

    <dependencies>
        <dependency>
            <groupId>com.darkness</groupId>
            <artifactId>acg-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>io.zipkin.reporter2</groupId>
            <artifactId>zipkin-reporter-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 6: 创建 acg-user/pom.xml

- [ ] 创建用户服务模块 POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.darkness</groupId>
        <artifactId>acgagent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>acg-user</artifactId>
    <description>用户 + 认证服务：注册、登录、RBAC、Dubbo UserFacade 实现</description>

    <dependencies>
        <dependency>
            <groupId>com.darkness</groupId>
            <artifactId>acg-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.darkness</groupId>
            <artifactId>acg-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>io.zipkin.reporter2</groupId>
            <artifactId>zipkin-reporter-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 7: 创建 acg-agent/pom.xml

- [ ] 创建 Agent 服务模块 POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.darkness</groupId>
        <artifactId>acgagent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>acg-agent</artifactId>
    <description>Agent + 对话服务：Agent CRUD、SSE 流式对话、Dubbo AgentFacade 实现</description>

    <dependencies>
        <dependency>
            <groupId>com.darkness</groupId>
            <artifactId>acg-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.darkness</groupId>
            <artifactId>acg-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>io.zipkin.reporter2</groupId>
            <artifactId>zipkin-reporter-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 8: 验证 POM 结构

- [ ] 运行编译验证

```bash
mvn clean compile -DskipTests
```

Expected: BUILD SUCCESS（所有模块编译通过，但此时各模块 src 为空，仅验证 POM 结构正确）

---

## Task 2: acg-common 模块——迁移共享代码

**Files:**
- Move: 所有实体、Mapper、Model、枚举、常量、工具类到 `acg-common/src/main/java/com/darkness/common/`

### Step 1: 迁移不变文件（只需改 package 声明和 import）

以下文件从旧 `src/main/java/com/darkness/` 移入 `acg-common/src/main/java/com/darkness/common/`，**仅需修改第一行 package 声明**：

- [ ] 迁移 result 层（package 不变）

| 旧路径 | 新路径 | package 变化 |
|---|---|---|
| `common/result/Result.java` | `acg-common/.../common/result/Result.java` | 无 |
| `common/result/ResultCode.java` | `acg-common/.../common/result/ResultCode.java` | 无 |

- [ ] 迁移 exception 层（package 不变）

| 旧路径 | 新路径 | package 变化 |
|---|---|---|
| `common/exception/BizException.java` | `acg-common/.../common/exception/BizException.java` | 无 |
| `common/exception/GlobalExceptionHandler.java` | `acg-common/.../common/exception/GlobalExceptionHandler.java` | 无 |

- [ ] 迁移 enums 层（package 不变）

| 旧路径 | 新路径 |
|---|---|
| `common/enums/CommonStatus.java` | `acg-common/.../common/enums/CommonStatus.java` |
| `common/enums/MessageRole.java` | `acg-common/.../common/enums/MessageRole.java` |
| `common/enums/PermissionType.java` | `acg-common/.../common/enums/PermissionType.java` |
| `common/enums/TokenType.java` | `acg-common/.../common/enums/TokenType.java` |
| `common/enums/UsedStatus.java` | `acg-common/.../common/enums/UsedStatus.java` |

- [ ] 迁移 util 层（package 不变）

| 旧路径 | 新路径 |
|---|---|
| `common/util/ServiceHelper.java` | `acg-common/.../common/util/ServiceHelper.java` |

- [ ] 迁移 entity（package 变化）

所有实体移入 `com.darkness.common.entity`，修改 package 声明：

| 旧 package | 新 package | 文件 |
|---|---|---|
| `com.darkness.common.entity` | `com.darkness.common.entity` | BaseEntity.java（不变） |
| `com.darkness.user.entity` | `com.darkness.common.entity` | UserDO, RoleDO, PermissionDO, UserRoleDO, RolePermissionDO |
| `com.darkness.auth.entity` | `com.darkness.common.entity` | SmsCodeDO, WxUserDO |
| `com.darkness.agent.entity` | `com.darkness.common.entity` | AgentDO, ConversationDO, MessageDO |

每个文件需要：
1. 修改 `package` 声明为 `com.darkness.common.entity`
2. Entity 中 import 的其他类（如 `BaseEntity`、`CommonStatus` 等）package 不变，无需改 import
3. `UserRoleDO` 和 `RolePermissionDO` 的 import 保持不变（它们只 import `Serializable` 和 MyBatis-Plus 注解）

- [ ] 迁移 mapper（package 变化）

所有 Mapper 移入 `com.darkness.common.mapper`，修改 package 和 import：

| 旧 package | 新 package |
|---|---|
| `com.darkness.user.mapper` | `com.darkness.common.mapper` |
| `com.darkness.auth.mapper` | `com.darkness.common.mapper` |
| `com.darkness.agent.mapper` | `com.darkness.common.mapper` |

每个 Mapper 需要：
1. 修改 `package` 声明
2. 修改 import 的实体类路径：`com.darkness.user.entity.UserDO` → `com.darkness.common.entity.UserDO`

- [ ] 迁移 model（package 变化）

所有 VO/Request 移入 `com.darkness.common.model`：

| 旧 package | 新 package | 文件 |
|---|---|---|
| `com.darkness.user.model` | `com.darkness.common.model` | UserVO, RoleVO, PermissionVO |
| `com.darkness.auth.model` | `com.darkness.common.model` | TokenVO, LoginRequest, RegisterRequest, RefreshTokenRequest, SmsLoginRequest, SmsSendRequest, WxLoginRequest |
| `com.darkness.agent.model` | `com.darkness.common.model` | AgentVO, ConversationVO, MessageVO, CreateConversationRequest, SendMessageRequest |

每个 Model 需要：
1. 修改 `package` 声明
2. 修改 import 的实体类路径（如 `com.darkness.user.entity.UserDO` → `com.darkness.common.entity.UserDO`）
3. 修改 import 的常量类路径（如 `com.darkness.agent.constant.AgentConstants` → `com.darkness.common.constant.AgentConstants`）

- [ ] 迁移 constants（package 重命名）

| 旧 package | 新 package | 文件 |
|---|---|---|
| `com.darkness.common.constants` | `com.darkness.common.constant` | AuthConstants.java |
| `com.darkness.agent.constant` | `com.darkness.common.constant` | AgentConstants.java, SseConstants.java |

注意：原 `com.darkness.common.constants`（带 s）统一为 `com.darkness.common.constant`（不带 s）。

- [ ] 迁移 JwtUtil（从 auth.util 到 common.util）

`auth/util/JwtUtil.java` → `acg-common/.../common/util/JwtUtil.java`

修改：
1. `package` 改为 `com.darkness.common.util`
2. import 中的 `com.darkness.common.enums.TokenType` 不变
3. `@Component` 注解保留（各服务的 component scan 会扫到）

### Step 2: 验证 acg-common 编译

- [ ] 编译验证

```bash
mvn clean compile -pl acg-common -DskipTests
```

Expected: BUILD SUCCESS

---

## Task 3: acg-api 模块——Dubbo 接口契约

**Files:**
- Create: `acg-api/src/main/java/com/darkness/api/dto/UserDTO.java`
- Create: `acg-api/src/main/java/com/darkness/api/dto/AgentDTO.java`
- Create: `acg-api/src/main/java/com/darkness/api/facade/UserFacade.java`
- Create: `acg-api/src/main/java/com/darkness/api/facade/AgentFacade.java`

### Step 1: 创建 UserDTO

- [ ] 创建跨服务用户传输对象

```java
package com.darkness.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 跨服务用户传输对象，用于 Dubbo RPC 调用时传递用户基本信息。
 */
@Data
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 状态：1-启用，0-禁用 */
    private Integer status;
}
```

### Step 2: 创建 AgentDTO

- [ ] 创建跨服务 Agent 传输对象

```java
package com.darkness.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 跨服务 Agent 传输对象，用于 Dubbo RPC 调用时传递 Agent 基本信息。
 */
@Data
public class AgentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Agent ID */
    private Long id;

    /** Agent 名称 */
    private String name;

    /** 状态：1-启用，0-禁用 */
    private Integer status;
}
```

### Step 3: 创建 UserFacade 接口

- [ ] 创建用户服务 Dubbo 接口

```java
package com.darkness.api.facade;

import com.darkness.api.dto.UserDTO;

import java.util.List;

/**
 * 用户服务 Dubbo Facade，供其他服务通过 RPC 查询用户信息。
 */
public interface UserFacade {

    /**
     * 根据用户 ID 查询用户基本信息。
     *
     * @param userId 用户 ID
     * @return 用户 DTO，用户不存在时返回 null
     */
    UserDTO getUserById(Long userId);

    /**
     * 批量查询用户信息。
     *
     * @param userIds 用户 ID 列表
     * @return 用户 DTO 列表
     */
    List<UserDTO> listUsersByIds(List<Long> userIds);
}
```

### Step 4: 创建 AgentFacade 接口

- [ ] 创建 Agent 服务 Dubbo 接口

```java
package com.darkness.api.facade;

/**
 * Agent 服务 Dubbo Facade，供其他服务通过 RPC 查询 Agent 信息。
 */
public interface AgentFacade {

    /**
     * 判断 Agent 是否存在且启用。
     *
     * @param agentId Agent ID
     * @return true 表示可用
     */
    boolean isAgentAvailable(Long agentId);
}
```

### Step 5: 验证 acg-api 编译

- [ ] 编译验证

```bash
mvn clean compile -pl acg-common,acg-api -DskipTests
```

Expected: BUILD SUCCESS

---

## Task 4: acg-user 模块——用户 + 认证服务

**Files:**
- Move: auth 和 user 层的 Controller、Service、Util、Config
- Create: `acg-user/src/main/java/com/darkness/UserApplication.java`
- Create: `acg-user/src/main/java/com/darkness/user/dubbo/UserFacadeImpl.java`
- Create: `acg-common/src/main/java/com/darkness/common/util/UserContext.java`（放在 common 模块，所有服务共享）

### Step 1: 创建启动类

- [ ] 创建 `acg-user/src/main/java/com/darkness/UserApplication.java`

```java
package com.darkness;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户 + 认证服务启动类。
 * 扫描 com.darkness 下的组件，包括 common 包中的 Mapper 和工具类。
 */
@SpringBootApplication(scanBasePackages = "com.darkness")
@MapperScan("com.darkness.common.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
```

### Step 2: 迁移 Config 类

- [ ] 移入 `config/MyBatisPlusConfig.java`（package 不变，直接复制到 `acg-user/src/main/java/com/darkness/config/`）
- [ ] 移入 `config/WxConfig.java`（同上）

### Step 3: 迁移 Auth 层（Controller + Service + Util）

移入 `acg-user/src/main/java/com/darkness/auth/`，保持 package 不变。

- [ ] 移入 `auth/controller/AuthController.java`
- [ ] 移入 `auth/service/AuthService.java`
- [ ] 移入 `auth/service/SmsService.java`
- [ ] 移入 `auth/service/WxAuthService.java`
- [ ] 移入 `auth/service/impl/AuthServiceImpl.java`
- [ ] 移入 `auth/service/impl/SmsServiceImpl.java`
- [ ] 移入 `auth/service/impl/WxAuthServiceImpl.java`
- [ ] 移入 `auth/util/PasswordUtil.java`

每个文件的 import 修改：
- `com.darkness.auth.model.*` → `com.darkness.common.model.*`（TokenVO、LoginRequest 等）
- `com.darkness.user.model.UserVO` → `com.darkness.common.model.UserVO`
- `com.darkness.user.entity.UserDO` → `com.darkness.common.entity.UserDO`
- `com.darkness.auth.entity.SmsCodeDO` → `com.darkness.common.entity.SmsCodeDO`
- `com.darkness.auth.entity.WxUserDO` → `com.darkness.common.entity.WxUserDO`
- `com.darkness.auth.mapper.*` → `com.darkness.common.mapper.*`
- `com.darkness.user.mapper.*` → `com.darkness.common.mapper.*`
- `com.darkness.auth.util.JwtUtil` → `com.darkness.common.util.JwtUtil`
- `com.darkness.common.constant.AuthConstants` → 注意 constants → constant 目录重命名

### Step 4: 迁移 User 层（Controller + Service）

移入 `acg-user/src/main/java/com/darkness/user/`，保持 package 不变。

- [ ] 移入 `user/controller/UserController.java`
- [ ] 移入 `user/controller/RoleController.java`
- [ ] 移入 `user/controller/PermissionController.java`
- [ ] 移入 `user/service/UserService.java`
- [ ] 移入 `user/service/RoleService.java`
- [ ] 移入 `user/service/PermissionService.java`
- [ ] 移入 `user/service/impl/UserServiceImpl.java`
- [ ] 移入 `user/service/impl/RoleServiceImpl.java`
- [ ] 移入 `user/service/impl/PermissionServiceImpl.java`

同样的 import 修改规则。

### Step 5: 移除 Spring Security 依赖

以下文件**不迁移**（删除）：

- [ ] 跳过 `config/SecurityConfig.java`
- [ ] 跳过 `auth/filter/JwtAuthenticationFilter.java`
- [ ] 跳过 `auth/model/LoginUserDetails.java`
- [ ] 跳过 `config/WebConfig.java`（CORS 改由 Gateway 处理）

### Step 6: 修改 Controller 获取当前用户的方式

所有 Controller 中获取当前用户 ID 的代码，从 SecurityContext 改为从请求 Header 读取。

- [ ] 创建 `acg-common/src/main/java/com/darkness/common/util/UserContext.java`

```java
package com.darkness.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户上下文工具类，从 Gateway 传递的请求 Header 中提取当前登录用户信息。
 * Gateway 验证 JWT 后将 userId 和 username 写入 X-User-Id 和 X-Username header。
 */
public final class UserContext {
    private UserContext() {}

    /** Gateway 传递的用户 ID header 名 */
    public static final String USER_ID_HEADER = "X-User-Id";
    /** Gateway 传递的用户名 header 名 */
    public static final String USERNAME_HEADER = "X-Username";

    /**
     * 获取当前请求的用户 ID。
     * 从 RequestContextHolder 中获取当前 HTTP 请求的 X-User-Id header。
     *
     * @return 用户 ID，header 不存在或格式错误时返回 null
     */
    public static Long getUserId() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return null;
        String userIdStr = request.getHeader(USER_ID_HEADER);
        if (userIdStr == null || userIdStr.isEmpty()) return null;
        try {
            return Long.valueOf(userIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取当前请求的用户名。
     *
     * @return 用户名，header 不存在时返回 null
     */
    public static String getUsername() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return null;
        return request.getHeader(USERNAME_HEADER);
    }

    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
```

- [ ] 在所有需要当前用户 ID 的 Controller/Service 中，将 SecurityContextHolder 方式替换为 UserContext.getUserId()

典型替换模式：
```java
// 旧代码
LoginUserDetails userDetails = (LoginUserDetails) SecurityContextHolder.getContext()
        .getAuthentication().getPrincipal();
Long userId = userDetails.getUserId();

// 新代码
Long userId = UserContext.getUserId();
```

涉及的文件（需逐一检查并修改）：
- `AuthServiceImpl.java` — login/register 方法可能不涉及（这些是登录前的操作）
- `UserController.java` — 获取当前用户信息、修改密码等
- `AuthServiceImpl.java` — refreshToken 方法中获取 userId

### Step 7: 创建 UserFacadeImpl（Dubbo 服务实现）

- [ ] 创建 `acg-user/src/main/java/com/darkness/user/dubbo/UserFacadeImpl.java`

```java
package com.darkness.user.dubbo;

import com.darkness.api.dto.UserDTO;
import com.darkness.api.facade.UserFacade;
import com.darkness.common.entity.UserDO;
import com.darkness.common.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务 Dubbo Facade 实现，供其他服务通过 RPC 查询用户信息。
 */
@DubboService
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {

    private final UserMapper userMapper;

    @Override
    public UserDTO getUserById(Long userId) {
        UserDO user = userMapper.selectById(userId);
        return toDTO(user);
    }

    @Override
    public List<UserDTO> listUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDO> users = userMapper.selectBatchIds(userIds);
        return users.stream().map(this::toDTO).collect(Collectors.toList());
    }

    private UserDTO toDTO(UserDO user) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setStatus(user.getStatus() != null ? user.getStatus().getValue() : null);
        return dto;
    }
}
```

### Step 8: 创建 bootstrap.yml

- [ ] 创建 `acg-user/src/main/resources/bootstrap.yml`

```yaml
server:
  port: 8081

spring:
  application:
    name: acg-user
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:localhost:8848}
      discovery:
        namespace: acg_agent
      config:
        namespace: acg_agent
        shared-configs:
          - data-id: common-mysql.yaml
            refresh: true
          - data-id: common-dubbo.yaml
            refresh: true
          - data-id: common-jwt.yaml
            refresh: true
        extension-configs:
          - data-id: acg-user.yaml
            refresh: true
```

### Step 9: 创建 application.yml（本地开发配置）

- [ ] 创建 `acg-user/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/acg_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_PASSWORD:root}
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
      web-stat-filter:
        enabled: true
        url-pattern: /*
        exclusions: "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*"
      filter:
        stat:
          enabled: true
          slow-sql-millis: 3000
          log-slow-sql: true

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

jwt:
  secret: ${JWT_SECRET:acgagent-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256}
  access-token-expiration: ${JWT_ACCESS_EXP:7200000}
  refresh-token-expiration: ${JWT_REFRESH_EXP:604800000}

wx:
  open:
    app-id: ${WX_APP_ID:}
    app-secret: ${WX_APP_SECRET:}

sms:
  provider: ${SMS_PROVIDER:mock}
  access-key-id: ${SMS_AK:}
  access-key-secret: ${SMS_SK:}
  sign-name: ${SMS_SIGN:}
  template-code: ${SMS_TEMPLATE:}

dubbo:
  protocol:
    name: tri
    port: 20881
  registry:
    address: nacos://${spring.cloud.nacos.server-addr}?namespace=acg_agent
  scan:
    base-packages: com.darkness.user.dubbo

management:
  tracing:
    sampling:
      probability: ${ZIPKIN_SAMPLING:1.0}
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

### Step 10: 复制 schema.sql 到 acg-user

- [ ] 复制 `src/main/resources/db/schema.sql` → `acg-user/src/main/resources/db/schema.sql`

### Step 11: 验证 acg-user 编译

- [ ] 编译验证

```bash
mvn clean compile -pl acg-common,acg-api,acg-user -DskipTests
```

Expected: BUILD SUCCESS

---

## Task 5: acg-agent 模块——Agent + 对话服务

**Files:**
- Move: agent 层的 Controller、Service、Client
- Create: `acg-agent/src/main/java/com/darkness/AgentApplication.java`
- Create: `acg-agent/src/main/java/com/darkness/agent/dubbo/AgentFacadeImpl.java`
- Create: `acg-agent/src/main/java/com/darkness/config/WebClientConfig.java`

### Step 1: 创建启动类

- [ ] 创建 `acg-agent/src/main/java/com/darkness/AgentApplication.java`

```java
package com.darkness;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent + 对话服务启动类。
 */
@SpringBootApplication(scanBasePackages = "com.darkness")
@MapperScan("com.darkness.common.mapper")
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
```

### Step 2: 迁移 Config 类

- [ ] 复制 `config/MyBatisPlusConfig.java` 到 `acg-agent/src/main/java/com/darkness/config/`（每个服务需要独立的配置）
- [ ] 创建 `acg-agent/src/main/java/com/darkness/config/WebClientConfig.java`

```java
package com.darkness.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 配置，提供 Agent 调用外部 LLM API 的 HTTP 客户端。
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}
```

### Step 3: 迁移 Agent 层（Controller + Service + Client）

移入 `acg-agent/src/main/java/com/darkness/agent/`，保持 package 不变。

- [ ] 移入 `agent/controller/AgentController.java`
- [ ] 移入 `agent/controller/ChatController.java`
- [ ] 移入 `agent/service/AgentService.java`
- [ ] 移入 `agent/service/ChatService.java`
- [ ] 移入 `agent/service/impl/AgentServiceImpl.java`
- [ ] 移入 `agent/service/impl/ChatServiceImpl.java`
- [ ] 移入 `agent/client/AgentClient.java`

每个文件的 import 修改：
- `com.darkness.agent.entity.*` → `com.darkness.common.entity.*`
- `com.darkness.agent.mapper.*` → `com.darkness.common.mapper.*`
- `com.darkness.agent.model.*` → `com.darkness.common.model.*`
- `com.darkness.agent.constant.*` → `com.darkness.common.constant.*`
- `com.darkness.common.constants.AuthConstants` → `com.darkness.common.constant.AuthConstants`
- SecurityContext → UserContext：检查 ChatController/ChatService 中获取当前用户 ID 的代码

### Step 4: 修改 ChatService 中获取用户 ID 的方式

- [ ] 在 `ChatServiceImpl.java` 和 `ChatController.java` 中：
  1. 移除 `SecurityContextHolder` 相关代码
  2. 使用 `UserContext.getUserId()` 替代
  3. 在 `ChatServiceImpl` 中，如果原来直接查询 UserMapper 验证用户是否存在，改为通过 `UserFacade` RPC 调用（或暂时保留直接查 Mapper，因为共享 DAO 层）

由于 agent 和 user 共享 DAO 层，ChatService 仍可直接通过 UserMapper 查询用户，**无需 RPC 调用**。但需改用 `UserContext.getUserId()` 获取当前用户。

### Step 5: 创建 AgentFacadeImpl（Dubbo 预留）

- [ ] 创建 `acg-agent/src/main/java/com/darkness/agent/dubbo/AgentFacadeImpl.java`

```java
package com.darkness.agent.dubbo;

import com.darkness.api.facade.AgentFacade;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Agent 服务 Dubbo Facade 实现，供其他服务通过 RPC 查询 Agent 信息。
 */
@DubboService
@RequiredArgsConstructor
public class AgentFacadeImpl implements AgentFacade {

    private final AgentMapper agentMapper;

    @Override
    public boolean isAgentAvailable(Long agentId) {
        if (agentId == null) return false;
        AgentDO agent = agentMapper.selectById(agentId);
        return agent != null && agent.getStatus() == CommonStatus.ENABLED;
    }
}
```

### Step 6: 创建配置文件

- [ ] 创建 `acg-agent/src/main/resources/bootstrap.yml`

```yaml
server:
  port: 8082

spring:
  application:
    name: acg-agent
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:localhost:8848}
      discovery:
        namespace: acg_agent
      config:
        namespace: acg_agent
        shared-configs:
          - data-id: common-mysql.yaml
            refresh: true
          - data-id: common-dubbo.yaml
            refresh: true
        extension-configs:
          - data-id: acg-agent.yaml
            refresh: true
```

- [ ] 创建 `acg-agent/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/acg_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_PASSWORD:root}
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

dubbo:
  protocol:
    name: tri
    port: 20882
  registry:
    address: nacos://${spring.cloud.nacos.server-addr}?namespace=acg_agent
  consumer:
    check: false
  scan:
    base-packages: com.darkness.agent.dubbo

management:
  tracing:
    sampling:
      probability: ${ZIPKIN_SAMPLING:1.0}
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

### Step 7: 验证 acg-agent 编译

- [ ] 编译验证

```bash
mvn clean compile -pl acg-common,acg-api,acg-agent -DskipTests
```

Expected: BUILD SUCCESS

---

## Task 6: acg-gateway 模块——API 网关

**Files:**
- Create: `acg-gateway/src/main/java/com/darkness/gateway/GatewayApplication.java`
- Create: `acg-gateway/src/main/java/com/darkness/gateway/filter/JwtAuthFilter.java`
- Create: `acg-gateway/src/main/java/com/darkness/gateway/config/CorsConfig.java`
- Create: `acg-gateway/src/main/java/com/darkness/gateway/handler/AuthExceptionHandler.java`

### Step 1: 创建启动类

- [ ] 创建 `acg-gateway/src/main/java/com/darkness/gateway/GatewayApplication.java`

```java
package com.darkness.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关启动类，负责 JWT 鉴权、路由转发和限流。
 * 注意：Gateway 基于 WebFlux，不扫描 Mapper 和 MVC 组件。
 */
@SpringBootApplication(scanBasePackages = "com.darkness.gateway")
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

### Step 2: 创建 JWT 鉴权过滤器

- [ ] 创建 `acg-gateway/src/main/java/com/darkness/gateway/filter/JwtAuthFilter.java`

```java
package com.darkness.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Gateway JWT 鉴权全局过滤器。
 * 从 Authorization header 提取 Bearer Token，本地验签后将 userId、username 写入下游请求 header。
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String secret;

    /** 白名单路径前缀——这些路径不需要鉴权 */
    private static final String[] WHITE_LIST = {
            "/api/auth/",
            "/druid/"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 白名单放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // GET /api/agents 和 GET /api/agents/{id} 放行
        if ("GET".equals(exchange.getRequest().getMethod().name())
                && (path.equals("/api/agents") || path.matches("^/api/agents/\\d+$"))) {
            return chain.filter(exchange);
        }

        // 提取 Token
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            // 将用户 ID 写入下游请求 header
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isWhitelisted(String path) {
        for (String prefix : WHITE_LIST) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
```

### Step 3: 创建 CORS 配置

- [ ] 创建 `acg-gateway/src/main/java/com/darkness/gateway/config/CorsConfig.java`

```java
package com.darkness.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Gateway CORS 跨域配置，替代原 WebConfig。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
```

### Step 4: 创建配置文件

- [ ] 创建 `acg-gateway/src/main/resources/bootstrap.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: acg-gateway
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:localhost:8848}
      discovery:
        namespace: acg_agent
      config:
        namespace: acg_agent
        shared-configs:
          - data-id: common-jwt.yaml
            refresh: true
        extension-configs:
          - data-id: acg-gateway.yaml
            refresh: true
```

- [ ] 创建 `acg-gateway/src/main/resources/application.yml`

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://acg-user
          predicates:
            - Path=/api/auth/**,/api/users/**,/api/roles/**,/api/permissions/**,/druid/**

        - id: agent-service
          uri: lb://acg-agent
          predicates:
            - Path=/api/agents/**,/api/chat/**

jwt:
  secret: ${JWT_SECRET:acgagent-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256}

management:
  tracing:
    sampling:
      probability: ${ZIPKIN_SAMPLING:1.0}
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

### Step 5: 验证 acg-gateway 编译

- [ ] 编译验证

```bash
mvn clean compile -pl acg-common,acg-gateway -DskipTests
```

Expected: BUILD SUCCESS

---

## Task 7: 全量编译验证

### Step 1: 删除旧 src 目录

- [ ] 确认所有文件已迁移后，删除旧单体代码

```bash
rm -rf src/
```

### Step 2: 全量编译

- [ ] 编译所有模块

```bash
mvn clean compile -DskipTests
```

Expected: BUILD SUCCESS

### Step 3: 全量打包

- [ ] 打包验证

```bash
mvn clean package -DskipTests
```

Expected: 5 个模块全部 BUILD SUCCESS，生成 3 个可执行 jar（gateway、user、agent）

---

## Task 8: Docker 部署

**Files:**
- Create: `docker/docker-compose.yml`
- Create: `docker/gateway/Dockerfile`
- Create: `docker/user/Dockerfile`
- Create: `docker/agent/Dockerfile`

### Step 1: 创建 Docker Compose 配置

- [ ] 创建 `docker/docker-compose.yml`

```yaml
services:
  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}
      MYSQL_DATABASE: acg_agent
    volumes:
      - mysql-data:/var/lib/mysql
      - ../acg-user/src/main/resources/db/schema.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      retries: 5

  nacos:
    image: nacos/nacos-server:v2.4.3
    ports:
      - "8848:8848"
    environment:
      MODE: standalone
    depends_on:
      mysql:
        condition: service_healthy

  zipkin:
    image: openzipkin/zipkin:latest
    ports:
      - "9411:9411"

  sentinel-dashboard:
    image: bladex/sentinel-dashboard:1.8.8
    ports:
      - "8858:8858"

  acg-user:
    build:
      context: ..
      dockerfile: docker/user/Dockerfile
    ports:
      - "8081:8081"
    environment:
      NACOS_ADDR: nacos:8848
      MYSQL_HOST: mysql
      MYSQL_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}
      JWT_SECRET: ${JWT_SECRET:-acgagent-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256}
    depends_on:
      - nacos
      - mysql

  acg-agent:
    build:
      context: ..
      dockerfile: docker/agent/Dockerfile
    ports:
      - "8082:8082"
    environment:
      NACOS_ADDR: nacos:8848
      MYSQL_HOST: mysql
      MYSQL_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}
    depends_on:
      - nacos
      - mysql

  acg-gateway:
    build:
      context: ..
      dockerfile: docker/gateway/Dockerfile
    ports:
      - "8080:8080"
    environment:
      NACOS_ADDR: nacos:8848
      JWT_SECRET: ${JWT_SECRET:-acgagent-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256}
    depends_on:
      - nacos
      - acg-user
      - acg-agent

volumes:
  mysql-data:
```

### Step 2: 创建 Dockerfile

- [ ] 创建 `docker/user/Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl acg-user -am -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/acg-user/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] 创建 `docker/agent/Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl acg-agent -am -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/acg-agent/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] 创建 `docker/gateway/Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl acg-gateway -am -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/acg-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Task 9: Sentinel + Zipkin 集成验证

### Step 1: 验证 Sentinel 集成

- [ ] 各服务 `application.yml` 已包含 Sentinel 依赖（通过 POM 引入 `spring-cloud-starter-alibaba-sentinel`）
- [ ] 启动 Sentinel Dashboard：`docker-compose up sentinel-dashboard`
- [ ] 访问 `http://localhost:8858` 确认 Dashboard 可用
- [ ] 启动任一服务，确认在 Sentinel Dashboard 中能看到服务注册

### Step 2: 验证 Zipkin 集成

- [ ] 各服务已引入 `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`（通过 POM）
- [ ] 启动 Zipkin：`docker-compose up zipkin`
- [ ] 访问 `http://localhost:9411` 确认 Zipkin UI 可用
- [ ] 发起一次 API 请求，在 Zipkin UI 中查看 trace

### Step 3: 配置 Sentinel 限流规则

- [ ] 在 Sentinel Dashboard 中为 `/api/chat/**` 路由配置 QPS 限流（如 QPS=50）

---

## Task 10: 端到端验证

### Step 1: 本地启动全部服务

- [ ] 启动顺序：MySQL → Nacos → acg-user → acg-agent → acg-gateway

```bash
# 方式1：Docker Compose
cd docker && docker-compose up -d

# 方式2：本地 IDE 启动（需要先启动 MySQL 和 Nacos）
# 先启动 UserApplication（:8081）
# 再启动 AgentApplication（:8082）
# 最后启动 GatewayApplication（:8080）
```

### Step 2: 验证认证流程

- [ ] 注册用户

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'
```

Expected: `{"code":200,"message":"success","data":{...}}`

- [ ] 登录获取 Token

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'
```

Expected: 返回 accessToken 和 refreshToken

- [ ] 使用 Token 访问受保护接口

```bash
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <accessToken>"
```

Expected: 返回用户信息

### Step 3: 验证 Agent 流程

- [ ] 创建 Agent

```bash
curl -X POST http://localhost:8080/api/agents \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Agent","apiUrl":"http://localhost:11434/v1/chat/completions","apiKey":"test-key","model":"llama3"}'
```

- [ ] 查看公开 Agent 列表（无需 Token）

```bash
curl http://localhost:8080/api/agents
```

Expected: 返回 Agent 列表

### Step 4: 清理并提交

- [ ] 确认所有功能正常后，提交代码

```bash
git add -A
# 由用户自行决定提交时机和消息（项目规约：AI 不自动 commit）
```
