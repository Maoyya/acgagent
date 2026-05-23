# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 构建与运行

```bash
# 构建项目（跳过测试）
mvn clean package -DskipTests

# 仅编译
mvn clean compile

# 运行测试
mvn test
```

项目没有 Maven Wrapper（`mvnw`），使用系统安装的 Maven。Maven 路径：`D:\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin`，JAVA_HOME：`D:\jdk\jdk21`。

## 技术架构

Spring Boot 3.3.5 单体应用，Java 21。Spring Security + JWT 认证，MyBatis-Plus ORM，Druid 连接池，WebClient（Agent SSE 流式）。

**技术栈：** Spring Boot 3.3.5, Spring Security, MyBatis-Plus 3.5.9, MySQL 8, Druid 1.2.23, jjwt 0.12.6, WebClient, BCrypt

### 包结构

```
com.darkness
├── Application.java          — 启动类（@MapperScan("com.darkness.**.mapper")）
├── common/
│   ├── entity/BaseEntity      — 实体基类（id, createdAt, updatedAt, deleted）
│   ├── result/Result<T>       — 统一响应体 {code, message, data}
│   └── exception/             — BizException + GlobalExceptionHandler
├── config/
│   ├── SecurityConfig         — Spring Security + JWT Filter + 路径权限
│   ├── MyBatisPlusConfig      — MetaObjectHandler 自动填充
│   ├── WebConfig              — CORS
│   └── WxConfig               — 微信开放平台配置
├── user/                      — 用户 + RBAC
│   ├── entity/                — UserDO, RoleDO, PermissionDO, UserRoleDO, RolePermissionDO
│   ├── mapper/
│   ├── model/                 — UserVO, RoleVO, PermissionVO
│   ├── service/
│   └── controller/            — UserController, RoleController, PermissionController
├── auth/                      — 认证
│   ├── entity/                — SmsCodeDO, WxUserDO
│   ├── mapper/
│   ├── model/                 — LoginUserDetails, TokenVO
│   ├── util/                  — JwtUtil, PasswordUtil
│   ├── filter/                — JwtAuthenticationFilter
│   ├── service/               — AuthService, SmsService, WxAuthService
│   └── controller/            — AuthController
└── agent/                     — Agent + Chat
    ├── entity/                — AgentDO, ConversationDO, MessageDO
    ├── mapper/
    ├── model/                 — AgentVO, ConversationVO, MessageVO
    ├── client/                — AgentClient（WebClient SSE 流式调用）
    ├── service/               — AgentService, ChatService
    └── controller/            — AgentController, ChatController
```

### API 路由

| 路径 | Controller | 说明 |
|---|---|---|
| `/api/auth/**` | AuthController | 注册、登录、刷新 token、SMS、微信登录（**permitAll**） |
| `/api/users/**` | UserController | 用户 CRUD + 角色分配 |
| `/api/roles/**` | RoleController | 角色 CRUD + 权限分配 |
| `/api/permissions/**` | PermissionController | 权限 CRUD |
| `/api/agents/**` | AgentController | Agent CRUD（GET 列表/详情 **permitAll**） |
| `/api/chat/**` | ChatController | 对话管理 + SSE 流式消息 |
| `/druid/**` | — | Druid 监控页面（**permitAll**） |

### 认证流程

- JWT Token（Bearer）通过 `Authorization` header 传递
- `JwtAuthenticationFilter` 解析 token，将 `LoginUserDetails` 放入 SecurityContext
- 三种登录方式：密码登录、短信验证码登录、微信开放平台登录
- 短信和微信登录支持自动注册

### 公共层

| 类 | 作用 |
|---|---|
| `Result<T>` | 统一 JSON 响应体 `{code, message, data}`。静态方法：`Result.success(data)`、`Result.error(code, msg)`。 |
| `BaseEntity` | MyBatis-Plus 实体基类。自增 `id`，自动填充 `createdAt`/`updatedAt`，逻辑删除字段 `deleted`（0/1）。 |
| `BizException` | 业务异常，携带 HTTP 风格的 `code` 状态码。 |
| `GlobalExceptionHandler` | `@RestControllerAdvice`，捕获 `BizException` 和 `Exception`，统一返回 `Result<Void>`。 |

### 外部基础设施

- **MySQL** 地址 `localhost:3306` — 数据库 `acg_agent`（建表脚本位于 `src/main/resources/db/schema.sql`）

### MyBatis-Plus 约定

- 逻辑删除：字段 `deleted`，0 = 未删除，1 = 已删除
- `map-underscore-to-camel-case: true` — 数据库字段 `create_time` 自动映射为 Java 属性 `createdAt`
- 所有 Entity 命名以 `DO` 后缀，VO 命名以 `VO` 后缀
- VO 使用静态 `from(DO)` 工厂方法转换，双向转换通过 `toEntity()`

### 配置文件（application.yml）

- 服务端口：`8080`
- JWT secret 和过期时间在 `jwt.*` 下配置
- 微信配置在 `wx.open.*` 下（环境变量 `WX_APP_ID`、`WX_APP_SECRET`）
- SMS 配置在 `sms.*` 下（默认 provider 为 mock）
- Druid 连接池已启用，监控页 `/druid/*`

---

## 项目规约

以下规约约束所有开发者和 AI 助手在此项目中的工作方式。优先级从高到低排列。

### 一、安全可靠（最高优先级）

> 新的改动绝不能影响现有业务正常运行。

1. **不提交敏感信息** — 密码、API Key、Secret、Token 等绝不硬编码在代码中，使用环境变量或配置文件（`.yml` 中用 `${VAR:default}`）。`.gitignore` 必须排除 `.env`、密钥文件。
2. **SQL 注入防护** — 所有数据库查询必须通过 MyBatis-Plus 的 `LambdaQueryWrapper` 或参数化查询，禁止拼接 SQL 字符串。
3. **接口鉴权** — 新增接口必须在 `SecurityConfig` 中明确配置权限（`permitAll()` 或 `authenticated()`），不能遗漏。
4. **输入校验** — Controller 层必须对外部输入做校验（空值、格式、长度）。敏感操作前必须校验当前用户身份和权限。
5. **密码安全** — 密码必须使用 `PasswordUtil`（BCrypt）加密存储，禁止明文。Token 过期时间不可随意延长。
6. **向前兼容** — 修改数据库表结构时只能加字段、不能删改已有字段的语义。修改 API 返回结构时只能新增字段、不能删除或重命名已有字段。
7. **依赖安全** — 新增第三方依赖前确认无已知高危漏洞（CVE），优先使用项目已有依赖而非引入新库。

### 二、代码注释

1. **类级注释** — 每个 Service、Controller、Config、Entity、Mapper 类必须有 Javadoc，说明职责和核心功能。
   ```java
   /** 短信验证码服务实现，包含发送验证码和验证码登录逻辑。验证码有效期 5 分钟。 */
   ```
2. **实体类字段注释** — 所有 DO、VO、Model 类的每个字段必须有 Javadoc，说明字段含义、取值规则或格式要求：
   ```java
   /** 状态：1-启用，0-禁用 */
   private Integer status;
   
   /** 外部 LLM API 的完整地址，如 https://api.openai.com/v1/chat/completions */
   private String apiUrl;
   
   /** 调用外部 API 的密钥，明文存储，生产环境需加密 */
   private String apiKey;
   ```
3. **Controller 接口方法注释** — 每个 Controller 方法必须有 Javadoc，包含：
   - 功能说明（这个接口做什么）
   - HTTP 方法和路径（如 `POST /api/roles`）
   - 认证要求（需认证 / 无需认证）
   - `@param` 参数含义和必填规则
   - `@return` 返回内容说明（如脱敏、特殊处理）
   ```java
   /**
    * 创建新角色，角色编码（code）需唯一。
    * POST /api/roles（需认证）
    *
    * @param vo 角色信息（name、code 必填）
    * @return 创建后的角色视图对象
    */
   ```
4. **Service 接口方法注释** — 每个 Service 接口方法必须有 Javadoc，包含：
   - 2-3 句话描述核心业务逻辑（校验了什么、做了什么操作、失败时抛什么异常）
   - `@param` 参数含义
   - `@return` 返回内容说明
   ```java
   /**
    * 用户注册。校验用户名非空且唯一（已存在时抛 BizException），
    * 使用 BCrypt 加密明文密码后创建用户记录。
    *
    * @param username 用户名，不能为空且不能重复
    * @param password 明文密码，不能为空
    * @return 注册后的用户视图对象
    */
   ```
5. **行内注释** — 以下情况必须加注释：
   - 非直觉的业务逻辑（如"验证码 5 分钟后过期"、"手机号未注册时自动注册"）
   - 临时方案或 workaround（标注 `// TODO:` 或 `// HACK:`）
   - 复杂的条件判断或算法
   - 脱敏、安全相关的特殊处理（如 `// 脱敏：不将真实 API Key 返回前端`）
6. **不注释废话** — 不要写 `// 设置用户名` 这种重复代码本身的注释。注释应解释"为什么"，而非"做什么"。

### 三、架构与设计原则

1. **分层职责清晰** — 严格遵循 Controller → Service → Mapper 三层架构：
   - Controller：参数接收、校验、调用 Service、返回 `Result<T>`。不包含业务逻辑。
   - Service：业务逻辑核心。事务管理在这里。不直接操作 HttpServletRequest/Response。
   - Mapper：纯数据访问，继承 `BaseMapper`，只在需要自定义 SQL 时才写 XML。
2. **单一职责** — 一个类只做一件事。如果 Service 超过 200 行，考虑拆分。如果一个方法超过 40 行，考虑提取私有方法。
3. **依赖注入** — 使用构造器注入（`@RequiredArgsConstructor`），禁止 `@Autowired` 字段注入。
4. **面向接口编程** — Service 层必须定义接口 + 实现类，方便后续替换实现或编写测试 Mock。
5. **统一异常处理** — 业务异常统一抛 `BizException`，由 `GlobalExceptionHandler` 捕获。不要在 Controller 中 try-catch 后手动返回错误。
6. **统一响应格式** — 所有 Controller 必须返回 `Result<T>`，不允许直接返回裸对象或 `ResponseEntity`。

### 四、复用性与扩展性

1. **复用优先** — 写新代码前先检查是否已有类似实现。公共逻辑放 `common` 包，跨模块复用的工具放 `util` 类。
2. **VO/DO 分离** — Entity（DO）只映射数据库，VO 只暴露给前端。两者通过 `from()` / `toEntity()` 互转，不要把 DO 直接返回给前端。
3. **配置外部化** — 可变的业务参数（超时时间、分页大小、阈值）放 `application.yml`，用 `@Value` 或 `@ConfigurationProperties` 注入，不要写死在代码里。
4. **新增模块遵循既有模式** — 新增业务模块时，参考现有模块的文件结构和命名规范（如 `agent/` 或 `auth/`），保持项目结构一致。

### 五、变更管理

1. **小步提交** — 每个逻辑完整的改动单独提交，不要把多个不相关的功能混在一个 commit 里。
2. **Commit 消息规范** — 使用约定式提交格式：
   - `feat: 新功能`
   - `fix: 修复 bug`
   - `refactor: 重构（不改变行为）`
   - `docs: 文档变更`
   - `chore: 构建/配置/依赖变更`
3. **大范围改动记录日志** — 涉及以下情况时，必须在 `docs/changelogs/` 目录下创建 Markdown 格式的变更日志：
   - 数据库表结构变更
   - API 接口新增/删除/不兼容修改
   - 架构层面的重构（包结构调整、新增模块、删除模块）
   - 安全策略变更（权限配置、认证流程改动）
   - 依赖版本升降级
   
   日志文件命名格式：`YYYY-MM-DD-<简短描述>.md`，内容包含：变更原因、影响范围、变更前后对比、需要特别注意的点。
4. **不破坏现有接口** — 修改已有接口时保持向前兼容。如需不兼容变更，必须新建接口版本（如 `/api/v2/...`）并保留旧接口。

### 六、命名规范

1. **类命名** — Entity 以 `DO` 结尾（如 `UserDO`），VO 以 `VO` 结尾（如 `UserVO`），Mapper 以 `Mapper` 结尾，Service 接口以 `Service` 结尾，实现类以 `ServiceImpl` 结尾。
2. **方法命名** — 查询用 `get`/`list`/`find` 前缀，操作用 `create`/`update`/`delete` 前缀，布尔判断用 `is`/`has`/`can` 前缀。
3. **常量命名** — 全大写下划线分隔（如 `MAX_RETRY_COUNT`），魔法数字必须提取为常量。
4. **包命名** — 全小写，按业务域划分（`user`、`auth`、`agent`、`common`、`config`），不要按技术层跨域分包。

### 七、测试要求

1. **核心路径必须有测试** — 认证流程、支付/扣费、数据状态变更等关键路径必须有单元测试或集成测试覆盖。
2. **测试命名** — 测试方法名用中文或英文描述业务场景（如 `register_withDuplicateUsername_throws400`），不使用无意义编号。
3. **不跳过测试** — 测试失败时必须修复根因，不允许用 `@Disabled` 或 `-DskipTests` 掩盖问题后提交。

### 八、Code Review（提交前必做）

1. **提交前必须 CR** — AI 助手在 `git add` 之前，必须调用 code review skill 对所有变更进行代码审查。
2. **修复 CR 发现的问题** — CR 发现的 bug、安全隐患、逻辑错误、规约违反必须修复后才能暂存。修复后再次 CR 确认通过。
3. **CR 关注重点**：
   - 安全漏洞（注入、越权、敏感信息泄露）
   - 逻辑正确性（空指针、边界条件、并发安全）
   - 规约符合度（命名、注释、分层、异常处理）
   - 向前兼容性（不改坏现有接口和行为）

### 九、Git 规范

1. **分支管理** — 功能开发在 `acgagent_dev` 分支进行，稳定后合并到 `main`。不直接在 `main` 上开发。
2. **不要自动提交** — AI 助手只执行 `git add`（且在 CR 通过后），不自动 `git commit`，由开发者自行判断提交时机和消息。
3. **冲突处理** — 合并冲突时优先保留两边的意图，不能简单地用一端覆盖另一端。
