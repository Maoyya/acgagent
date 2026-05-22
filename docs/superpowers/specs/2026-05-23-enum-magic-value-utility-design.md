# 枚举、魔法值与工具类优化设计

> 日期：2026-05-23
> 状态：待实现

## 背景

项目现有 61 个 Java 源文件，所有业务状态码、类型标识、错误消息均以裸数字或字符串字面量直接硬编码在代码中，缺少枚举定义、常量管理和公共工具类。本次优化旨在消除魔法值、建立枚举体系、提取公共工具方法，提升代码可读性和可维护性。

## 改动范围

共 9 项改动，涉及 5 个模块。

---

## 模块 1：ResultCode 枚举

**包路径：** `com.darkness.common.result.ResultCode`

```java
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

### 影响文件及变更

| 文件 | 变更 |
|------|------|
| `Result` | `success()` 和 `error()` 方法改为基于 `ResultCode` 构造 |
| `BizException` | 增加 `BizException(ResultCode, String)` 构造方法；默认构造使用 `ResultCode.INTERNAL_ERROR` |
| `GlobalExceptionHandler` | 引用 `ResultCode.INTERNAL_ERROR` |
| `SecurityConfig` | 引用 `ResultCode.UNAUTHORIZED` |
| `AuthServiceImpl` | 所有 `throw new BizException(400, ...)` 改为 `throw new BizException(ResultCode.BAD_REQUEST, ...)`；同理 401 |
| `SmsServiceImpl` | 400/401 替换 |
| `WxAuthServiceImpl` | 400/500 替换 |
| `ChatServiceImpl` | 404/403 替换 |
| `ChatController` | 400 替换 |
| `AgentServiceImpl` | 404 替换 |
| `UserServiceImpl` | 404 替换 |
| `RoleServiceImpl` | 404 替换 |
| `PermissionServiceImpl` | 404 替换 |

**总计替换：** 38 处裸数字。

---

## 模块 2：业务枚举

**包路径：** `com.darkness.common.enums`

所有枚举使用 `@EnumValue` 注解标记实际存储值，MyBatis-Plus 自动处理类型转换。

### 2.1 CommonStatus

```java
/** 通用启用/禁用状态枚举，用于 User、Role、Permission、Agent 的 status 字段。 */
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

**影响：** UserDO/VO、RoleDO/VO、PermissionDO/VO、AgentDO/VO 的 `status` 字段类型从 `Integer` 改为 `CommonStatus`；AuthServiceImpl、SmsServiceImpl、WxAuthServiceImpl 中 `setStatus(1)` 改为 `setStatus(CommonStatus.ENABLED)`。

### 2.2 MessageRole

```java
/** 聊天消息角色枚举，用于 MessageDO 的 role 字段。 */
@Getter
@AllArgsConstructor
public enum MessageRole {
    USER("user"),
    ASSISTANT("assistant");

    @EnumValue
    private final String value;
}
```

**影响：** MessageDO/VO 的 `role` 字段从 `String` 改为 `MessageRole`；ChatServiceImpl 中 `setRole("user")` 改为 `setRole(MessageRole.USER)`；AgentClient 中构建 LLM 请求时用 `MessageRole.USER.getValue()`。

### 2.3 PermissionType

```java
/** 权限类型枚举，用于 PermissionDO 的 type 字段。 */
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

**影响：** PermissionDO/VO 的 `type` 字段从 `Integer` 改为 `PermissionType`。

### 2.4 TokenType

```java
/** JWT Token 类型枚举，用于 JwtUtil 中区分 access/refresh 令牌。 */
@Getter
@AllArgsConstructor
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String value;
}
```

**影响：** JwtUtil 中 `"access"` 和 `"refresh"` 字符串字面量替换为 `TokenType.ACCESS.getValue()` 和 `TokenType.REFRESH.getValue()`；`"refresh".equals(...)` 改为 `TokenType.REFRESH.getValue().equals(...)`。

### 2.5 UsedStatus

```java
/** 验证码使用状态枚举，用于 SmsCodeDO 的 used 字段。 */
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

**影响：** SmsCodeDO 的 `used` 字段从 `Integer` 改为 `UsedStatus`；SmsServiceImpl 中 `setUsed(0)` / `.eq(..., 0)` / `.set(..., 1)` 替换为枚举引用。

---

## 模块 3：请求 DTO

用专用 DTO 类 + JSR 303 校验替代 Controller 中的 `Map<String, String>` + 手动空值检查。

### 3.1 Auth 模块 DTO（`com.darkness.auth.model`）

| DTO 类 | 字段 | 校验 |
|--------|------|------|
| `LoginRequest` | `username`, `password` | `@NotBlank` |
| `RegisterRequest` | `username`, `password` | `@NotBlank` |
| `RefreshTokenRequest` | `refreshToken` | `@NotBlank` |
| `SmsSendRequest` | `phone` | `@NotBlank` |
| `SmsLoginRequest` | `phone`, `code` | `@NotBlank` |
| `WxLoginRequest` | `code` | `@NotBlank` |

**示例：**
```java
/** 用户登录请求。 */
@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
```

### 3.2 Agent 模块 DTO（`com.darkness.agent.model`）

| DTO 类 | 字段 | 校验 |
|--------|------|------|
| `CreateConversationRequest` | `agentId`, `title` | agentId `@NotNull` |
| `SendMessageRequest` | `content` | `@NotBlank` |

### Controller 变更

- `@RequestBody Map<String, String>` 改为 `@RequestBody @Valid XxxRequest`
- 移除 Controller/Service 中的手动空值校验逻辑（`@Valid` + `@NotBlank` 已覆盖）
- `@Valid` 校验失败由 `GlobalExceptionHandler` 新增 `MethodArgumentNotValidException` 处理

### GlobalExceptionHandler 扩展

新增处理 JSR 303 校验异常：

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public Result<Void> handleValidation(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining("; "));
    return Result.error(ResultCode.BAD_REQUEST, message);
}
```

---

## 模块 4：ServiceHelper 工具类

**包路径：** `com.darkness.common.util.ServiceHelper`

```java
/**
 * Service 层公共工具方法。
 * 提供实体查询不存在时统一抛异常的模式。
 */
public class ServiceHelper {

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

### 替换模式

**替换前（15 处）：**
```java
UserDO user = userMapper.selectById(id);
if (user == null) throw new BizException(404, "User not found: " + id);
```

**替换后：**
```java
UserDO user = ServiceHelper.findOrThrow(userMapper.selectById(id), "User", id);
```

**影响文件：** UserServiceImpl（3 处）、RoleServiceImpl（3 处）、PermissionServiceImpl（3 处）、AgentServiceImpl（3 处）、ChatServiceImpl（4 处，其中 1 处是 Agent 查询）。

---

## 模块 5：常量定义

### 5.1 Bearer 前缀常量

**放置：** `com.darkness.common.constant.AuthConstants`

```java
/** 认证相关常量。 */
public final class AuthConstants {
    private AuthConstants() {}

    /** Authorization header 中 Bearer Token 的前缀。 */
    public static final String BEARER_PREFIX = "Bearer ";
}
```

**替换：**
- `JwtAuthenticationFilter`：`header.startsWith("Bearer ")` → `header.startsWith(AuthConstants.BEARER_PREFIX)`
- `JwtAuthenticationFilter`：`header.substring(7)` → `header.substring(AuthConstants.BEARER_PREFIX.length())`
- `AgentClient`：`"Bearer " + agent.getApiKey()` → `AuthConstants.BEARER_PREFIX + agent.getApiKey()`

### 5.2 SSE 协议常量

**放置：** `com.darkness.agent.constant.SseConstants`

```java
/** SSE 流式响应相关常量，用于 AgentClient 解析 LLM API 的 SSE 协议。 */
public final class SseConstants {
    private SseConstants() {}

    /** SSE data 行前缀。 */
    public static final String DATA_PREFIX = "data:";

    /** SSE 流结束标记。 */
    public static final String DONE_MARKER = "[DONE]";
}
```

**替换：** AgentClient 中 `"data:"` → `SseConstants.DATA_PREFIX`，`"[DONE]"` → `SseConstants.DONE_MARKER`，`substring(5)` → `substring(SseConstants.DATA_PREFIX.length())`。

### 5.3 API Key 掩码常量

**放置：** `com.darkness.agent.constant.AgentConstants`

```java
/** Agent 模块常量。 */
public final class AgentConstants {
    private AgentConstants() {}

    /** API Key 脱敏掩码，用于返回给前端时隐藏真实密钥。 */
    public static final String API_KEY_MASK = "******";
}
```

**替换：** AgentVO 和 AgentServiceImpl 中的 `"******"` → `AgentConstants.API_KEY_MASK`。

### 5.4 微信 API 常量

**放置：** `com.darkness.auth.constant.WxApiConstants`

```java
/** 微信开放平台 API 相关常量。 */
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

**替换：** WxAuthServiceImpl 中所有微信 JSON 字段名字符串 → `WxApiConstants.FIELD_XXX`；`"authorization_code"` → `WxApiConstants.GRANT_TYPE_AUTH_CODE`。

> 注：微信 API URL 模板保持内联（涉及动态拼接 appId/secret/code），提取反而降低可读性。

---

## 不在本次范围内

以下项目经审计但决定暂不处理：

- **ValidationUtil 工具类** — 引入 DTO + JSR 303 校验后，手动空值检查大幅减少，暂无必要单独提取。
- **权限校验提取**（ChatServiceImpl 中 3 处归属权检查）— 逻辑简单且仅限单个 Service，暂不提取。
- **Error message 常量类** — 引入 `ResultCode` 枚举后，错误消息已集中管理；业务特定的错误消息（如 "Username already exists"）保持内联，因其具有上下文唯一性。
- **Entity-to-VO 通用映射工具** — 现有 `from()` 工厂方法模式清晰，暂不引入 MapStruct 等框架。

---

## 数据库变更

无。所有枚举通过 `@EnumValue` 注解映射到现有数据库值（0/1、"user"/"assistant" 等），表结构和数据不受影响。

## 配置变更

无需修改 `application.yml`。MyBatis-Plus 的 `@EnumValue` 注解在 3.5.x 版本中默认生效，无需额外配置 `default-enum-type-handler`。

## 构建依赖变更

无新增第三方依赖。JSR 303 校验注解（`@NotBlank`、`@NotNull`、`@Valid`）已包含在 `spring-boot-starter-web` 的 `hibernate-validator` 中。
