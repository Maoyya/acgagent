# Architecture Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge Spring Cloud microservices into a single monolithic Spring Boot application with RBAC, JWT auth (WeChat/SMS/password), and Agent SSE streaming.

**Architecture:** Single Spring Boot 3.3.5 app with package-level module separation (`common/`, `user/`, `auth/`, `agent/`, `config/`). No gateway, no Nacos. Service layer talks directly to MyBatis-Plus Mapper (no Repository/Converter layers). Druid for connection pooling. Spring Security + JWT for auth.

**Tech Stack:** Spring Boot 3.3.5, Spring Security, MyBatis-Plus 3.5.9, MySQL 8, Druid 1.2.23, jjwt 0.12.6, WebClient (for Agent SSE), BCrypt

---

## Phase 1: Foundation (Tasks 1-5)

Produces a compilable, runnable single-module app with user CRUD.

### Task 1: Create single-module project structure and pom.xml

**Files:**
- Rewrite: `pom.xml`
- Create: `src/main/java/com/darkness/Application.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/db/schema.sql`

- [ ] **Step 1: Rewrite `pom.xml` as single-module Spring Boot app**

Replace the entire `pom.xml` (currently multi-module parent) with:

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

    <properties>
        <java.version>21</java.version>
        <mybatis-plus.version>3.5.9</mybatis-plus.version>
        <druid.version>1.2.23</druid.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencies>
        <!-- Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- WebFlux (WebClient for Agent SSE streaming) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
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

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- JWT -->
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

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Test -->
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
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `src/main/java/com/darkness/Application.java`**

```java
package com.darkness;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.darkness.**.mapper")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

- [ ] **Step 3: Create `src/main/resources/application.yml`**

```yaml
server:
  port: 8080

spring:
  application:
    name: acgagent
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/acg_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: root
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
  secret: acgagent-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256
  access-token-expiration: 7200000
  refresh-token-expiration: 604800000

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
```

- [ ] **Step 4: Create `src/main/resources/db/schema.sql`**

```sql
CREATE DATABASE IF NOT EXISTS acg_agent DEFAULT CHARACTER SET utf8mb4;
USE acg_agent;

-- User & RBAC
CREATE TABLE IF NOT EXISTS sys_user (
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

CREATE TABLE IF NOT EXISTS sys_role (
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

CREATE TABLE IF NOT EXISTS sys_permission (
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

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

-- Auth
CREATE TABLE IF NOT EXISTS wx_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid VARCHAR(128) NOT NULL UNIQUE,
    union_id VARCHAR(128),
    user_id BIGINT NOT NULL,
    nickname VARCHAR(64),
    avatar_url VARCHAR(512),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sms_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    code VARCHAR(6) NOT NULL,
    used TINYINT NOT NULL DEFAULT 0,
    expired_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Agent
CREATE TABLE IF NOT EXISTS agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    avatar VARCHAR(512),
    api_url VARCHAR(512) NOT NULL,
    api_key VARCHAR(512) NOT NULL,
    model VARCHAR(128),
    status TINYINT NOT NULL DEFAULT 1,
    config_json TEXT,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    title VARCHAR(256),
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    tokens INT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 5: Delete old module directories**

```bash
rm -rf acgagent-common acgagent-service-user acgagent-gateway
```

- [ ] **Step 6: Verify build**

```bash
mvn clean compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: restructure to single-module Spring Boot app with Druid"
```

---

### Task 2: Create common layer

**Files:**
- Create: `src/main/java/com/darkness/common/entity/BaseEntity.java`
- Create: `src/main/java/com/darkness/common/result/Result.java`
- Create: `src/main/java/com/darkness/common/exception/BizException.java`
- Create: `src/main/java/com/darkness/common/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create `BaseEntity.java`** (adds `deleted` field missing from original)

```java
package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

- [ ] **Step 2: Create `Result.java`** (unchanged from original)

```java
package com.darkness.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
```

- [ ] **Step 3: Create `BizException.java`** (unchanged from original)

```java
package com.darkness.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BizException extends RuntimeException {

    private Integer code;

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        super(message);
        this.code = 500;
    }
}
```

- [ ] **Step 4: Create `GlobalExceptionHandler.java`** (unchanged from original)

```java
package com.darkness.common.exception;

import com.darkness.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Internal server error", e);
        return Result.error(500, "Internal server error");
    }
}
```

- [ ] **Step 5: Verify build**

```bash
mvn clean compile -q
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add common layer (BaseEntity, Result, BizException, GlobalExceptionHandler)"
```

---

### Task 3: Create config classes

**Files:**
- Create: `src/main/java/com/darkness/config/MyBatisPlusConfig.java`
- Create: `src/main/java/com/darkness/config/WebConfig.java`

- [ ] **Step 1: Create `MyBatisPlusConfig.java`**

```java
package com.darkness.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
```

- [ ] **Step 2: Create `WebConfig.java`** (CORS for servlet, replaces gateway CorsConfig)

```java
package com.darkness.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

- [ ] **Step 3: Verify build**

```bash
mvn clean compile -q
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add MyBatisPlusConfig and WebConfig (CORS)"
```

---

### Task 4: Create user entity, mapper, VO, service, controller

**Files:**
- Create: `src/main/java/com/darkness/user/entity/UserDO.java`
- Create: `src/main/java/com/darkness/user/mapper/UserMapper.java`
- Create: `src/main/java/com/darkness/user/model/UserVO.java`
- Create: `src/main/java/com/darkness/user/service/UserService.java`
- Create: `src/main/java/com/darkness/user/service/impl/UserServiceImpl.java`
- Create: `src/main/java/com/darkness/user/controller/UserController.java`

- [ ] **Step 1: Create `UserDO.java`**

```java
package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class UserDO extends BaseEntity {

    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
}
```

- [ ] **Step 2: Create `UserMapper.java`** (single mapper for UserDO, replaces old UserMapper + UserDoMapper)

```java
package com.darkness.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.user.entity.UserDO;

public interface UserMapper extends BaseMapper<UserDO> {
}
```

- [ ] **Step 3: Create `UserVO.java`** (with static factory method, no Converter class needed)

```java
package com.darkness.user.model;

import com.darkness.user.entity.UserDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserVO from(UserDO entity) {
        if (entity == null) return null;
        UserVO vo = new UserVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setNickname(entity.getNickname());
        vo.setEmail(entity.getEmail());
        vo.setPhone(entity.getPhone());
        vo.setAvatar(entity.getAvatar());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public UserDO toEntity() {
        UserDO entity = new UserDO();
        entity.setId(this.id);
        entity.setUsername(this.username);
        entity.setNickname(this.nickname);
        entity.setEmail(this.email);
        entity.setPhone(this.phone);
        entity.setAvatar(this.avatar);
        entity.setStatus(this.status);
        return entity;
    }
}
```

- [ ] **Step 4: Create `UserService.java` interface**

```java
package com.darkness.user.service;

import com.darkness.user.model.UserVO;

import java.util.List;

public interface UserService {

    UserVO getUserById(Long id);

    List<UserVO> listUsers();

    UserVO createUser(UserVO vo);

    UserVO updateUser(Long id, UserVO vo);

    void deleteUser(Long id);

    void assignRoles(Long userId, List<Long> roleIds);

    List<Long> getRoleIds(Long userId);
}
```

- [ ] **Step 5: Create `UserServiceImpl.java`**

```java
package com.darkness.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.common.exception.BizException;
import com.darkness.user.entity.UserDO;
import com.darkness.user.entity.UserRoleDO;
import com.darkness.user.mapper.UserMapper;
import com.darkness.user.mapper.UserRoleMapper;
import com.darkness.user.model.UserVO;
import com.darkness.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public UserVO getUserById(Long id) {
        UserDO user = userMapper.selectById(id);
        if (user == null) throw new BizException(404, "User not found: " + id);
        return UserVO.from(user);
    }

    @Override
    public List<UserVO> listUsers() {
        return userMapper.selectList(null).stream().map(UserVO::from).toList();
    }

    @Override
    public UserVO createUser(UserVO vo) {
        UserDO entity = vo.toEntity();
        userMapper.insert(entity);
        return UserVO.from(entity);
    }

    @Override
    public UserVO updateUser(Long id, UserVO vo) {
        UserDO existing = userMapper.selectById(id);
        if (existing == null) throw new BizException(404, "User not found: " + id);
        UserDO entity = vo.toEntity();
        entity.setId(id);
        userMapper.updateById(entity);
        return UserVO.from(userMapper.selectById(id));
    }

    @Override
    public void deleteUser(Long id) {
        UserDO existing = userMapper.selectById(id);
        if (existing == null) throw new BizException(404, "User not found: " + id);
        userMapper.deleteById(id);
    }

    @Override
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        for (Long roleId : roleIds) {
            UserRoleDO ur = new UserRoleDO();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    @Override
    public List<Long> getRoleIds(Long userId) {
        return userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId)
        ).stream().map(UserRoleDO::getRoleId).toList();
    }
}
```

- [ ] **Step 6: Create remaining user entity and mapper files needed by UserServiceImpl**

Create `src/main/java/com/darkness/user/entity/UserRoleDO.java`:

```java
package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("sys_user_role")
public class UserRoleDO implements Serializable {

    private Long userId;
    private Long roleId;
}
```

Create `src/main/java/com/darkness/user/mapper/UserRoleMapper.java`:

```java
package com.darkness.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.user.entity.UserRoleDO;

public interface UserRoleMapper extends BaseMapper<UserRoleDO> {
}
```

- [ ] **Step 7: Create `UserController.java`** (path changed from `/api/user` to `/api/users`)

```java
package com.darkness.user.controller;

import com.darkness.common.result.Result;
import com.darkness.user.model.UserVO;
import com.darkness.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @GetMapping
    public Result<List<UserVO>> listUsers() {
        return Result.success(userService.listUsers());
    }

    @PostMapping
    public Result<UserVO> createUser(@RequestBody UserVO vo) {
        return Result.success(userService.createUser(vo));
    }

    @PutMapping("/{id}")
    public Result<UserVO> updateUser(@PathVariable Long id, @RequestBody UserVO vo) {
        return Result.success(userService.updateUser(id, vo));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @PostMapping("/{userId}/roles")
    public Result<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
        return Result.success();
    }

    @GetMapping("/{userId}/roles")
    public Result<List<Long>> getRoles(@PathVariable Long userId) {
        return Result.success(userService.getRoleIds(userId));
    }
}
```

- [ ] **Step 8: Verify build**

```bash
mvn clean compile -q
```

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: add user CRUD (entity, mapper, service, controller) with role assignment"
```

---

### Task 5: Create RBAC — Role and Permission CRUD

**Files:**
- Create: `src/main/java/com/darkness/user/entity/RoleDO.java`
- Create: `src/main/java/com/darkness/user/entity/PermissionDO.java`
- Create: `src/main/java/com/darkness/user/entity/RolePermissionDO.java`
- Create: `src/main/java/com/darkness/user/mapper/RoleMapper.java`
- Create: `src/main/java/com/darkness/user/mapper/PermissionMapper.java`
- Create: `src/main/java/com/darkness/user/mapper/RolePermissionMapper.java`
- Create: `src/main/java/com/darkness/user/model/RoleVO.java`
- Create: `src/main/java/com/darkness/user/model/PermissionVO.java`
- Create: `src/main/java/com/darkness/user/service/RoleService.java`
- Create: `src/main/java/com/darkness/user/service/impl/RoleServiceImpl.java`
- Create: `src/main/java/com/darkness/user/service/PermissionService.java`
- Create: `src/main/java/com/darkness/user/service/impl/PermissionServiceImpl.java`
- Create: `src/main/java/com/darkness/user/controller/RoleController.java`
- Create: `src/main/java/com/darkness/user/controller/PermissionController.java`

- [ ] **Step 1: Create entity files**

`src/main/java/com/darkness/user/entity/RoleDO.java`:

```java
package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class RoleDO extends BaseEntity {

    private String name;
    private String code;
    private Integer sort;
    private Integer status;
    private String remark;
}
```

`src/main/java/com/darkness/user/entity/PermissionDO.java`:

```java
package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class PermissionDO extends BaseEntity {

    private Long parentId;
    private String name;
    private String code;
    private Integer type;
    private String path;
    private String icon;
    private Integer sort;
    private Integer status;
}
```

`src/main/java/com/darkness/user/entity/RolePermissionDO.java`:

```java
package com.darkness.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("sys_role_permission")
public class RolePermissionDO implements Serializable {

    private Long roleId;
    private Long permissionId;
}
```

- [ ] **Step 2: Create mapper files**

`src/main/java/com/darkness/user/mapper/RoleMapper.java`:

```java
package com.darkness.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.user.entity.RoleDO;

public interface RoleMapper extends BaseMapper<RoleDO> {
}
```

`src/main/java/com/darkness/user/mapper/PermissionMapper.java`:

```java
package com.darkness.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.user.entity.PermissionDO;

public interface PermissionMapper extends BaseMapper<PermissionDO> {
}
```

`src/main/java/com/darkness/user/mapper/RolePermissionMapper.java`:

```java
package com.darkness.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.user.entity.RolePermissionDO;

public interface RolePermissionMapper extends BaseMapper<RolePermissionDO> {
}
```

- [ ] **Step 3: Create VO files**

`src/main/java/com/darkness/user/model/RoleVO.java`:

```java
package com.darkness.user.model;

import com.darkness.user.entity.RoleDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleVO {

    private Long id;
    private String name;
    private String code;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RoleVO from(RoleDO entity) {
        if (entity == null) return null;
        RoleVO vo = new RoleVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setCode(entity.getCode());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public RoleDO toEntity() {
        RoleDO entity = new RoleDO();
        entity.setId(this.id);
        entity.setName(this.name);
        entity.setCode(this.code);
        entity.setSort(this.sort);
        entity.setStatus(this.status);
        entity.setRemark(this.remark);
        return entity;
    }
}
```

`src/main/java/com/darkness/user/model/PermissionVO.java`:

```java
package com.darkness.user.model;

import com.darkness.user.entity.PermissionDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PermissionVO {

    private Long id;
    private Long parentId;
    private String name;
    private String code;
    private Integer type;
    private String path;
    private String icon;
    private Integer sort;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PermissionVO from(PermissionDO entity) {
        if (entity == null) return null;
        PermissionVO vo = new PermissionVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        vo.setName(entity.getName());
        vo.setCode(entity.getCode());
        vo.setType(entity.getType());
        vo.setPath(entity.getPath());
        vo.setIcon(entity.getIcon());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public PermissionDO toEntity() {
        PermissionDO entity = new PermissionDO();
        entity.setId(this.id);
        entity.setParentId(this.parentId);
        entity.setName(this.name);
        entity.setCode(this.code);
        entity.setType(this.type);
        entity.setPath(this.path);
        entity.setIcon(this.icon);
        entity.setSort(this.sort);
        entity.setStatus(this.status);
        return entity;
    }
}
```

- [ ] **Step 4: Create RoleService and RoleServiceImpl**

`src/main/java/com/darkness/user/service/RoleService.java`:

```java
package com.darkness.user.service;

import com.darkness.user.model.PermissionVO;
import com.darkness.user.model.RoleVO;

import java.util.List;

public interface RoleService {

    RoleVO getRoleById(Long id);

    List<RoleVO> listRoles();

    RoleVO createRole(RoleVO vo);

    RoleVO updateRole(Long id, RoleVO vo);

    void deleteRole(Long id);

    void assignPermissions(Long roleId, List<Long> permissionIds);

    List<PermissionVO> getPermissions(Long roleId);
}
```

`src/main/java/com/darkness/user/service/impl/RoleServiceImpl.java`:

```java
package com.darkness.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.common.exception.BizException;
import com.darkness.user.entity.PermissionDO;
import com.darkness.user.entity.RoleDO;
import com.darkness.user.entity.RolePermissionDO;
import com.darkness.user.mapper.PermissionMapper;
import com.darkness.user.mapper.RoleMapper;
import com.darkness.user.mapper.RolePermissionMapper;
import com.darkness.user.model.PermissionVO;
import com.darkness.user.model.RoleVO;
import com.darkness.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public RoleVO getRoleById(Long id) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) throw new BizException(404, "Role not found: " + id);
        return RoleVO.from(role);
    }

    @Override
    public List<RoleVO> listRoles() {
        return roleMapper.selectList(null).stream().map(RoleVO::from).toList();
    }

    @Override
    public RoleVO createRole(RoleVO vo) {
        RoleDO entity = vo.toEntity();
        roleMapper.insert(entity);
        return RoleVO.from(entity);
    }

    @Override
    public RoleVO updateRole(Long id, RoleVO vo) {
        RoleDO existing = roleMapper.selectById(id);
        if (existing == null) throw new BizException(404, "Role not found: " + id);
        RoleDO entity = vo.toEntity();
        entity.setId(id);
        roleMapper.updateById(entity);
        return RoleVO.from(roleMapper.selectById(id));
    }

    @Override
    public void deleteRole(Long id) {
        RoleDO existing = roleMapper.selectById(id);
        if (existing == null) throw new BizException(404, "Role not found: " + id);
        roleMapper.deleteById(id);
    }

    @Override
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId));
        for (Long permId : permissionIds) {
            RolePermissionDO rp = new RolePermissionDO();
            rp.setRoleId(roleId);
            rp.setPermissionId(permId);
            rolePermissionMapper.insert(rp);
        }
    }

    @Override
    public List<PermissionVO> getPermissions(Long roleId) {
        List<Long> permIds = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId)
        ).stream().map(RolePermissionDO::getPermissionId).toList();

        if (permIds.isEmpty()) return List.of();
        return permissionMapper.selectBatchIds(permIds).stream().map(PermissionVO::from).toList();
    }
}
```

- [ ] **Step 5: Create PermissionService and PermissionServiceImpl**

`src/main/java/com/darkness/user/service/PermissionService.java`:

```java
package com.darkness.user.service;

import com.darkness.user.model.PermissionVO;

import java.util.List;

public interface PermissionService {

    PermissionVO getPermissionById(Long id);

    List<PermissionVO> listPermissions();

    PermissionVO createPermission(PermissionVO vo);

    PermissionVO updatePermission(Long id, PermissionVO vo);

    void deletePermission(Long id);
}
```

`src/main/java/com/darkness/user/service/impl/PermissionServiceImpl.java`:

```java
package com.darkness.user.service.impl;

import com.darkness.common.exception.BizException;
import com.darkness.user.entity.PermissionDO;
import com.darkness.user.mapper.PermissionMapper;
import com.darkness.user.model.PermissionVO;
import com.darkness.user.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;

    @Override
    public PermissionVO getPermissionById(Long id) {
        PermissionDO perm = permissionMapper.selectById(id);
        if (perm == null) throw new BizException(404, "Permission not found: " + id);
        return PermissionVO.from(perm);
    }

    @Override
    public List<PermissionVO> listPermissions() {
        return permissionMapper.selectList(null).stream().map(PermissionVO::from).toList();
    }

    @Override
    public PermissionVO createPermission(PermissionVO vo) {
        PermissionDO entity = vo.toEntity();
        permissionMapper.insert(entity);
        return PermissionVO.from(entity);
    }

    @Override
    public PermissionVO updatePermission(Long id, PermissionVO vo) {
        PermissionDO existing = permissionMapper.selectById(id);
        if (existing == null) throw new BizException(404, "Permission not found: " + id);
        PermissionDO entity = vo.toEntity();
        entity.setId(id);
        permissionMapper.updateById(entity);
        return PermissionVO.from(permissionMapper.selectById(id));
    }

    @Override
    public void deletePermission(Long id) {
        PermissionDO existing = permissionMapper.selectById(id);
        if (existing == null) throw new BizException(404, "Permission not found: " + id);
        permissionMapper.deleteById(id);
    }
}
```

- [ ] **Step 6: Create controllers**

`src/main/java/com/darkness/user/controller/RoleController.java`:

```java
package com.darkness.user.controller;

import com.darkness.common.result.Result;
import com.darkness.user.model.PermissionVO;
import com.darkness.user.model.RoleVO;
import com.darkness.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/{id}")
    public Result<RoleVO> getRole(@PathVariable Long id) {
        return Result.success(roleService.getRoleById(id));
    }

    @GetMapping
    public Result<List<RoleVO>> listRoles() {
        return Result.success(roleService.listRoles());
    }

    @PostMapping
    public Result<RoleVO> createRole(@RequestBody RoleVO vo) {
        return Result.success(roleService.createRole(vo));
    }

    @PutMapping("/{id}")
    public Result<RoleVO> updateRole(@PathVariable Long id, @RequestBody RoleVO vo) {
        return Result.success(roleService.updateRole(id, vo));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    @PostMapping("/{roleId}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long roleId, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(roleId, permissionIds);
        return Result.success();
    }

    @GetMapping("/{roleId}/permissions")
    public Result<List<PermissionVO>> getPermissions(@PathVariable Long roleId) {
        return Result.success(roleService.getPermissions(roleId));
    }
}
```

`src/main/java/com/darkness/user/controller/PermissionController.java`:

```java
package com.darkness.user.controller;

import com.darkness.common.result.Result;
import com.darkness.user.model.PermissionVO;
import com.darkness.user.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping("/{id}")
    public Result<PermissionVO> getPermission(@PathVariable Long id) {
        return Result.success(permissionService.getPermissionById(id));
    }

    @GetMapping
    public Result<List<PermissionVO>> listPermissions() {
        return Result.success(permissionService.listPermissions());
    }

    @PostMapping
    public Result<PermissionVO> createPermission(@RequestBody PermissionVO vo) {
        return Result.success(permissionService.createPermission(vo));
    }

    @PutMapping("/{id}")
    public Result<PermissionVO> updatePermission(@PathVariable Long id, @RequestBody PermissionVO vo) {
        return Result.success(permissionService.updatePermission(id, vo));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return Result.success();
    }
}
```

- [ ] **Step 7: Verify build**

```bash
mvn clean compile -q
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add RBAC (Role, Permission CRUD + role-permission assignment)"
```

---

## Phase 2: Auth (Tasks 6-9)

Adds JWT authentication with password, SMS, and WeChat login.

### Task 6: JWT utilities and Spring Security config

**Files:**
- Create: `src/main/java/com/darkness/auth/util/JwtUtil.java`
- Create: `src/main/java/com/darkness/auth/util/PasswordUtil.java`
- Create: `src/main/java/com/darkness/auth/filter/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/darkness/auth/model/TokenVO.java`
- Create: `src/main/java/com/darkness/auth/model/LoginUserDetails.java`
- Create: `src/main/java/com/darkness/config/SecurityConfig.java`

- [ ] **Step 1: Create `JwtUtil.java`**

```java
package com.darkness.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseToken(token).get("type", String.class));
    }
}
```

- [ ] **Step 2: Create `PasswordUtil.java`**

```java
package com.darkness.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordUtil {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
```

- [ ] **Step 3: Create `LoginUserDetails.java`**

```java
package com.darkness.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
@AllArgsConstructor
public class LoginUserDetails implements UserDetails {

    private Long userId;
    private String username;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities != null ? authorities : List.of();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
```

- [ ] **Step 4: Create `TokenVO.java`**

```java
package com.darkness.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
}
```

- [ ] **Step 5: Create `JwtAuthenticationFilter.java`**

```java
package com.darkness.auth.filter;

import com.darkness.auth.model.LoginUserDetails;
import com.darkness.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isTokenValid(token)) {
                Long userId = jwtUtil.getUserId(token);
                LoginUserDetails userDetails = new LoginUserDetails(userId, null, null, null);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 6: Create `SecurityConfig.java`**

```java
package com.darkness.config;

import com.darkness.auth.filter.JwtAuthenticationFilter;
import com.darkness.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/druid/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/agents").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/agents/*").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Result.error(401, "Unauthorized")));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

Note: This file needs `import jakarta.servlet.http.HttpServletResponse;` — add it to the imports.

- [ ] **Step 7: Verify build**

```bash
mvn clean compile -q
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add JWT utilities, PasswordUtil, SecurityConfig, JwtAuthenticationFilter"
```

---

### Task 7: Auth — Register, Login, Refresh

**Files:**
- Create: `src/main/java/com/darkness/auth/service/AuthService.java`
- Create: `src/main/java/com/darkness/auth/controller/AuthController.java`

- [ ] **Step 1: Create `AuthService.java`**

```java
package com.darkness.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.auth.model.TokenVO;
import com.darkness.auth.util.JwtUtil;
import com.darkness.auth.util.PasswordUtil;
import com.darkness.common.exception.BizException;
import com.darkness.user.entity.UserDO;
import com.darkness.user.mapper.UserMapper;
import com.darkness.user.model.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;

    public UserVO register(String username, String password) {
        if (username == null || username.isBlank()) throw new BizException(400, "Username is required");
        if (password == null || password.isBlank()) throw new BizException(400, "Password is required");

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (count > 0) throw new BizException(400, "Username already exists");

        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(passwordUtil.encode(password));
        user.setNickname(username);
        user.setStatus(1);
        userMapper.insert(user);
        return UserVO.from(user);
    }

    public TokenVO login(String username, String password) {
        UserDO user = userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (user == null) throw new BizException(401, "Invalid credentials");
        if (user.getPassword() == null || !passwordUtil.matches(password, user.getPassword())) {
            throw new BizException(401, "Invalid credentials");
        }
        return generateTokenPair(user.getId());
    }

    public TokenVO refresh(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BizException(401, "Invalid refresh token");
        }
        Long userId = jwtUtil.getUserId(refreshToken);
        return generateTokenPair(userId);
    }

    TokenVO generateTokenPair(Long userId) {
        String accessToken = jwtUtil.generateAccessToken(userId);
        String refreshToken = jwtUtil.generateRefreshToken(userId);
        return new TokenVO(accessToken, refreshToken, 7200L);
    }
}
```

- [ ] **Step 2: Create `AuthController.java`**

```java
package com.darkness.auth.controller;

import com.darkness.auth.model.TokenVO;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.service.SmsService;
import com.darkness.auth.service.WxAuthService;
import com.darkness.common.result.Result;
import com.darkness.user.model.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SmsService smsService;
    private final WxAuthService wxAuthService;

    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody Map<String, String> body) {
        return Result.success(authService.register(body.get("username"), body.get("password")));
    }

    @PostMapping("/login")
    public Result<TokenVO> login(@RequestBody Map<String, String> body) {
        return Result.success(authService.login(body.get("username"), body.get("password")));
    }

    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@RequestBody Map<String, String> body) {
        return Result.success(authService.refresh(body.get("refreshToken")));
    }

    @PostMapping("/sms/send")
    public Result<Void> sendSmsCode(@RequestBody Map<String, String> body) {
        smsService.sendCode(body.get("phone"));
        return Result.success();
    }

    @PostMapping("/sms/login")
    public Result<TokenVO> smsLogin(@RequestBody Map<String, String> body) {
        return Result.success(smsService.login(body.get("phone"), body.get("code")));
    }

    @PostMapping("/wx/qrcode")
    public Result<Map<String, String>> wxQrcode() {
        return Result.success(wxAuthService.generateQrcode());
    }

    @PostMapping("/wx/callback")
    public Result<TokenVO> wxCallback(@RequestBody Map<String, String> body) {
        return Result.success(wxAuthService.handleCallback(body.get("code")));
    }
}
```

- [ ] **Step 3: Create stub services for SMS and WeChat (so AuthController compiles)**

`src/main/java/com/darkness/auth/service/SmsService.java`:

```java
package com.darkness.auth.service;

import com.darkness.auth.model.TokenVO;

public interface SmsService {

    void sendCode(String phone);

    TokenVO login(String phone, String code);
}
```

`src/main/java/com/darkness/auth/service/WxAuthService.java`:

```java
package com.darkness.auth.service;

import java.util.Map;

public interface WxAuthService {

    Map<String, String> generateQrcode();

    TokenVO handleCallback(String code);
}
```

Import `com.darkness.auth.model.TokenVO` in WxAuthService.

- [ ] **Step 4: Verify build**

```bash
mvn clean compile -q
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add AuthController with register, login, refresh endpoints"
```

---

### Task 8: Auth — SMS verification code login

**Files:**
- Create: `src/main/java/com/darkness/auth/entity/SmsCodeDO.java`
- Create: `src/main/java/com/darkness/auth/mapper/SmsCodeMapper.java`
- Create: `src/main/java/com/darkness/auth/service/impl/SmsServiceImpl.java`

- [ ] **Step 1: Create `SmsCodeDO.java`**

```java
package com.darkness.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sms_code")
public class SmsCodeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;
    private String code;
    private Integer used;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Create `SmsCodeMapper.java`**

```java
package com.darkness.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.auth.entity.SmsCodeDO;

public interface SmsCodeMapper extends BaseMapper<SmsCodeDO> {
}
```

- [ ] **Step 3: Create `SmsServiceImpl.java`**

```java
package com.darkness.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.darkness.auth.entity.SmsCodeDO;
import com.darkness.auth.mapper.SmsCodeMapper;
import com.darkness.auth.model.TokenVO;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.service.SmsService;
import com.darkness.common.exception.BizException;
import com.darkness.user.entity.UserDO;
import com.darkness.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {

    private final SmsCodeMapper smsCodeMapper;
    private final UserMapper userMapper;
    private final AuthService authService;

    @Value("${sms.provider:mock}")
    private String smsProvider;

    @Override
    public void sendCode(String phone) {
        if (phone == null || phone.isBlank()) throw new BizException(400, "Phone number is required");

        String code = String.format("%06d", new Random().nextInt(1000000));

        SmsCodeDO smsCode = new SmsCodeDO();
        smsCode.setPhone(phone);
        smsCode.setCode(code);
        smsCode.setUsed(0);
        smsCode.setExpiredAt(LocalDateTime.now().plusMinutes(5));
        smsCodeMapper.insert(smsCode);

        if ("mock".equals(smsProvider)) {
            log.info("[Mock SMS] Phone: {}, Code: {}", phone, code);
        } else {
            // TODO: integrate with real SMS provider (Aliyun/Tencent Cloud)
            log.info("[SMS] Sending code to phone: {}", phone);
        }
    }

    @Override
    public TokenVO login(String phone, String code) {
        if (phone == null || code == null) throw new BizException(400, "Phone and code are required");

        SmsCodeDO smsCode = smsCodeMapper.selectOne(
                new LambdaQueryWrapper<SmsCodeDO>()
                        .eq(SmsCodeDO::getPhone, phone)
                        .eq(SmsCodeDO::getCode, code)
                        .eq(SmsCodeDO::getUsed, 0)
                        .gt(SmsCodeDO::getExpiredAt, LocalDateTime.now())
                        .orderByDesc(SmsCodeDO::getCreatedAt)
                        .last("LIMIT 1"));

        if (smsCode == null) throw new BizException(401, "Invalid or expired verification code");

        smsCodeMapper.update(null,
                new LambdaUpdateWrapper<SmsCodeDO>().eq(SmsCodeDO::getId, smsCode.getId()).set(SmsCodeDO::getUsed, 1));

        // Auto-register if phone not found
        UserDO user = userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getPhone, phone));
        if (user == null) {
            user = new UserDO();
            user.setPhone(phone);
            user.setNickname("user_" + phone.substring(Math.max(0, phone.length() - 4)));
            user.setStatus(1);
            userMapper.insert(user);
        }

        return authService.generateTokenPair(user.getId());
    }
}
```

- [ ] **Step 4: Verify build**

```bash
mvn clean compile -q
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add SMS verification code login with auto-registration"
```

---

### Task 9: Auth — WeChat open platform login

**Files:**
- Create: `src/main/java/com/darkness/auth/entity/WxUserDO.java`
- Create: `src/main/java/com/darkness/auth/mapper/WxUserMapper.java`
- Create: `src/main/java/com/darkness/auth/service/impl/WxAuthServiceImpl.java`
- Create: `src/main/java/com/darkness/config/WxConfig.java`

- [ ] **Step 1: Create `WxUserDO.java`**

```java
package com.darkness.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wx_user")
public class WxUserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;
    private String unionId;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Create `WxUserMapper.java`**

```java
package com.darkness.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.auth.entity.WxUserDO;

public interface WxUserMapper extends BaseMapper<WxUserDO> {
}
```

- [ ] **Step 3: Create `WxConfig.java`**

```java
package com.darkness.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wx.open")
public class WxConfig {

    private String appId;
    private String appSecret;
}
```

- [ ] **Step 4: Create `WxAuthServiceImpl.java`**

```java
package com.darkness.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.auth.entity.WxUserDO;
import com.darkness.auth.mapper.WxUserMapper;
import com.darkness.auth.model.TokenVO;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.service.WxAuthService;
import com.darkness.common.exception.BizException;
import com.darkness.config.WxConfig;
import com.darkness.user.entity.UserDO;
import com.darkness.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WxAuthServiceImpl implements WxAuthService {

    private final WxConfig wxConfig;
    private final WxUserMapper wxUserMapper;
    private final UserMapper userMapper;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, String> generateQrcode() {
        String state = UUID.randomUUID().toString().replace("-", "");
        String url = String.format(
                "https://open.weixin.qq.com/connect/qrconnect?appid=%s&redirect_uri=&response_type=code&scope=snsapi_login&state=%s",
                wxConfig.getAppId(), state);
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        result.put("state", state);
        return result;
    }

    @Override
    public TokenVO handleCallback(String code) {
        if (code == null || code.isBlank()) throw new BizException(400, "Authorization code is required");

        // Exchange code for access_token + openid
        String tokenUrl = String.format(
                "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
                wxConfig.getAppId(), wxConfig.getAppSecret(), code);

        String response;
        try {
            RestClient restClient = RestClient.create();
            response = restClient.get().uri(tokenUrl).retrieve().body(String.class);
            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode")) {
                throw new BizException(400, "WeChat auth failed: " + json.get("errmsg").asText());
            }

            String openid = json.get("openid").asText();
            String accessToken = json.get("access_token").asText();

            // Get user info
            String userInfoUrl = String.format(
                    "https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s",
                    accessToken, openid);
            String userInfoResponse = restClient.get().uri(userInfoUrl).retrieve().body(String.class);
            JsonNode userInfo = objectMapper.readTree(userInfoResponse);

            // Find or create binding
            WxUserDO wxUser = wxUserMapper.selectOne(
                    new LambdaQueryWrapper<WxUserDO>().eq(WxUserDO::getOpenid, openid));

            Long userId;
            if (wxUser != null) {
                userId = wxUser.getUserId();
            } else {
                // Auto-register
                String nickname = userInfo.has("nickname") ? userInfo.get("nickname").asText() : "wx_user";
                String avatar = userInfo.has("headimgurl") ? userInfo.get("headimgurl").asText() : null;

                UserDO user = new UserDO();
                user.setNickname(nickname);
                user.setAvatar(avatar);
                user.setStatus(1);
                userMapper.insert(user);
                userId = user.getId();

                wxUser = new WxUserDO();
                wxUser.setOpenid(openid);
                wxUser.setUserId(userId);
                wxUser.setNickname(nickname);
                wxUser.setAvatarUrl(avatar);
                wxUserMapper.insert(wxUser);
            }

            return authService.generateTokenPair(userId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("WeChat auth error", e);
            throw new BizException(500, "WeChat authentication failed");
        }
    }
}
```

- [ ] **Step 5: Verify build**

```bash
mvn clean compile -q
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add WeChat open platform login with auto-registration"
```

---

## Phase 3: Agent & Chat (Tasks 10-11)

Adds Agent management and SSE streaming chat.

### Task 10: Agent management CRUD

**Files:**
- Create: `src/main/java/com/darkness/agent/entity/AgentDO.java`
- Create: `src/main/java/com/darkness/agent/mapper/AgentMapper.java`
- Create: `src/main/java/com/darkness/agent/model/AgentVO.java`
- Create: `src/main/java/com/darkness/agent/service/AgentService.java`
- Create: `src/main/java/com/darkness/agent/service/impl/AgentServiceImpl.java`
- Create: `src/main/java/com/darkness/agent/controller/AgentController.java`

- [ ] **Step 1: Create `AgentDO.java`**

```java
package com.darkness.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent")
public class AgentDO extends BaseEntity {

    private String name;
    private String description;
    private String avatar;
    private String apiUrl;
    private String apiKey;
    private String model;
    private Integer status;
    private String configJson;
}
```

- [ ] **Step 2: Create `AgentMapper.java`**

```java
package com.darkness.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.agent.entity.AgentDO;

public interface AgentMapper extends BaseMapper<AgentDO> {
}
```

- [ ] **Step 3: Create `AgentVO.java`**

```java
package com.darkness.agent.model;

import com.darkness.agent.entity.AgentDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentVO {

    private Long id;
    private String name;
    private String description;
    private String avatar;
    private String apiUrl;
    private String model;
    private Integer status;
    private String configJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AgentVO from(AgentDO entity) {
        if (entity == null) return null;
        AgentVO vo = new AgentVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setAvatar(entity.getAvatar());
        vo.setApiUrl(entity.getApiUrl());
        vo.setModel(entity.getModel());
        vo.setStatus(entity.getStatus());
        vo.setConfigJson(entity.getConfigJson());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public AgentDO toEntity() {
        AgentDO entity = new AgentDO();
        entity.setId(this.id);
        entity.setName(this.name);
        entity.setDescription(this.description);
        entity.setAvatar(this.avatar);
        entity.setApiUrl(this.apiUrl);
        entity.setApiKey(this.apiKey);
        entity.setModel(this.model);
        entity.setStatus(this.status);
        entity.setConfigJson(this.configJson);
        return entity;
    }
}
```

Wait — AgentVO should NOT expose apiKey. The `toEntity` method should not set apiKey from VO (apiKey is managed separately). Let me fix:

```java
package com.darkness.agent.model;

import com.darkness.agent.entity.AgentDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentVO {

    private Long id;
    private String name;
    private String description;
    private String avatar;
    private String apiUrl;
    private String apiKey;
    private String model;
    private Integer status;
    private String configJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AgentVO from(AgentDO entity) {
        if (entity == null) return null;
        AgentVO vo = new AgentVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setAvatar(entity.getAvatar());
        vo.setApiUrl(entity.getApiUrl());
        vo.setApiKey(entity.getApiKey() != null ? "******" : null);
        vo.setModel(entity.getModel());
        vo.setStatus(entity.getStatus());
        vo.setConfigJson(entity.getConfigJson());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public AgentDO toEntity() {
        AgentDO entity = new AgentDO();
        entity.setId(this.id);
        entity.setName(this.name);
        entity.setDescription(this.description);
        entity.setAvatar(this.avatar);
        entity.setApiUrl(this.apiUrl);
        entity.setApiKey(this.apiKey);
        entity.setModel(this.model);
        entity.setStatus(this.status);
        entity.setConfigJson(this.configJson);
        return entity;
    }
}
```

Actually, the apiKey should be write-through on create/update but masked on read. The `toEntity` method handles write. The `from` method masks it. That's the correct approach — apiKey in the VO request body is the raw key, but the response masks it.

- [ ] **Step 4: Create `AgentService.java` and `AgentServiceImpl.java`**

`src/main/java/com/darkness/agent/service/AgentService.java`:

```java
package com.darkness.agent.service;

import com.darkness.agent.model.AgentVO;

import java.util.List;

public interface AgentService {

    AgentVO getAgentById(Long id);

    List<AgentVO> listAgents();

    AgentVO createAgent(AgentVO vo);

    AgentVO updateAgent(Long id, AgentVO vo);

    void deleteAgent(Long id);
}
```

`src/main/java/com/darkness/agent/service/impl/AgentServiceImpl.java`:

```java
package com.darkness.agent.service.impl;

import com.darkness.agent.entity.AgentDO;
import com.darkness.agent.mapper.AgentMapper;
import com.darkness.agent.model.AgentVO;
import com.darkness.agent.service.AgentService;
import com.darkness.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;

    @Override
    public AgentVO getAgentById(Long id) {
        AgentDO agent = agentMapper.selectById(id);
        if (agent == null) throw new BizException(404, "Agent not found: " + id);
        return AgentVO.from(agent);
    }

    @Override
    public List<AgentVO> listAgents() {
        return agentMapper.selectList(null).stream().map(AgentVO::from).toList();
    }

    @Override
    public AgentVO createAgent(AgentVO vo) {
        AgentDO entity = vo.toEntity();
        agentMapper.insert(entity);
        return AgentVO.from(entity);
    }

    @Override
    public AgentVO updateAgent(Long id, AgentVO vo) {
        AgentDO existing = agentMapper.selectById(id);
        if (existing == null) throw new BizException(404, "Agent not found: " + id);
        AgentDO entity = vo.toEntity();
        entity.setId(id);
        // Keep existing apiKey if not provided in update
        if (entity.getApiKey() == null || "******".equals(entity.getApiKey())) {
            entity.setApiKey(existing.getApiKey());
        }
        agentMapper.updateById(entity);
        return AgentVO.from(agentMapper.selectById(id));
    }

    @Override
    public void deleteAgent(Long id) {
        AgentDO existing = agentMapper.selectById(id);
        if (existing == null) throw new BizException(404, "Agent not found: " + id);
        agentMapper.deleteById(id);
    }
}
```

- [ ] **Step 5: Create `AgentController.java`**

```java
package com.darkness.agent.controller;

import com.darkness.agent.model.AgentVO;
import com.darkness.agent.service.AgentService;
import com.darkness.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public Result<List<AgentVO>> listAgents() {
        return Result.success(agentService.listAgents());
    }

    @GetMapping("/{id}")
    public Result<AgentVO> getAgent(@PathVariable Long id) {
        return Result.success(agentService.getAgentById(id));
    }

    @PostMapping
    public Result<AgentVO> createAgent(@RequestBody AgentVO vo) {
        return Result.success(agentService.createAgent(vo));
    }

    @PutMapping("/{id}")
    public Result<AgentVO> updateAgent(@PathVariable Long id, @RequestBody AgentVO vo) {
        return Result.success(agentService.updateAgent(id, vo));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAgent(@PathVariable Long id) {
        agentService.deleteAgent(id);
        return Result.success();
    }
}
```

- [ ] **Step 6: Verify build**

```bash
mvn clean compile -q
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add Agent management CRUD"
```

---

### Task 11: Chat — Conversation, Message, SSE streaming

**Files:**
- Create: `src/main/java/com/darkness/agent/entity/ConversationDO.java`
- Create: `src/main/java/com/darkness/agent/entity/MessageDO.java`
- Create: `src/main/java/com/darkness/agent/mapper/ConversationMapper.java`
- Create: `src/main/java/com/darkness/agent/mapper/MessageMapper.java`
- Create: `src/main/java/com/darkness/agent/model/ConversationVO.java`
- Create: `src/main/java/com/darkness/agent/model/MessageVO.java`
- Create: `src/main/java/com/darkness/agent/client/AgentClient.java`
- Create: `src/main/java/com/darkness/agent/service/ChatService.java`
- Create: `src/main/java/com/darkness/agent/service/impl/ChatServiceImpl.java`
- Create: `src/main/java/com/darkness/agent/controller/ChatController.java`

- [ ] **Step 1: Create entity files**

`src/main/java/com/darkness/agent/entity/ConversationDO.java`:

```java
package com.darkness.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.darkness.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation")
public class ConversationDO extends BaseEntity {

    private Long userId;
    private Long agentId;
    private String title;
}
```

`src/main/java/com/darkness/agent/entity/MessageDO.java`:

```java
package com.darkness.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class MessageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private String role;
    private String content;
    private Integer tokens;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Create mapper files**

`src/main/java/com/darkness/agent/mapper/ConversationMapper.java`:

```java
package com.darkness.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.agent.entity.ConversationDO;

public interface ConversationMapper extends BaseMapper<ConversationDO> {
}
```

`src/main/java/com/darkness/agent/mapper/MessageMapper.java`:

```java
package com.darkness.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.agent.entity.MessageDO;

public interface MessageMapper extends BaseMapper<MessageDO> {
}
```

- [ ] **Step 3: Create VO files**

`src/main/java/com/darkness/agent/model/ConversationVO.java`:

```java
package com.darkness.agent.model;

import com.darkness.agent.entity.ConversationDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {

    private Long id;
    private Long userId;
    private Long agentId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ConversationVO from(ConversationDO entity) {
        if (entity == null) return null;
        ConversationVO vo = new ConversationVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setAgentId(entity.getAgentId());
        vo.setTitle(entity.getTitle());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
```

`src/main/java/com/darkness/agent/model/MessageVO.java`:

```java
package com.darkness.agent.model;

import com.darkness.agent.entity.MessageDO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {

    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private Integer tokens;
    private LocalDateTime createdAt;

    public static MessageVO from(MessageDO entity) {
        if (entity == null) return null;
        MessageVO vo = new MessageVO();
        vo.setId(entity.getId());
        vo.setConversationId(entity.getConversationId());
        vo.setRole(entity.getRole());
        vo.setContent(entity.getContent());
        vo.setTokens(entity.getTokens());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
```

- [ ] **Step 4: Create `AgentClient.java`** (HTTP client for external Agent APIs)

```java
package com.darkness.agent.client;

import com.darkness.agent.entity.AgentDO;
import com.darkness.agent.entity.MessageDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentClient {

    private final ObjectMapper objectMapper;

    public Flux<String> stream(AgentDO agent, List<MessageDO> history, String userMessage) {
        List<Map<String, String>> messages = history.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(java.util.stream.Collectors.toList());
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model", agent.getModel());
        body.put("messages", messages);
        body.put("stream", true);

        return WebClient.create(agent.getApiUrl())
                .post()
                .header("Authorization", "Bearer " + agent.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank() && line.startsWith("data:"))
                .map(line -> line.substring(5).trim())
                .filter(data -> !"[DONE]".equals(data))
                .handle((data, sink) -> {
                    try {
                        JsonNode json = objectMapper.readTree(data);
                        JsonNode delta = json.at("/choices/0/delta/content");
                        if (!delta.isMissingNode() && !delta.isNull()) {
                            sink.next(delta.asText());
                        }
                    } catch (Exception e) {
                        log.debug("Skipping non-JSON SSE chunk: {}", data);
                    }
                });
    }
}
```

- [ ] **Step 5: Create `ChatService.java` and `ChatServiceImpl.java`**

`src/main/java/com/darkness/agent/service/ChatService.java`:

```java
package com.darkness.agent.service;

import com.darkness.agent.model.ConversationVO;
import com.darkness.agent.model.MessageVO;

import java.util.List;

public interface ChatService {

    ConversationVO createConversation(Long userId, Long agentId, String title);

    List<ConversationVO> listConversations(Long userId);

    List<MessageVO> getMessages(Long conversationId);

    void deleteConversation(Long conversationId, Long userId);
}
```

`src/main/java/com/darkness/agent/service/impl/ChatServiceImpl.java`:

```java
package com.darkness.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.agent.entity.ConversationDO;
import com.darkness.agent.entity.MessageDO;
import com.darkness.agent.mapper.ConversationMapper;
import com.darkness.agent.mapper.MessageMapper;
import com.darkness.agent.model.ConversationVO;
import com.darkness.agent.model.MessageVO;
import com.darkness.agent.service.ChatService;
import com.darkness.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @Override
    public ConversationVO createConversation(Long userId, Long agentId, String title) {
        ConversationDO conv = new ConversationDO();
        conv.setUserId(userId);
        conv.setAgentId(agentId);
        conv.setTitle(title);
        conversationMapper.insert(conv);
        return ConversationVO.from(conv);
    }

    @Override
    public List<ConversationVO> listConversations(Long userId) {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<ConversationDO>()
                        .eq(ConversationDO::getUserId, userId)
                        .orderByDesc(ConversationDO::getCreatedAt)
        ).stream().map(ConversationVO::from).toList();
    }

    @Override
    public List<MessageVO> getMessages(Long conversationId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<MessageDO>()
                        .eq(MessageDO::getConversationId, conversationId)
                        .orderByAsc(MessageDO::getCreatedAt)
        ).stream().map(MessageVO::from).toList();
    }

    @Override
    public void deleteConversation(Long conversationId, Long userId) {
        ConversationDO conv = conversationMapper.selectById(conversationId);
        if (conv == null) throw new BizException(404, "Conversation not found");
        if (!conv.getUserId().equals(userId)) throw new BizException(403, "Forbidden");
        conversationMapper.deleteById(conversationId);
    }
}
```

- [ ] **Step 6: Create `ChatController.java`** (SSE streaming endpoint)

```java
package com.darkness.agent.controller;

import com.darkness.agent.client.AgentClient;
import com.darkness.agent.entity.AgentDO;
import com.darkness.agent.entity.ConversationDO;
import com.darkness.agent.entity.MessageDO;
import com.darkness.agent.mapper.AgentMapper;
import com.darkness.agent.mapper.ConversationMapper;
import com.darkness.agent.mapper.MessageMapper;
import com.darkness.agent.model.ConversationVO;
import com.darkness.agent.model.MessageVO;
import com.darkness.auth.model.LoginUserDetails;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ConversationMapper conversationMapper;
    private final AgentMapper agentMapper;
    private final MessageMapper messageMapper;
    private final AgentClient agentClient;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping("/conversations")
    public Result<ConversationVO> createConversation(
            @AuthenticationPrincipal LoginUserDetails user,
            @RequestBody Map<String, Object> body) {
        Long agentId = Long.valueOf(body.get("agentId").toString());
        String title = (String) body.getOrDefault("title", "New Conversation");
        return Result.success(chatService.createConversation(user.getUserId(), agentId, title));
    }

    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations(
            @AuthenticationPrincipal LoginUserDetails user) {
        return Result.success(chatService.listConversations(user.getUserId()));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageVO>> getMessages(@PathVariable Long id) {
        return Result.success(chatService.getMessages(id));
    }

    @PostMapping("/conversations/{id}/send")
    public SseEmitter sendMessage(
            @AuthenticationPrincipal LoginUserDetails user,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String content = body.get("content");
        ConversationDO conv = conversationMapper.selectById(id);
        if (conv == null) throw new BizException(404, "Conversation not found");
        if (!conv.getUserId().equals(user.getUserId())) throw new BizException(403, "Forbidden");

        AgentDO agent = agentMapper.selectById(conv.getAgentId());
        if (agent == null) throw new BizException(404, "Agent not found");

        // Save user message
        MessageDO userMsg = new MessageDO();
        userMsg.setConversationId(id);
        userMsg.setRole("user");
        userMsg.setContent(content);
        messageMapper.insert(userMsg);

        // Load history
        List<MessageDO> history = messageMapper.selectList(
                new LambdaQueryWrapper<MessageDO>()
                        .eq(MessageDO::getConversationId, id)
                        .orderByAsc(MessageDO::getCreatedAt));

        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        executor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            try {
                agentClient.stream(agent, history, content)
                        .doOnNext(chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                                fullResponse.append(chunk);
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(() -> {
                            // Save assistant message
                            MessageDO assistantMsg = new MessageDO();
                            assistantMsg.setConversationId(id);
                            assistantMsg.setRole("assistant");
                            assistantMsg.setContent(fullResponse.toString());
                            messageMapper.insert(assistantMsg);
                            emitter.complete();
                        })
                        .doOnError(emitter::completeWithError)
                        .subscribe();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(
            @AuthenticationPrincipal LoginUserDetails user,
            @PathVariable Long id) {
        chatService.deleteConversation(id, user.getUserId());
        return Result.success();
    }
}
```

Add missing import for `ChatController`: `import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;`

- [ ] **Step 7: Verify build**

```bash
mvn clean compile -q
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add chat with SSE streaming, conversation and message management"
```

---

## Phase 4: Finalization (Task 12)

### Task 12: Update CLAUDE.md and verify full build

**Files:**
- Modify: `CLAUDE.md`
- Delete: stale test file (old UserControllerTest references dead code)

- [ ] **Step 1: Update `CLAUDE.md` to reflect new architecture**

Replace the entire content of `CLAUDE.md` with updated documentation reflecting the single-module structure, new API paths, Druid, JWT, etc. Key changes:
- Remove multi-module references (gateway, nacos, common module)
- Update build commands to single module
- Document new API paths (`/api/users`, `/api/roles`, `/api/permissions`, `/api/agents`, `/api/chat/**`, `/api/auth/**`)
- Document Druid, JWT, Spring Security
- Update tech architecture section

- [ ] **Step 2: Delete stale test**

Delete `acgagent-service-user/src/test/...` — this directory was removed in Task 1 Step 5. No action needed if already deleted.

- [ ] **Step 3: Full build verification**

```bash
mvn clean package -DskipTests -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Run tests**

```bash
mvn test
```

Expected: Tests pass (existing tests were deleted with old modules, new tests can be added later)

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "docs: update CLAUDE.md for new single-module architecture"
```
