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

### 修改文件（共 23 个）

**common 层：** Result, BizException, GlobalExceptionHandler
**config 层：** SecurityConfig
**auth 层：** JwtUtil, JwtAuthenticationFilter, AuthController, AuthServiceImpl, SmsServiceImpl, WxAuthServiceImpl, SmsCodeDO
**user 层：** UserDO, RoleDO, PermissionDO, UserVO, RoleVO, PermissionVO, UserServiceImpl, RoleServiceImpl, PermissionServiceImpl
**agent 层：** AgentDO, MessageDO, AgentVO, MessageVO, AgentServiceImpl, ChatServiceImpl, ChatController, AgentClient

### 依赖变更

新增 `spring-boot-starter-validation` 依赖，为请求 DTO 提供 JSR 303 校验注解支持。

## 变更前后对比

| 方面 | 变更前 | 变更后 |
|------|--------|--------|
| 状态码 | 裸数字 `throw new BizException(404, ...)` | `throw new BizException(ResultCode.NOT_FOUND, ...)` |
| status 字段 | `Integer` (0/1) | `CommonStatus` 枚举 |
| Message role | `String` ("user"/"assistant") | `MessageRole` 枚举 |
| 请求参数 | `Map<String, String>` + 手动校验 | 专用 DTO + `@Valid` + JSR 303 |
| null 检查 | 15 处重复 if-null-throw | `ServiceHelper.findOrThrow()` |
| Bearer 前缀 | `"Bearer "` + `substring(7)` | `AuthConstants.BEARER_PREFIX` + `.length()` |
| SSE 常量 | `"data:"` + `"[DONE]"` + `substring(5)` | `SseConstants.DATA_PREFIX` / `DONE_MARKER` |
| API Key 掩码 | `"******"` | `AgentConstants.API_KEY_MASK` |
| 微信字段 | 硬编码字符串 `"openid"` 等 | `WxApiConstants.FIELD_XXX` |

## 需要注意的点

1. **数据库无变更** — 所有枚举通过 `@EnumValue` 映射到现有值，表结构和数据不受影响。
2. **API 接口兼容** — 枚举字段通过 `@JsonValue` + `@JsonCreator` 保持 JSON 序列化/反序列化值不变（status 仍为 0/1，role 仍为 "user"/"assistant"）。请求体 JSON 字段名不变，增加了 JSR 303 校验，校验失败返回 400 + 具体字段错误信息。
3. **MyBatis-Plus 枚举** — `@EnumValue` 在 3.5.x 版本中默认生效，无需额外配置。`@JsonValue` 同时确保 Jackson 序列化输出与数据库存储值一致。
4. **新增依赖** — `spring-boot-starter-validation` 提供 `@NotBlank`、`@NotNull`、`@Valid` 等注解支持。
5. **ChatController 补充** — `createConversation` 和 `sendMessage` 的 `@RequestBody` 已加 `@Valid`；创建会话时 title 为空默认为 "New Conversation"。
