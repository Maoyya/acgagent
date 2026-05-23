# 枚举、魔法值与工具类优化 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除项目中所有裸数字和字符串魔法值，建立枚举体系、常量管理、请求 DTO 和公共工具方法。

**Architecture:** 在 `common` 包下新建 `enums`、`util`、`constant` 子包，创建 ResultCode 枚举作为所有状态码的唯一来源，5 个业务枚举用 `@EnumValue` 映射数据库值，请求 DTO 替代 `Map<String, String>`，ServiceHelper 消除重复的 null 检查模式。

**Tech Stack:** Spring Boot 3.3.5, MyBatis-Plus 3.5.9 (@EnumValue), Lombok, JSR 303 (hibernate-validator)

---

## File Structure

### 新建文件

| 文件 | 职责 |
|------|------|
| `common/enums/CommonStatus.java` | 启用/禁用状态枚举 |
| `common/enums/MessageRole.java` | 消息角色枚举 |
| `common/enums/PermissionType.java` | 权限类型枚举 |
| `common/enums/TokenType.java` | JWT Token 类型枚举 |
| `common/enums/UsedStatus.java` | 验证码使用状态枚举 |
| `common/result/ResultCode.java` | 统一响应状态码枚举 |
| `common/util/ServiceHelper.java` | 查询不存在抛异常工具方法 |
| `common/constant/AuthConstants.java` | 认证常量（Bearer 前缀） |
| `agent/constant/SseConstants.java` | SSE 协议常量 |
| `agent/constant/AgentConstants.java` | Agent 模块常量（API Key 掩码） |
| `auth/constant/WxApiConstants.java` | 微信 API 常量 |
| `auth/model/LoginRequest.java` | 登录请求 DTO |
| `auth/model/RegisterRequest.java` | 注册请求 DTO |
| `auth/model/RefreshTokenRequest.java` | 刷新令牌请求 DTO |
| `auth/model/SmsSendRequest.java` | 发送短信请求 DTO |
| `auth/model/SmsLoginRequest.java` | 短信登录请求 DTO |
| `auth/model/WxLoginRequest.java` | 微信登录请求 DTO |
| `agent/model/CreateConversationRequest.java` | 创建会话请求 DTO |
| `agent/model/SendMessageRequest.java` | 发送消息请求 DTO |

### 修改文件

| 文件 | 变更内容 |
|------|---------|
| `common/result/Result.java` | 改用 ResultCode 构造 |
| `common/exception/BizException.java` | 增加 ResultCode 构造方法 |
| `common/exception/GlobalExceptionHandler.java` | 引用 ResultCode + 增加 JSR 303 处理 |
| `config/SecurityConfig.java` | 引用 ResultCode.UNAUTHORIZED |
| `auth/util/JwtUtil.java` | 引用 TokenType |
| `auth/filter/JwtAuthenticationFilter.java` | 引用 AuthConstants |
| `auth/controller/AuthController.java` | DTO 替代 Map |
| `auth/service/impl/AuthServiceImpl.java` | ResultCode + CommonStatus |
| `auth/service/impl/SmsServiceImpl.java` | ResultCode + UsedStatus + CommonStatus |
| `auth/service/impl/WxAuthServiceImpl.java` | ResultCode + CommonStatus + WxApiConstants |
| `auth/entity/SmsCodeDO.java` | used 字段改 UsedStatus |
| `user/entity/UserDO.java` | status 字段改 CommonStatus |
| `user/entity/RoleDO.java` | status 字段改 CommonStatus |
| `user/entity/PermissionDO.java` | status 改 CommonStatus, type 改 PermissionType |
| `user/model/UserVO.java` | status 字段改 CommonStatus |
| `user/model/RoleVO.java` | status 字段改 CommonStatus |
| `user/model/PermissionVO.java` | status 改 CommonStatus, type 改 PermissionType |
| `user/service/impl/UserServiceImpl.java` | ResultCode + ServiceHelper |
| `user/service/impl/RoleServiceImpl.java` | ResultCode + ServiceHelper |
| `user/service/impl/PermissionServiceImpl.java` | ResultCode + ServiceHelper |
| `agent/entity/AgentDO.java` | status 字段改 CommonStatus |
| `agent/entity/MessageDO.java` | role 字段改 MessageRole |
| `agent/model/AgentVO.java` | status 改 CommonStatus, apiKey 掩码引用 AgentConstants |
| `agent/model/MessageVO.java` | role 字段改 MessageRole |
| `agent/service/impl/AgentServiceImpl.java` | ResultCode + ServiceHelper + AgentConstants |
| `agent/service/impl/ChatServiceImpl.java` | ResultCode + ServiceHelper + MessageRole |
| `agent/controller/ChatController.java` | DTO 替代 Map + ResultCode |
| `agent/client/AgentClient.java` | SseConstants + AuthConstants + MessageRole |

---

### Task 1: 创建 ResultCode 枚举 + 改造 Result / BizException / GlobalExceptionHandler

**Files:**
- Create: `src/main/java/com/darkness/common/result/ResultCode.java`
- Modify: `src/main/java/com/darkness/common/result/Result.java`
- Modify: `src/main/java/com/darkness/common/exception/BizException.java`
- Modify: `src/main/java/com/darkness/common/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 创建 ResultCode 枚举**

```java
package com.darkness.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一业务响应状态码枚举。
 * 所有接口返回值和业务异常统一使用此枚举，禁止裸数字。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "Bad request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not found"),
    INTERNAL_ERROR(500, "Internal server error");

    private final int code;
    private final String message;
}
```

- [ ] **Step 2: 改造 Result 类**

将 `Result.java` 全文替换为：

```java
package com.darkness.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 JSON 响应体，封装 {code, message, data} 格式的接口返回结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 响应状态码，200 表示成功，其他值表示各类错误 */
    private Integer code;

    /** 响应描述信息，成功时为 "success"，失败时为具体错误描述 */
    private String message;

    /** 响应数据体，成功时携带业务数据，失败时为 null（JSON 序列化时自动忽略） */
    private T data;

    /**
     * 构建成功响应，携带数据。
     *
     * @param data 业务数据
     * @return code=200, message="success" 的成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构建成功响应，无数据体。
     *
     * @return code=200, message="success" 的成功响应，data 为 null
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构建错误响应，指定 ResultCode 和自定义错误信息。
     *
     * @param resultCode 错误状态码枚举
     * @param message     错误描述信息
     * @return 携带错误信息的 Result，data 为 null
     */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /**
     * 构建错误响应，指定状态码和错误信息（向后兼容，逐步废弃）。
     *
     * @param code    错误状态码
     * @param message 错误描述信息
     * @return 携带错误信息的 Result，data 为 null
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 构建错误响应，使用默认 500 状态码。
     *
     * @param message 错误描述信息
     * @return code=500 的错误响应，data 为 null
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ResultCode.INTERNAL_ERROR.getCode(), message, null);
    }
}
```

- [ ] **Step 3: 改造 BizException 类**

将 `BizException.java` 全文替换为：

```java
package com.darkness.common.exception;

import com.darkness.common.result.ResultCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常，携带 HTTP 风格的状态码，用于在业务逻辑中主动抛出可识别的错误。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizException extends RuntimeException {

    /** 错误状态码，采用 HTTP 风格，如 404-未找到、403-无权限、500-内部错误 */
    private Integer code;

    /**
     * 构造业务异常，指定 ResultCode 和自定义错误信息。
     *
     * @param resultCode 错误状态码枚举
     * @param message    错误描述信息
     */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 构造业务异常，指定状态码和错误信息（向后兼容，逐步废弃）。
     *
     * @param code    错误状态码
     * @param message 错误描述信息
     */
    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造业务异常，使用默认 500 状态码。
     *
     * @param message 错误描述信息
     */
    public BizException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_ERROR.getCode();
    }
}
```

- [ ] **Step 4: 改造 GlobalExceptionHandler**

将 `GlobalExceptionHandler.java` 全文替换为：

```java
package com.darkness.common.exception;

import com.darkness.common.result.Result;
import com.darkness.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，捕获 BizException、JSR 303 校验异常和未处理异常，统一返回 Result 响应体。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，将 BizException 中携带的状态码和错误信息包装为 Result 响应体返回。
     *
     * @param e 捕获到的业务异常
     * @return 包含业务错误码和错误描述的 {@link Result}，HTTP 状态码仍为 200
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理 JSR 303 参数校验异常，收集所有字段校验失败信息后返回 400 响应。
     *
     * @param e 校验异常
     * @return code=400、message 包含所有校验失败信息的 {@link Result}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 处理所有未捕获的未知异常，记录错误日志并返回统一的 500 内部错误响应。
     * 不向客户端暴露异常堆栈细节，固定返回 "Internal server error" 提示。
     *
     * @param e 捕获到的未知异常
     * @return code=500、message="Internal server error" 的 {@link Result}
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Internal server error", e);
        return Result.error(ResultCode.INTERNAL_ERROR, ResultCode.INTERNAL_ERROR.getMessage());
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `cd D:\ideaproject\acgAgent && mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/darkness/common/result/ResultCode.java src/main/java/com/darkness/common/result/Result.java src/main/java/com/darkness/common/exception/BizException.java src/main/java/com/darkness/common/exception/GlobalExceptionHandler.java
git commit -m "refactor: add ResultCode enum and refactor Result/BizException/GlobalExceptionHandler"
```

---

### Task 2: 创建 5 个业务枚举

**Files:**
- Create: `src/main/java/com/darkness/common/enums/CommonStatus.java`
- Create: `src/main/java/com/darkness/common/enums/MessageRole.java`
- Create: `src/main/java/com/darkness/common/enums/PermissionType.java`
- Create: `src/main/java/com/darkness/common/enums/TokenType.java`
- Create: `src/main/java/com/darkness/common/enums/UsedStatus.java`

- [ ] **Step 1: 创建 CommonStatus 枚举**

```java
package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用启用/禁用状态枚举，用于 User、Role、Permission、Agent 的 status 字段。
 */
@Getter
@AllArgsConstructor
public enum CommonStatus {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    @EnumValue
    private final int value;
    private final String description;
}
```

- [ ] **Step 2: 创建 MessageRole 枚举**

```java
package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 聊天消息角色枚举，用于 MessageDO 的 role 字段。
 */
@Getter
@AllArgsConstructor
public enum MessageRole {

    USER("user"),
    ASSISTANT("assistant");

    @EnumValue
    private final String value;
}
```

- [ ] **Step 3: 创建 PermissionType 枚举**

```java
package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 权限类型枚举，用于 PermissionDO 的 type 字段。
 */
@Getter
@AllArgsConstructor
public enum PermissionType {

    MENU(1, "菜单"),
    BUTTON(2, "按钮");

    @EnumValue
    private final int value;
    private final String description;
}
```

- [ ] **Step 4: 创建 TokenType 枚举**

```java
package com.darkness.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * JWT Token 类型枚举，用于 JwtUtil 中区分 access/refresh 令牌。
 */
@Getter
@AllArgsConstructor
public enum TokenType {

    ACCESS("access"),
    REFRESH("refresh");

    private final String value;
}
```

- [ ] **Step 5: 创建 UsedStatus 枚举**

```java
package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 验证码使用状态枚举，用于 SmsCodeDO 的 used 字段。
 */
@Getter
@AllArgsConstructor
public enum UsedStatus {

    UNUSED(0, "未使用"),
    USED(1, "已使用");

    @EnumValue
    private final int value;
    private final String description;
}
```

- [ ] **Step 6: 编译验证**

Run: `cd D:\ideaproject\acgAgent && mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/darkness/common/enums/
git commit -m "feat: add CommonStatus, MessageRole, PermissionType, TokenType, UsedStatus enums"
```

---

### Task 3: 将枚举应用到实体和 VO 类

**Files:**
- Modify: `src/main/java/com/darkness/user/entity/UserDO.java`
- Modify: `src/main/java/com/darkness/user/entity/RoleDO.java`
- Modify: `src/main/java/com/darkness/user/entity/PermissionDO.java`
- Modify: `src/main/java/com/darkness/user/model/UserVO.java`
- Modify: `src/main/java/com/darkness/user/model/RoleVO.java`
- Modify: `src/main/java/com/darkness/user/model/PermissionVO.java`
- Modify: `src/main/java/com/darkness/agent/entity/AgentDO.java`
- Modify: `src/main/java/com/darkness/agent/entity/MessageDO.java`
- Modify: `src/main/java/com/darkness/agent/model/AgentVO.java`
- Modify: `src/main/java/com/darkness/agent/model/MessageVO.java`
- Modify: `src/main/java/com/darkness/auth/entity/SmsCodeDO.java`

- [ ] **Step 1: 修改 UserDO — status 字段**

将 `UserDO.java` 中 `private Integer status;` 改为 `private CommonStatus status;`，并增加 import。

变更点：
```java
import com.darkness.common.enums.CommonStatus;
// ...
/** 状态：ENABLED-启用，DISABLED-禁用 */
private CommonStatus status;
```

- [ ] **Step 2: 修改 RoleDO — status 字段**

同上模式，`RoleDO.java` 中 `private Integer status;` → `private CommonStatus status;`，加 import。

- [ ] **Step 3: 修改 PermissionDO — status + type 字段**

`PermissionDO.java` 中：
```java
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PermissionType;
// ...
/** 权限类型：MENU-菜单，BUTTON-按钮 */
private PermissionType type;
// ...
/** 状态：ENABLED-启用，DISABLED-禁用 */
private CommonStatus status;
```

- [ ] **Step 4: 修改 UserVO — status 字段**

`UserVO.java` 中 `private Integer status;` → `private CommonStatus status;`，加 import。`from()` 和 `toEntity()` 中 `entity.getStatus()` / `this.status` 赋值不变（类型随字段一起变了）。

- [ ] **Step 5: 修改 RoleVO — status 字段**

同上模式。

- [ ] **Step 6: 修改 PermissionVO — status + type 字段**

`PermissionVO.java` 中：
```java
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PermissionType;
// ...
/** 权限类型：MENU-菜单，BUTTON-按钮 */
private PermissionType type;
// ...
/** 状态：ENABLED-启用，DISABLED-禁用 */
private CommonStatus status;
```

- [ ] **Step 7: 修改 AgentDO — status 字段**

`AgentDO.java` 中 `private Integer status;` → `private CommonStatus status;`，加 import。

- [ ] **Step 8: 修改 MessageDO — role 字段**

`MessageDO.java` 中：
```java
import com.darkness.common.enums.MessageRole;
// ...
/** 消息角色：USER-用户发送，ASSISTANT-AI 回复 */
private MessageRole role;
```

- [ ] **Step 9: 修改 AgentVO — status 字段 + apiKey 掩码常量引用**

`AgentVO.java` 中 `private Integer status;` → `private CommonStatus status;`，加 import。`from()` 方法中 `"******"` 暂不改（Task 5 统一处理）。

- [ ] **Step 10: 修改 MessageVO — role 字段**

`MessageVO.java` 中：
```java
import com.darkness.common.enums.MessageRole;
// ...
/** 消息角色：USER-用户发送，ASSISTANT-AI 回复 */
private MessageRole role;
```

- [ ] **Step 11: 修改 SmsCodeDO — used 字段**

`SmsCodeDO.java` 中：
```java
import com.darkness.common.enums.UsedStatus;
// ...
/** 使用状态：UNUSED-未使用，USED-已使用 */
private UsedStatus used;
```

- [ ] **Step 12: 编译验证**

Run: `cd D:\ideaproject\acgAgent && mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 13: 提交**

```bash
git add src/main/java/com/darkness/user/entity/ src/main/java/com/darkness/user/model/ src/main/java/com/darkness/agent/entity/ src/main/java/com/darkness/agent/model/ src/main/java/com/darkness/auth/entity/SmsCodeDO.java
git commit -m "refactor: apply enums to entity and VO fields (status/type/role/used)"
```

---

### Task 4: 替换 Service 层中的魔法值 + 应用 ServiceHelper

**Files:**
- Create: `src/main/java/com/darkness/common/util/ServiceHelper.java`
- Modify: `src/main/java/com/darkness/auth/service/impl/AuthServiceImpl.java`
- Modify: `src/main/java/com/darkness/auth/service/impl/SmsServiceImpl.java`
- Modify: `src/main/java/com/darkness/auth/service/impl/WxAuthServiceImpl.java`
- Modify: `src/main/java/com/darkness/user/service/impl/UserServiceImpl.java`
- Modify: `src/main/java/com/darkness/user/service/impl/RoleServiceImpl.java`
- Modify: `src/main/java/com/darkness/user/service/impl/PermissionServiceImpl.java`
- Modify: `src/main/java/com/darkness/agent/service/impl/AgentServiceImpl.java`
- Modify: `src/main/java/com/darkness/agent/service/impl/ChatServiceImpl.java`

- [ ] **Step 1: 创建 ServiceHelper 工具类**

```java
package com.darkness.common.util;

import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;

/**
 * Service 层公共工具方法。
 * 提供实体查询不存在时统一抛异常的模式。
 */
public class ServiceHelper {

    private ServiceHelper() {}

    /**
     * 校验实体是否存在，不存在则抛 NOT_FOUND 异常。
     *
     * @param entity 查询结果，可为 null
     * @param name   实体名称，用于错误消息（如 "User"、"Agent"）
     * @param id     查询使用的 ID，用于错误消息
     * @return 非 null 的实体
     * @throws BizException entity 为 null 时抛出 NOT_FOUND
     */
    public static <T> T findOrThrow(T entity, String name, Object id) {
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, name + " not found: " + id);
        }
        return entity;
    }
}
```

- [ ] **Step 2: 改造 AuthServiceImpl**

全文替换为：

```java
package com.darkness.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.auth.model.TokenVO;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.util.JwtUtil;
import com.darkness.auth.util.PasswordUtil;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
import com.darkness.user.entity.UserDO;
import com.darkness.user.mapper.UserMapper;
import com.darkness.user.model.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现，处理用户注册、密码登录和 Token 对生成。
 * <p>
 * 注册流程：校验用户名非空 + 唯一性 -> BCrypt 加密密码 -> 插入 user 表 -> 返回 UserVO。
 * 登录流程：按用户名查询 -> BCrypt 比对密码 -> 签发 accessToken + refreshToken。
 * 刷新流程：校验 refreshToken 有效且类型为 refresh -> 重新签发 Token 对。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册。
     * 校验用户名和密码非空，查询 user 表确认用户名唯一（已存在则抛 BizException(400)），
     * 使用 BCrypt 加密明文密码后插入用户记录，默认昵称取用户名，状态设为启用。
     *
     * @param username 用户名，不能为空且不能重复
     * @param password 明文密码，不能为空
     * @return 注册后的用户视图对象
     */
    @Override
    public UserVO register(String username, String password) {
        if (username == null || username.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, "Username is required");
        if (password == null || password.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, "Password is required");

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (count > 0) throw new BizException(ResultCode.BAD_REQUEST, "Username already exists");

        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(passwordUtil.encode(password));
        user.setNickname(username);
        user.setStatus(CommonStatus.ENABLED);
        userMapper.insert(user);
        return UserVO.from(user);
    }

    /**
     * 账号密码登录。
     * 根据用户名查询 user 表，用户不存在或密码不匹配时抛出 BizException(401)，
     * 校验通过后调用 generateTokenPair 签发 accessToken 和 refreshToken。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return Token 对（accessToken + refreshToken + expiresIn）
     */
    @Override
    public TokenVO login(String username, String password) {
        UserDO user = userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED, "Invalid credentials");
        if (user.getPassword() == null || !passwordUtil.matches(password, user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "Invalid credentials");
        }
        return generateTokenPair(user.getId());
    }

    /**
     * 使用 refreshToken 刷新令牌。
     * 依次校验 refreshToken 非空、签名有效（未过期）、类型为 refresh，任一不满足则抛出 BizException(401)。
     * 解析出 userId 后重新签发新的 Token 对。
     *
     * @param refreshToken 刷新令牌
     * @return 新的 Token 对
     */
    @Override
    public TokenVO refresh(String refreshToken) {
        if (refreshToken == null || !jwtUtil.isTokenValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "Invalid refresh token");
        }
        Long userId = jwtUtil.getUserId(refreshToken);
        return generateTokenPair(userId);
    }

    /**
     * 为指定用户生成 Token 对（accessToken + refreshToken）。
     * accessToken 默认 2 小时有效，refreshToken 默认 7 天有效，expiresIn 固定返回 7200 秒。
     *
     * @param userId 用户 ID
     * @return Token 对
     */
    @Override
    public TokenVO generateTokenPair(Long userId) {
        String accessToken = jwtUtil.generateAccessToken(userId);
        String refreshToken = jwtUtil.generateRefreshToken(userId);
        // TODO: expiresIn 应与 jwt.access-token-expiration 配置保持一致
        return new TokenVO(accessToken, refreshToken, 7200L);
    }
}
```

- [ ] **Step 3: 改造 SmsServiceImpl**

全文替换为：

```java
package com.darkness.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.darkness.auth.entity.SmsCodeDO;
import com.darkness.auth.mapper.SmsCodeMapper;
import com.darkness.auth.model.TokenVO;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.service.SmsService;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.UsedStatus;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
import com.darkness.user.entity.UserDO;
import com.darkness.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 短信验证码服务实现，包含发送验证码和验证码登录逻辑。
 * <p>
 * 验证码有效期为 5 分钟，登录时若手机号未注册则自动创建账号。
 * 通过 sms.provider 配置项切换短信发送方式：mock（默认，仅打印日志）或真实短信网关。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {

    private final SmsCodeMapper smsCodeMapper;
    private final UserMapper userMapper;
    private final AuthService authService;

    /** 短信发送提供者，取值：mock（默认，仅打印日志）或真实短信网关标识 */
    @Value("${sms.provider:mock}")
    private String smsProvider;

    /**
     * 向指定手机号发送 6 位随机数字验证码。
     * 生成验证码后写入 sms_code 表（used=UNUSED，expiredAt=当前时间+5分钟），
     * mock 模式下仅输出到日志，方便本地开发调试。
     *
     * @param phone 手机号，不能为空
     */
    @Override
    public void sendCode(String phone) {
        if (phone == null || phone.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, "Phone number is required");

        String code = String.format("%06d", new Random().nextInt(1000000));

        SmsCodeDO smsCode = new SmsCodeDO();
        smsCode.setPhone(phone);
        smsCode.setCode(code);
        smsCode.setUsed(UsedStatus.UNUSED);
        // 验证码 5 分钟后过期
        smsCode.setExpiredAt(LocalDateTime.now().plusMinutes(5));
        smsCodeMapper.insert(smsCode);

        // mock 模式下直接打印验证码到日志，方便开发调试
        if ("mock".equals(smsProvider)) {
            log.info("[Mock SMS] Phone: {}, Code: {}", phone, code);
        } else {
            log.info("[SMS] Sending code to phone: {}", phone);
        }
    }

    /**
     * 短信验证码登录。
     * 查询该手机号未使用且未过期的验证码记录，
     * 验证码无效或已过期时抛出 BizException(401)。验证通过后标记 used=USED 防止重复消费。
     * 若手机号未注册则自动创建用户（昵称取 "user_" + 手机号后四位），最后签发 Token 对。
     *
     * @param phone 手机号
     * @param code  6 位数字验证码
     * @return accessToken 和 refreshToken
     */
    @Override
    public TokenVO login(String phone, String code) {
        if (phone == null || code == null) throw new BizException(ResultCode.BAD_REQUEST, "Phone and code are required");

        // 查询未使用且未过期的验证码
        SmsCodeDO smsCode = smsCodeMapper.selectOne(
                new LambdaQueryWrapper<SmsCodeDO>()
                        .eq(SmsCodeDO::getPhone, phone)
                        .eq(SmsCodeDO::getCode, code)
                        .eq(SmsCodeDO::getUsed, UsedStatus.UNUSED)
                        .gt(SmsCodeDO::getExpiredAt, LocalDateTime.now())
                        .orderByDesc(SmsCodeDO::getCreatedAt)
                        .last("LIMIT 1"));

        if (smsCode == null) throw new BizException(ResultCode.UNAUTHORIZED, "Invalid or expired verification code");

        // 标记验证码已使用，防止重复使用
        smsCodeMapper.update(null,
                new LambdaUpdateWrapper<SmsCodeDO>().eq(SmsCodeDO::getId, smsCode.getId()).set(SmsCodeDO::getUsed, UsedStatus.USED));

        UserDO user = userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getPhone, phone));
        // 手机号未注册时自动创建账号，昵称取手机号后四位拼接
        if (user == null) {
            user = new UserDO();
            user.setPhone(phone);
            user.setNickname("user_" + phone.substring(Math.max(0, phone.length() - 4)));
            user.setStatus(CommonStatus.ENABLED);
            userMapper.insert(user);
        }

        return authService.generateTokenPair(user.getId());
    }
}
```

- [ ] **Step 4: 改造 WxAuthServiceImpl**

全文替换为：

```java
package com.darkness.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.auth.constant.WxApiConstants;
import com.darkness.auth.entity.WxUserDO;
import com.darkness.auth.mapper.WxUserMapper;
import com.darkness.auth.model.TokenVO;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.service.WxAuthService;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
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

/**
 * 微信扫码登录服务实现。
 * 流程：生成二维码 URL -> 用户扫码 -> 回调获取 access_token -> 查询/创建用户及绑定关系 -> 签发 JWT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAuthServiceImpl implements WxAuthService {

    private final WxConfig wxConfig;
    private final WxUserMapper wxUserMapper;
    private final UserMapper userMapper;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    /**
     * 生成微信扫码登录二维码 URL。
     * 生成 UUID 作为 state 参数用于防 CSRF 攻击，拼接微信 OAuth2 授权链接
     * （appid + snsapi_login + state），返回 URL 和 state 供前端渲染二维码。
     *
     * @return 包含 url（二维码地址）和 state（防 CSRF 随机串）的 Map
     */
    @Override
    public Map<String, String> generateQrcode() {
        // state 用于防 CSRF 攻击，回调时需校验一致性
        String state = UUID.randomUUID().toString().replace("-", "");
        String url = String.format(
                "https://open.weixin.qq.com/connect/qrconnect?appid=%s&redirect_uri=&response_type=code&scope=snsapi_login&state=%s",
                wxConfig.getAppId(), state);
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        result.put("state", state);
        return result;
    }

    /**
     * 处理微信扫码回调。
     * 使用授权码调用微信 OAuth2 接口换取 access_token 和 openid（失败时抛出 BizException(400)），
     * 再用 access_token 拉取微信用户信息（昵称、头像）。
     * 查询 wx_user 表：若 openid 已绑定则直接获取关联的系统 userId；
     * 若未绑定则自动创建系统用户 + 建立 wx_user 绑定关系，最后签发 Token 对。
     *
     * @param code 微信返回的授权码，不能为空
     * @return 令牌对（accessToken + refreshToken）
     */
    @Override
    public TokenVO handleCallback(String code) {
        if (code == null || code.isBlank()) throw new BizException(ResultCode.BAD_REQUEST, "Authorization code is required");

        // 用授权码向微信服务器换取 access_token 和 openid
        String tokenUrl = String.format(
                "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=%s",
                wxConfig.getAppId(), wxConfig.getAppSecret(), code, WxApiConstants.GRANT_TYPE_AUTH_CODE);

        try {
            RestClient restClient = RestClient.create();
            String response = restClient.get().uri(tokenUrl).retrieve().body(String.class);
            JsonNode json = objectMapper.readTree(response);

            if (json.has(WxApiConstants.FIELD_ERRCODE)) {
                throw new BizException(ResultCode.BAD_REQUEST, "WeChat auth failed: " + json.get(WxApiConstants.FIELD_ERRMSG).asText());
            }

            String openid = json.get(WxApiConstants.FIELD_OPENID).asText();
            String accessToken = json.get(WxApiConstants.FIELD_ACCESS_TOKEN).asText();

            // 用 access_token 拉取微信用户信息（昵称、头像）
            String userInfoUrl = String.format(
                    "https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s",
                    accessToken, openid);
            String userInfoResponse = restClient.get().uri(userInfoUrl).retrieve().body(String.class);
            JsonNode userInfo = objectMapper.readTree(userInfoResponse);

            // 查询已有的微信绑定关系
            WxUserDO wxUser = wxUserMapper.selectOne(
                    new LambdaQueryWrapper<WxUserDO>().eq(WxUserDO::getOpenid, openid));

            Long userId;
            if (wxUser != null) {
                // 已绑定，直接使用关联的系统用户
                userId = wxUser.getUserId();
            } else {
                // 未绑定：先创建系统用户，再建立微信绑定关系
                String nickname = userInfo.has(WxApiConstants.FIELD_NICKNAME) ? userInfo.get(WxApiConstants.FIELD_NICKNAME).asText() : "wx_user";
                String avatar = userInfo.has(WxApiConstants.FIELD_HEADIMGURL) ? userInfo.get(WxApiConstants.FIELD_HEADIMGURL).asText() : null;

                UserDO user = new UserDO();
                user.setNickname(nickname);
                user.setAvatar(avatar);
                user.setStatus(CommonStatus.ENABLED);
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
            throw new BizException(ResultCode.INTERNAL_ERROR, "WeChat authentication failed");
        }
    }
}
```

注意：此文件引用了 `WxApiConstants`，但该类在 Task 6 才创建。**需要先完成 Task 6 再编译本 Task**，或者将 Task 6 的 Step 1-2 提前执行。

- [ ] **Step 5: 改造 UserServiceImpl**

全文替换为：

```java
package com.darkness.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.ServiceHelper;
import com.darkness.user.entity.UserDO;
import com.darkness.user.entity.UserRoleDO;
import com.darkness.user.mapper.UserMapper;
import com.darkness.user.mapper.UserRoleMapper;
import com.darkness.user.model.UserVO;
import com.darkness.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户业务实现层。基于 MyBatis-Plus 实现用户 CRUD 与角色分配逻辑，
 * 查询/更新/删除时校验记录是否存在，不存在则抛出 BizException(404)。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public UserVO getUserById(Long id) {
        UserDO user = ServiceHelper.findOrThrow(userMapper.selectById(id), "User", id);
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
        ServiceHelper.findOrThrow(userMapper.selectById(id), "User", id);
        UserDO entity = vo.toEntity();
        entity.setId(id);
        userMapper.updateById(entity);
        return UserVO.from(userMapper.selectById(id));
    }

    @Override
    public void deleteUser(Long id) {
        ServiceHelper.findOrThrow(userMapper.selectById(id), "User", id);
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

- [ ] **Step 6: 改造 RoleServiceImpl**

全文替换为：

```java
package com.darkness.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.common.util.ServiceHelper;
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

/**
 * 角色业务实现层。基于 MyBatis-Plus 实现角色 CRUD 与权限分配逻辑，
 * 查询/更新/删除时校验记录是否存在，不存在则抛出 BizException(404)。
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public RoleVO getRoleById(Long id) {
        return RoleVO.from(ServiceHelper.findOrThrow(roleMapper.selectById(id), "Role", id));
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
        ServiceHelper.findOrThrow(roleMapper.selectById(id), "Role", id);
        RoleDO entity = vo.toEntity();
        entity.setId(id);
        roleMapper.updateById(entity);
        return RoleVO.from(roleMapper.selectById(id));
    }

    @Override
    public void deleteRole(Long id) {
        ServiceHelper.findOrThrow(roleMapper.selectById(id), "Role", id);
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

- [ ] **Step 7: 改造 PermissionServiceImpl**

全文替换为：

```java
package com.darkness.user.service.impl;

import com.darkness.common.util.ServiceHelper;
import com.darkness.user.entity.PermissionDO;
import com.darkness.user.mapper.PermissionMapper;
import com.darkness.user.model.PermissionVO;
import com.darkness.user.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限业务实现层。基于 MyBatis-Plus 实现权限 CRUD 逻辑，
 * 查询/更新/删除时校验记录是否存在，不存在则抛出 BizException(404)。
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;

    @Override
    public PermissionVO getPermissionById(Long id) {
        return PermissionVO.from(ServiceHelper.findOrThrow(permissionMapper.selectById(id), "Permission", id));
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
        ServiceHelper.findOrThrow(permissionMapper.selectById(id), "Permission", id);
        PermissionDO entity = vo.toEntity();
        entity.setId(id);
        permissionMapper.updateById(entity);
        return PermissionVO.from(permissionMapper.selectById(id));
    }

    @Override
    public void deletePermission(Long id) {
        ServiceHelper.findOrThrow(permissionMapper.selectById(id), "Permission", id);
        permissionMapper.deleteById(id);
    }
}
```

- [ ] **Step 8: 改造 AgentServiceImpl**

全文替换为：

```java
package com.darkness.agent.service.impl;

import com.darkness.agent.constant.AgentConstants;
import com.darkness.agent.entity.AgentDO;
import com.darkness.agent.mapper.AgentMapper;
import com.darkness.agent.model.AgentVO;
import com.darkness.agent.service.AgentService;
import com.darkness.common.util.ServiceHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 业务服务实现类，处理 Agent 的增删改查逻辑及 API Key 脱敏回写。
 * <p>
 * 查询单条记录时，不存在则抛出 BizException(404)。更新时若前端脱敏回传掩码值
 * 或 apiKey 为 null，则保留数据库中的原始密钥不变，避免密钥被清空。
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;

    /**
     * 根据 ID 查询 Agent 配置。不存在时抛出 BizException(404)。
     * 返回的 AgentVO 中 apiKey 已脱敏。
     *
     * @param id Agent 主键
     * @return Agent 视图对象（apiKey 已脱敏）
     */
    @Override
    public AgentVO getAgentById(Long id) {
        return AgentVO.from(ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id));
    }

    /**
     * 查询所有 Agent 配置列表，按数据库默认顺序返回。
     *
     * @return Agent 视图对象列表
     */
    @Override
    public List<AgentVO> listAgents() {
        return agentMapper.selectList(null).stream().map(AgentVO::from).toList();
    }

    /**
     * 创建 Agent 配置。将 VO 转换为实体后插入 agent 表，返回包含自增主键的 VO。
     *
     * @param vo Agent 视图对象，包含名称、apiUrl、apiKey、model 等字段
     * @return 创建后的 Agent 视图对象（含自增 ID）
     */
    @Override
    public AgentVO createAgent(AgentVO vo) {
        AgentDO entity = vo.toEntity();
        agentMapper.insert(entity);
        return AgentVO.from(entity);
    }

    /**
     * 更新 Agent 配置。先校验 ID 存在（不存在抛 BizException(404)），
     * 若前端脱敏回传掩码值或 apiKey 为 null，则保留数据库中的原始密钥不变，
     * 避免 API Key 被意外清空。
     *
     * @param id Agent 主键
     * @param vo  Agent 视图对象，包含待更新字段
     * @return 更新后的 Agent 视图对象
     */
    @Override
    public AgentVO updateAgent(Long id, AgentVO vo) {
        AgentDO existing = ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id);
        AgentDO entity = vo.toEntity();
        entity.setId(id);
        if (entity.getApiKey() == null || AgentConstants.API_KEY_MASK.equals(entity.getApiKey())) {
            // 前端脱敏回传掩码值时保留原密钥不变
            entity.setApiKey(existing.getApiKey());
        }
        agentMapper.updateById(entity);
        return AgentVO.from(agentMapper.selectById(id));
    }

    /**
     * 删除 Agent 配置。先校验 ID 存在（不存在抛 BizException(404)），再执行物理删除。
     *
     * @param id Agent 主键
     */
    @Override
    public void deleteAgent(Long id) {
        ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id);
        agentMapper.deleteById(id);
    }
}
```

- [ ] **Step 9: 改造 ChatServiceImpl**

全文替换为：

```java
package com.darkness.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.agent.client.AgentClient;
import com.darkness.agent.entity.AgentDO;
import com.darkness.agent.entity.ConversationDO;
import com.darkness.agent.entity.MessageDO;
import com.darkness.agent.mapper.AgentMapper;
import com.darkness.agent.mapper.ConversationMapper;
import com.darkness.agent.mapper.MessageMapper;
import com.darkness.agent.model.ConversationVO;
import com.darkness.agent.model.MessageVO;
import com.darkness.agent.service.ChatService;
import com.darkness.common.enums.MessageRole;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.ServiceHelper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 对话服务实现类，管理会话的创建/删除、消息查询，以及 SSE 流式消息发送。
 * <p>
 * SSE 流式发送通过 AgentClient 调用外部 LLM API，在独立线程池中异步执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AgentMapper agentMapper;
    private final AgentClient agentClient;

    /** SSE 连接超时时间（毫秒），默认 5 分钟 */
    @Value("${chat.sse-timeout:300000}")
    private long sseTimeout;

    /** 异步线程池，用于执行 SSE 流式请求 */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 创建新会话。将用户与指定 Agent 关联，插入 conversation 表后返回 VO。
     *
     * @param userId  当前登录用户 ID
     * @param agentId 关联的 Agent ID
     * @param title   会话标题，前端传入
     * @return 创建后的会话视图对象
     */
    @Override
    public ConversationVO createConversation(Long userId, Long agentId, String title) {
        ConversationDO conv = new ConversationDO();
        conv.setUserId(userId);
        conv.setAgentId(agentId);
        conv.setTitle(title);
        conversationMapper.insert(conv);
        return ConversationVO.from(conv);
    }

    /**
     * 查询指定用户的所有会话，按创建时间倒序排列。
     *
     * @param userId 当前登录用户 ID
     * @return 会话视图对象列表
     */
    @Override
    public List<ConversationVO> listConversations(Long userId) {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<ConversationDO>()
                        .eq(ConversationDO::getUserId, userId)
                        .orderByDesc(ConversationDO::getCreatedAt)
        ).stream().map(ConversationVO::from).toList();
    }

    /**
     * 查询指定会话的消息列表，按创建时间正序排列。
     * 先校验会话存在且归属当前用户（防止 IDOR 越权），校验失败抛出 BizException(404/403)。
     *
     * @param conversationId 会话 ID
     * @param userId         当前登录用户 ID，用于归属校验
     * @return 消息视图对象列表
     */
    @Override
    public List<MessageVO> getMessages(Long conversationId, Long userId) {
        // 校验会话归属，防止 IDOR 越权读取他人消息
        ConversationDO conv = ServiceHelper.findOrThrow(conversationMapper.selectById(conversationId), "Conversation", conversationId);
        if (!conv.getUserId().equals(userId)) throw new BizException(ResultCode.FORBIDDEN, "Forbidden");

        return messageMapper.selectList(
                new LambdaQueryWrapper<MessageDO>()
                        .eq(MessageDO::getConversationId, conversationId)
                        .orderByAsc(MessageDO::getCreatedAt)
        ).stream().map(MessageVO::from).toList();
    }

    /**
     * 删除指定会话。先校验会话存在且归属当前用户（防止越权删除），校验失败抛出 BizException(404/403)。
     *
     * @param conversationId 会话 ID
     * @param userId         当前登录用户 ID，用于归属校验
     */
    @Override
    public void deleteConversation(Long conversationId, Long userId) {
        ConversationDO conv = ServiceHelper.findOrThrow(conversationMapper.selectById(conversationId), "Conversation", conversationId);
        if (!conv.getUserId().equals(userId)) throw new BizException(ResultCode.FORBIDDEN, "Forbidden");
        conversationMapper.deleteById(conversationId);
    }

    /**
     * 通过 SSE 流式发送用户消息给 AI Agent。
     * <p>
     * 流程：
     * 1. 校验会话归属（防止越权）和 Agent 存在性；
     * 2. 持久化用户消息到 message 表；
     * 3. 查询会话完整历史作为 LLM 上下文；
     * 4. 创建 SseEmitter（超时时间由 chat.sse-timeout 配置，默认 5 分钟），
     *    在异步线程池中调用 AgentClient.stream() 逐 token 推送；
     * 5. 流结束后持久化助手回复并完成 SseEmitter。
     *
     * @param userId         当前登录用户 ID
     * @param conversationId 会话 ID
     * @param content        用户输入的消息内容
     * @return SseEmitter 实例，前端通过 EventSource 接收流式数据
     */
    @Override
    public SseEmitter sendMessage(Long userId, Long conversationId, String content) {
        // 校验会话归属
        ConversationDO conv = ServiceHelper.findOrThrow(conversationMapper.selectById(conversationId), "Conversation", conversationId);
        if (!conv.getUserId().equals(userId)) throw new BizException(ResultCode.FORBIDDEN, "Forbidden");

        ServiceHelper.findOrThrow(agentMapper.selectById(conv.getAgentId()), "Agent", conv.getAgentId());

        // 持久化用户消息
        MessageDO userMsg = new MessageDO();
        userMsg.setConversationId(conversationId);
        userMsg.setRole(MessageRole.USER);
        userMsg.setContent(content);
        messageMapper.insert(userMsg);

        // 查询当前会话的完整消息历史作为 LLM 上下文
        List<MessageDO> history = messageMapper.selectList(
                new LambdaQueryWrapper<MessageDO>()
                        .eq(MessageDO::getConversationId, conversationId)
                        .orderByAsc(MessageDO::getCreatedAt));

        SseEmitter emitter = new SseEmitter(sseTimeout);

        executor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            try {
                agentClient.stream(agentMapper.selectById(conv.getAgentId()), history, content)
                        .doOnNext(chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                                fullResponse.append(chunk);
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(() -> {
                            // 持久化助手回复
                            MessageDO assistantMsg = new MessageDO();
                            assistantMsg.setConversationId(conversationId);
                            assistantMsg.setRole(MessageRole.ASSISTANT);
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

    /**
     * 应用关闭时优雅关闭 SSE 异步线程池。
     * 先调用 shutdown() 停止接受新任务，等待 10 秒让已提交任务完成，
     * 超时后调用 shutdownNow() 强制中断，最后恢复中断状态。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 10: 改造 SecurityConfig**

将 `SecurityConfig.java` 中第 70 行的 `Result.error(401, "Unauthorized")` 改为 `Result.error(ResultCode.UNAUTHORIZED, "Unauthorized")`，并增加 import：

```java
import com.darkness.common.result.ResultCode;
```

变更行：
```java
// 原: objectMapper.writeValueAsString(Result.error(401, "Unauthorized")));
// 改:
objectMapper.writeValueAsString(Result.error(ResultCode.UNAUTHORIZED, "Unauthorized")));
```

- [ ] **Step 11: 编译验证**

Run: `cd D:\ideaproject\acgAgent && mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 12: 提交**

```bash
git add src/main/java/com/darkness/common/util/ServiceHelper.java src/main/java/com/darkness/auth/service/impl/ src/main/java/com/darkness/user/service/impl/ src/main/java/com/darkness/agent/service/impl/ src/main/java/com/darkness/config/SecurityConfig.java
git commit -m "refactor: replace magic values with ResultCode/ServiceHelper in all ServiceImpl and SecurityConfig"
```

---

### Task 5: 改造 JwtUtil（TokenType） + JwtAuthenticationFilter（AuthConstants） + AgentClient（SseConstants + AuthConstants + MessageRole） + AgentVO（AgentConstants）

**Files:**
- Create: `src/main/java/com/darkness/common/constant/AuthConstants.java`
- Create: `src/main/java/com/darkness/agent/constant/SseConstants.java`
- Create: `src/main/java/com/darkness/agent/constant/AgentConstants.java`
- Modify: `src/main/java/com/darkness/auth/util/JwtUtil.java`
- Modify: `src/main/java/com/darkness/auth/filter/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/darkness/agent/client/AgentClient.java`
- Modify: `src/main/java/com/darkness/agent/model/AgentVO.java`

- [ ] **Step 1: 创建 AuthConstants**

```java
package com.darkness.common.constant;

/**
 * 认证相关常量。
 */
public final class AuthConstants {
    private AuthConstants() {}

    /** Authorization header 中 Bearer Token 的前缀。 */
    public static final String BEARER_PREFIX = "Bearer ";
}
```

- [ ] **Step 2: 创建 SseConstants**

```java
package com.darkness.agent.constant;

/**
 * SSE 流式响应相关常量，用于 AgentClient 解析 LLM API 的 SSE 协议。
 */
public final class SseConstants {
    private SseConstants() {}

    /** SSE data 行前缀。 */
    public static final String DATA_PREFIX = "data:";

    /** SSE 流结束标记。 */
    public static final String DONE_MARKER = "[DONE]";
}
```

- [ ] **Step 3: 创建 AgentConstants**

```java
package com.darkness.agent.constant;

/**
 * Agent 模块常量。
 */
public final class AgentConstants {
    private AgentConstants() {}

    /** API Key 脱敏掩码，用于返回给前端时隐藏真实密钥。 */
    public static final String API_KEY_MASK = "******";
}
```

- [ ] **Step 4: 改造 JwtUtil — 应用 TokenType**

将 `JwtUtil.java` 全文替换为：

```java
package com.darkness.auth.util;

import com.darkness.common.enums.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌工具类，负责 Access Token 和 Refresh Token 的签发、解析与校验。
 * <p>
 * 使用 HMAC-SHA 算法签名，密钥来自配置项 jwt.secret。
 * Token 结构：header.payload.signature，payload 中包含 subject（userId）、type（access/refresh）、签发时间和过期时间。
 * </p>
 * <ul>
 *   <li>accessToken — 短期令牌，默认 2 小时有效，用于接口鉴权</li>
 *   <li>refreshToken — 长期令牌，默认 7 天有效，仅用于刷新 accessToken</li>
 * </ul>
 */
@Component
public class JwtUtil {

    /** JWT 签名密钥，对应配置项 jwt.secret，至少 32 字节以满足 HMAC-SHA 要求 */
    @Value("${jwt.secret}")
    private String secret;

    /** accessToken 过期时间，单位毫秒，对应配置项 jwt.access-token-expiration，默认 2 小时 */
    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    /** refreshToken 过期时间，单位毫秒，对应配置项 jwt.refresh-token-expiration，默认 7 天 */
    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * 根据配置的 secret 构建 HMAC-SHA 签名密钥。
     *
     * @return SecretKey 实例
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 accessToken。
     * payload 中 subject 为 userId，type 声明为 ACCESS，签发时间为当前时间，
     * 过期时间为当前时间 + accessTokenExpiration。
     *
     * @param userId 用户 ID
     * @return 签名后的 JWT 字符串
     */
    public String generateAccessToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", TokenType.ACCESS.getValue())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成 refreshToken。
     * payload 中 subject 为 userId，type 声明为 REFRESH，签发时间为当前时间，
     * 过期时间为当前时间 + refreshTokenExpiration。
     *
     * @param userId 用户 ID
     * @return 签名后的 JWT 字符串
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", TokenType.REFRESH.getValue())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 JWT 令牌，返回 Claims 载荷。
     * 验证签名和过期时间，签名不匹配或已过期时抛出异常。
     *
     * @param token JWT 字符串
     * @return Claims 载荷，包含 subject、type、iat、exp 等声明
     * @throws io.jsonwebtoken.JwtException 签名无效或令牌过期时抛出
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从令牌中提取用户 ID。
     * 直接解析 token 的 subject 字段并转换为 Long。
     *
     * @param token JWT 字符串
     * @return 用户 ID
     */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    /**
     * 校验令牌是否有效（签名合法且未过期）。
     * 内部调用 parseToken，捕获所有异常后返回 false。
     *
     * @param token JWT 字符串
     * @return true 表示令牌有效，false 表示无效或已过期
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断令牌是否为 refreshToken 类型。
     * 解析 token 的 type 声明，值为 REFRESH 时返回 true。
     *
     * @param token JWT 字符串
     * @return true 表示是 refreshToken，false 表示是 accessToken
     */
    public boolean isRefreshToken(String token) {
        return TokenType.REFRESH.getValue().equals(parseToken(token).get("type", String.class));
    }
}
```

- [ ] **Step 5: 改造 JwtAuthenticationFilter — 应用 AuthConstants**

将 `JwtAuthenticationFilter.java` 全文替换为：

```java
package com.darkness.auth.filter;

import com.darkness.auth.model.LoginUserDetails;
import com.darkness.auth.util.JwtUtil;
import com.darkness.common.constant.AuthConstants;
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

/**
 * JWT 认证过滤器，从请求头 Authorization 中提取 Bearer Token，
 * 解析成功后将用户身份写入 Spring Security 上下文。
 * <p>
 * 过滤逻辑：
 * <ol>
 *   <li>从请求头 Authorization 中提取 Bearer Token（截取 "Bearer " 之后的部分）</li>
 *   <li>调用 JwtUtil 校验令牌有效性（签名 + 过期时间）</li>
 *   <li>解析出 userId，构造 LoginUserDetails 并写入 SecurityContext</li>
 *   <li>无论 Token 是否存在或有效，都继续执行后续过滤器链</li>
 * </ol>
 * 在 JWT 无密码模式下仅用 userId 做身份标识，不需要密码和权限。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    /**
     * 执行 JWT 认证过滤。
     * 从 Authorization 头提取 Bearer Token，校验通过后构造认证对象写入 SecurityContext。
     * 无论 Token 是否存在或有效，最终都会调用 filterChain.doFilter 继续后续处理。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(AuthConstants.BEARER_PREFIX)) {
            String token = header.substring(AuthConstants.BEARER_PREFIX.length());
            if (jwtUtil.isTokenValid(token)) {
                Long userId = jwtUtil.getUserId(token);
                // JWT 无密码模式下仅用 userId 做身份标识，不需要密码和权限
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

- [ ] **Step 6: 改造 AgentClient — 应用 SseConstants + AuthConstants + MessageRole**

将 `AgentClient.java` 全文替换为：

```java
package com.darkness.agent.client;

import com.darkness.agent.constant.SseConstants;
import com.darkness.agent.entity.AgentDO;
import com.darkness.agent.entity.MessageDO;
import com.darkness.common.constant.AuthConstants;
import com.darkness.common.enums.MessageRole;
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
import java.util.stream.Collectors;

/**
 * AI Agent 远程调用客户端，通过 SSE 流式协议与 AI 模型 API 通信。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentClient {

    /** JSON 序列化/反序列化工具，用于解析 SSE chunk 中的 JSON 数据提取 delta 内容 */
    private final ObjectMapper objectMapper;

    /**
     * 以流式方式调用 AI 模型 API，逐 token 返回生成内容。
     * 请求格式遵循 OpenAI ChatCompletion 兼容协议（model + messages + stream=true），
     * 通过 SSE 协议接收响应，逐行解析 data 前缀的 JSON chunk，从每个 chunk 的
     * choices[0].delta.content 路径提取增量文本，跳过结束标记和非法行，
     * 最终以 Flux 流形式返回拼接后的完整文本。
     *
     * @param agent       Agent 配置（含 apiUrl、apiKey、model）
     * @param history     历史消息列表，用于构建上下文
     * @param userMessage 当前用户输入
     * @return Flux 流，每次发射一段增量文本内容
     */
    public Flux<String> stream(AgentDO agent, List<MessageDO> history, String userMessage) {
        List<Map<String, String>> messages = history.stream()
                .map(m -> Map.of("role", m.getRole().getValue(), "content", m.getContent()))
                .collect(Collectors.toList());
        messages.add(Map.of("role", MessageRole.USER.getValue(), "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model", agent.getModel());
        body.put("messages", messages);
        body.put("stream", true);

        return WebClient.create(agent.getApiUrl())
                .post()
                .header("Authorization", AuthConstants.BEARER_PREFIX + agent.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                // 过滤空行和非数据行，跳过结束标记
                .filter(line -> !line.isBlank() && line.startsWith(SseConstants.DATA_PREFIX))
                .map(line -> line.substring(SseConstants.DATA_PREFIX.length()).trim())
                .filter(data -> !SseConstants.DONE_MARKER.equals(data))
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

- [ ] **Step 7: 改造 AgentVO — 应用 AgentConstants**

将 `AgentVO.java` 中 `"******"` 替换为 `AgentConstants.API_KEY_MASK`。

变更点（`from()` 方法中）：
```java
import com.darkness.agent.constant.AgentConstants;
// ...
vo.setApiKey(entity.getApiKey() != null ? AgentConstants.API_KEY_MASK : null);
```

- [ ] **Step 8: 编译验证**

Run: `cd D:\ideaproject\acgAgent && mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/darkness/common/constant/AuthConstants.java src/main/java/com/darkness/agent/constant/ src/main/java/com/darkness/auth/util/JwtUtil.java src/main/java/com/darkness/auth/filter/JwtAuthenticationFilter.java src/main/java/com/darkness/agent/client/AgentClient.java src/main/java/com/darkness/agent/model/AgentVO.java
git commit -m "refactor: add AuthConstants/SseConstants/AgentConstants, apply TokenType and MessageRole to JwtUtil/AgentClient"
```

---

### Task 6: 创建微信 API 常量 + 改造 ChatController（DTO + ResultCode）

**Files:**
- Create: `src/main/java/com/darkness/auth/constant/WxApiConstants.java`
- Modify: `src/main/java/com/darkness/agent/controller/ChatController.java`

注：`WxApiConstants` 已在 Task 4 Step 4 的 `WxAuthServiceImpl` 中引用，需先创建此类。

- [ ] **Step 1: 创建 WxApiConstants**

```java
package com.darkness.auth.constant;

/**
 * 微信开放平台 API 相关常量。
 */
public final class WxApiConstants {
    private WxApiConstants() {}

    /** 微信授权码 grant type。 */
    public static final String GRANT_TYPE_AUTH_CODE = "authorization_code";

    /** 微信 JSON 响应字段名。 */
    public static final String FIELD_ERRCODE = "errcode";
    public static final String FIELD_ERRMSG = "errmsg";
    public static final String FIELD_OPENID = "openid";
    public static final String FIELD_ACCESS_TOKEN = "access_token";
    public static final String FIELD_NICKNAME = "nickname";
    public static final String FIELD_HEADIMGURL = "headimgurl";
}
```

- [ ] **Step 2: 改造 ChatController — DTO + ResultCode**

将 `ChatController.java` 全文替换为：

```java
package com.darkness.agent.controller;

import com.darkness.agent.model.ConversationVO;
import com.darkness.agent.model.CreateConversationRequest;
import com.darkness.agent.model.MessageVO;
import com.darkness.agent.model.SendMessageRequest;
import com.darkness.agent.service.ChatService;
import com.darkness.auth.model.LoginUserDetails;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.Result;
import com.darkness.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话控制器，管理会话的创建/删除/查询，以及通过 SSE 流式发送消息给 Agent。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 创建新会话，将用户与指定 Agent 关联。
     * POST /api/chat/conversations（需认证）
     *
     * @param user    当前登录用户（由 JWT Filter 注入）
     * @param request 请求体，包含 agentId（必填）和 title（可选）
     * @return 创建后的会话视图对象
     */
    @PostMapping("/conversations")
    public Result<ConversationVO> createConversation(
            @AuthenticationPrincipal LoginUserDetails user,
            @RequestBody CreateConversationRequest request) {
        return Result.success(chatService.createConversation(user.getUserId(), request.getAgentId(), request.getTitle()));
    }

    /**
     * 查询当前用户的所有会话，按创建时间倒序排列。
     * GET /api/chat/conversations（需认证）
     *
     * @param user 当前登录用户
     * @return 会话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations(
            @AuthenticationPrincipal LoginUserDetails user) {
        return Result.success(chatService.listConversations(user.getUserId()));
    }

    /**
     * 查询指定会话的消息列表。
     * GET /api/chat/conversations/{id}/messages（需认证）
     * 已在 Service 层校验会话归属，防止越权访问他人消息。
     *
     * @param user 当前登录用户（由 JWT Filter 注入）
     * @param id   会话主键
     * @return 消息列表，按创建时间正序排列
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageVO>> getMessages(
            @AuthenticationPrincipal LoginUserDetails user,
            @PathVariable Long id) {
        return Result.success(chatService.getMessages(id, user.getUserId()));
    }

    /**
     * SSE 流式发送消息。
     * POST /api/chat/conversations/{id}/send（需认证）
     * 此端点返回 SseEmitter 而非 Result&lt;T&gt;，因为 SSE 需要保持长连接持续推送数据，无法用统一响应体包装。
     *
     * @param user    当前登录用户（由 JWT Filter 注入）
     * @param id      会话主键
     * @param request 请求体，包含 content（用户输入的消息内容）
     * @return SseEmitter 实例，前端通过 EventSource 接收流式数据
     */
    @PostMapping("/conversations/{id}/send")
    public SseEmitter sendMessage(
            @AuthenticationPrincipal LoginUserDetails user,
            @PathVariable Long id,
            @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(user.getUserId(), id, request.getContent());
    }

    /**
     * 逻辑删除会话，需校验会话归属当前用户，非本人会话返回 403。
     * DELETE /api/chat/conversations/{id}（需认证）
     *
     * @param user 当前登录用户
     * @param id   会话主键
     */
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(
            @AuthenticationPrincipal LoginUserDetails user,
            @PathVariable Long id) {
        chatService.deleteConversation(id, user.getUserId());
        return Result.success();
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd D:\ideaproject\acgAgent && mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/darkness/auth/constant/WxApiConstants.java src/main/java/com/darkness/agent/controller/ChatController.java
git commit -m "refactor: add WxApiConstants, replace ChatController Map with DTO"
```

---

### Task 7: 创建请求 DTO 类 + 改造 AuthController

**Files:**
- Create: `src/main/java/com/darkness/auth/model/LoginRequest.java`
- Create: `src/main/java/com/darkness/auth/model/RegisterRequest.java`
- Create: `src/main/java/com/darkness/auth/model/RefreshTokenRequest.java`
- Create: `src/main/java/com/darkness/auth/model/SmsSendRequest.java`
- Create: `src/main/java/com/darkness/auth/model/SmsLoginRequest.java`
- Create: `src/main/java/com/darkness/auth/model/WxLoginRequest.java`
- Create: `src/main/java/com/darkness/agent/model/CreateConversationRequest.java`
- Create: `src/main/java/com/darkness/agent/model/SendMessageRequest.java`
- Modify: `src/main/java/com/darkness/auth/controller/AuthController.java`

- [ ] **Step 1: 创建 LoginRequest**

```java
package com.darkness.auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 账号密码登录请求。
 */
@Data
public class LoginRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
```

- [ ] **Step 2: 创建 RegisterRequest**

```java
package com.darkness.auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 账号密码注册请求。
 */
@Data
public class RegisterRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
```

- [ ] **Step 3: 创建 RefreshTokenRequest**

```java
package com.darkness.auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求。
 */
@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
```

- [ ] **Step 4: 创建 SmsSendRequest**

```java
package com.darkness.auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送短信验证码请求。
 */
@Data
public class SmsSendRequest {
    @NotBlank(message = "Phone number is required")
    private String phone;
}
```

- [ ] **Step 5: 创建 SmsLoginRequest**

```java
package com.darkness.auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 短信验证码登录请求。
 */
@Data
public class SmsLoginRequest {
    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "Verification code is required")
    private String code;
}
```

- [ ] **Step 6: 创建 WxLoginRequest**

```java
package com.darkness.auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信扫码登录回调请求。
 */
@Data
public class WxLoginRequest {
    @NotBlank(message = "Authorization code is required")
    private String code;
}
```

- [ ] **Step 7: 创建 CreateConversationRequest**

```java
package com.darkness.agent.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求。
 */
@Data
public class CreateConversationRequest {
    @NotNull(message = "agentId is required")
    private Long agentId;

    /** 会话标题，可选 */
    private String title;
}
```

- [ ] **Step 8: 创建 SendMessageRequest**

```java
package com.darkness.agent.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SSE 流式发送消息请求。
 */
@Data
public class SendMessageRequest {
    @NotBlank(message = "content is required")
    private String content;
}
```

- [ ] **Step 9: 改造 AuthController — DTO 替代 Map**

将 `AuthController.java` 全文替换为：

```java
package com.darkness.auth.controller;

import com.darkness.auth.model.*;
import com.darkness.auth.service.AuthService;
import com.darkness.auth.service.SmsService;
import com.darkness.auth.service.WxAuthService;
import com.darkness.common.result.Result;
import com.darkness.user.model.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器，提供账号密码注册/登录、令牌刷新、短信验证码登录和微信扫码登录入口。
 * 所有接口路径以 /api/auth 为前缀，返回值统一使用 {@link Result} 包装。
 * 请求体均为 JSON 格式（application/json），通过专用 DTO 类接收参数。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SmsService smsService;
    private final WxAuthService wxAuthService;

    /**
     * 账号密码注册。
     * 请求体需包含 username（用户名）和 password（明文密码），不能为空。
     * 服务端校验用户名唯一性后使用 BCrypt 加密密码并创建用户。
     *
     * @param request 注册请求（username、password 必填）
     * @return 注册成功的用户信息（UserVO）
     */
    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody @Valid RegisterRequest request) {
        return Result.success(authService.register(request.getUsername(), request.getPassword()));
    }

    /**
     * 账号密码登录。
     * 请求体需包含 username 和 password。校验通过后返回 accessToken 和 refreshToken。
     *
     * @param request 登录请求（username、password 必填）
     * @return Token 对，包含 accessToken、refreshToken 和 expiresIn
     */
    @PostMapping("/login")
    public Result<TokenVO> login(@RequestBody @Valid LoginRequest request) {
        return Result.success(authService.login(request.getUsername(), request.getPassword()));
    }

    /**
     * 使用 refreshToken 刷新令牌。
     * 请求体需包含 refreshToken，服务端校验其有效性和类型后重新签发 Token 对。
     *
     * @param request 刷新令牌请求（refreshToken 必填）
     * @return 新的 Token 对
     */
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return Result.success(authService.refresh(request.getRefreshToken()));
    }

    /**
     * 发送短信验证码。
     * 请求体需包含 phone（手机号）。服务端生成 6 位验证码并发送，
     * mock 模式下仅输出到日志。成功返回空数据。
     *
     * @param request 发送短信请求（phone 必填）
     * @return 空结果
     */
    @PostMapping("/sms/send")
    public Result<Void> sendSmsCode(@RequestBody @Valid SmsSendRequest request) {
        smsService.sendCode(request.getPhone());
        return Result.success();
    }

    /**
     * 短信验证码登录。
     * 请求体需包含 phone 和 code。校验验证码有效后登录，
     * 手机号未注册时自动创建账号。返回 Token 对。
     *
     * @param request 短信登录请求（phone、code 必填）
     * @return Token 对
     */
    @PostMapping("/sms/login")
    public Result<TokenVO> smsLogin(@RequestBody @Valid SmsLoginRequest request) {
        return Result.success(smsService.login(request.getPhone(), request.getCode()));
    }

    /**
     * 获取微信扫码登录二维码 URL。
     * 无需请求参数。返回微信 OAuth2 授权链接和防 CSRF 的 state 参数。
     *
     * @return 包含 url（二维码地址）和 state（防 CSRF 随机串）
     */
    @PostMapping("/wx/qrcode")
    public Result<Map<String, String>> wxQrcode() {
        return Result.success(wxAuthService.generateQrcode());
    }

    /**
     * 微信扫码回调处理。
     * 请求体需包含 code（微信返回的授权码）。服务端用授权码换取 access_token 和 openid，
     * 未绑定时自动注册系统用户。返回 Token 对。
     *
     * @param request 微信登录请求（code 必填）
     * @return Token 对
     */
    @PostMapping("/wx/callback")
    public Result<TokenVO> wxCallback(@RequestBody @Valid WxLoginRequest request) {
        return Result.success(wxAuthService.handleCallback(request.getCode()));
    }
}
```

- [ ] **Step 10: 编译验证**

Run: `cd D:\ideaproject\acgAgent && mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 11: 提交**

```bash
git add src/main/java/com/darkness/auth/model/LoginRequest.java src/main/java/com/darkness/auth/model/RegisterRequest.java src/main/java/com/darkness/auth/model/RefreshTokenRequest.java src/main/java/com/darkness/auth/model/SmsSendRequest.java src/main/java/com/darkness/auth/model/SmsLoginRequest.java src/main/java/com/darkness/auth/model/WxLoginRequest.java src/main/java/com/darkness/agent/model/CreateConversationRequest.java src/main/java/com/darkness/agent/model/SendMessageRequest.java src/main/java/com/darkness/auth/controller/AuthController.java
git commit -m "feat: add request DTOs with JSR 303 validation, replace Map<String,String> in AuthController/ChatController"
```

---

### Task 8: 全量编译验证

- [ ] **Step 1: 全量编译**

Run: `cd D:\ideaproject\acgAgent && mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 打包验证**

Run: `cd D:\ideaproject\acgAgent && mvn clean package -DskipTests -q`
Expected: BUILD SUCCESS

---

### Task 9: 创建变更日志

**Files:**
- Create: `docs/changelogs/2026-05-23-enum-constants-utility.md`

- [ ] **Step 1: 创建变更日志**

```markdown
# 枚举、魔法值与工具类优化

> 日期：2026-05-23

## 变更原因

项目中所有业务状态码、类型标识、错误消息均以裸数字或字符串字面量硬编码在代码中，缺少枚举定义、常量管理和公共工具类，影响代码可读性和可维护性。

## 变更范围

### 新增包/模块

- `com.darkness.common.enums` — 5 个业务枚举（CommonStatus, MessageRole, PermissionType, TokenType, UsedStatus）
- `com.darkness.common.constant` — AuthConstants
- `com.darkness.common.util` — ServiceHelper
- `com.darkness.agent.constant` — SseConstants, AgentConstants
- `com.darkness.auth.constant` — WxApiConstants

### 新增文件

- `common/result/ResultCode.java` — 统一响应状态码枚举
- `common/enums/CommonStatus.java`, `MessageRole.java`, `PermissionType.java`, `TokenType.java`, `UsedStatus.java`
- `common/util/ServiceHelper.java`
- `common/constant/AuthConstants.java`
- `agent/constant/SseConstants.java`, `AgentConstants.java`
- `auth/constant/WxApiConstants.java`
- `auth/model/LoginRequest.java`, `RegisterRequest.java`, `RefreshTokenRequest.java`, `SmsSendRequest.java`, `SmsLoginRequest.java`, `WxLoginRequest.java`
- `agent/model/CreateConversationRequest.java`, `SendMessageRequest.java`

### 修改文件（共 22 个）

**common 层：** Result, BizException, GlobalExceptionHandler
**config 层：** SecurityConfig
**auth 层：** JwtUtil, JwtAuthenticationFilter, AuthController, AuthServiceImpl, SmsServiceImpl, WxAuthServiceImpl, SmsCodeDO
**user 层：** UserDO, RoleDO, PermissionDO, UserVO, RoleVO, PermissionVO, UserServiceImpl, RoleServiceImpl, PermissionServiceImpl
**agent 层：** AgentDO, MessageDO, AgentVO, MessageVO, AgentServiceImpl, ChatServiceImpl, ChatController, AgentClient

## 变更前后对比

| 方面 | 变更前 | 变更后 |
|------|--------|--------|
| 状态码 | 裸数字 `throw new BizException(404, ...)` | `throw new BizException(ResultCode.NOT_FOUND, ...)` |
| status 字段 | `Integer` (0/1) | `CommonStatus` 枚举 |
| Message role | `String` ("user"/"assistant") | `MessageRole` 枚举 |
| 请求参数 | `Map<String, String>` + 手动校验 | 专用 DTO + `@Valid` + JSR 303 |
| null 检查 | 15 处重复 if-null-throw | `ServiceHelper.findOrThrow()` |
| Bearer 前缀 | `"Bearer "` + `substring(7)` | `AuthConstants.BEARER_PREFIX` + `.length()` |

## 需要注意的点

1. **数据库无变更** — 所有枚举通过 `@EnumValue` 映射到现有值，表结构和数据不受影响。
2. **API 接口兼容** — 请求体 JSON 字段名不变，只是增加了 JSR 303 校验。校验失败返回 400 + 具体字段错误信息。
3. **MyBatis-Plus 枚举** — `@EnumValue` 在 3.5.x 版本中默认生效，无需额外配置。
4. **无新增依赖** — JSR 303 校验注解已包含在 spring-boot-starter-web 中。
```

- [ ] **Step 2: 提交**

```bash
git add docs/changelogs/2026-05-23-enum-constants-utility.md
git commit -m "docs: add changelog for enum/constants/utility refactoring"
```

---

## 执行顺序说明

Task 1-3 相互独立，可并行。Task 4 依赖 Task 1（ResultCode）和 Task 2（枚举）和 Task 3（实体字段类型）。Task 5 依赖 Task 2（TokenType、MessageRole）和 Task 3。Task 6 依赖 Task 1（ResultCode）。Task 7 独立。Task 8 需等待所有 Task 完成。Task 9 最后执行。

推荐执行顺序：**1 → 2 → 3 → 4 + 5 + 6 + 7（可并行）→ 8 → 9**
