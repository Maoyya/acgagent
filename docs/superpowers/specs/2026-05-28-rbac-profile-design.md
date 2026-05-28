# RBAC 权限管控 + 个人中心 系统设计

## 概述

在现有微服务项目（acgagent）基础上实现菜单级权限管控和个人中心功能。

**目标：**
- 新注册用户默认为普通用户（user 角色）
- admin 用户为管理员，可使用系统管理模块和 Agent 管理
- 普通用户和 VIP 用户不能看到或操作系统管理模块和 Agent 管理页
- 普通用户在对话时仍可选择 Agent（只读列表），但不能管理 Agent
- 所有用户（含 admin）只能看到自己的对话和消息，保护用户隐私
- 提供完整的个人中心（修改密码、昵称、头像、手机号、邮箱、微信绑定）
- 预留 VIP 角色（user_vip）用于后续付费分层

**权限粒度：** 菜单级 + 业务数据隔离（对话/消息按 userId 隔离）

**实现方案：** JWT 自包含角色 + 自定义注解 AOP 校验（方案 A）

---

## 一、角色与权限设计

### 1.1 角色定义

| 角色 | code | 说明 | 可访问模块 |
|---|---|---|---|
| 管理员 | `admin` | 系统管理员，拥有全部权限 | 全部 |
| 普通用户 | `user` | 注册默认角色 | Agent、对话、素材库、创作工坊、个人中心 |
| VIP 用户 | `user_vip` | 付费用户，预留角色 | 同 user，后续可扩展高级功能 |

### 1.2 权限树

```
系统管理（system）                      — admin 可见
├── 用户管理（system:user）              — admin
├── 角色管理（system:role）              — admin
└── 权限管理（system:perm）              — admin

业务功能（business）                     — user / user_vip 可见
├── 素材库（business:assets）
└── 创作工坊（business:workshop）

Agent 管理（agent）                      — admin 可见
└── Agent 管理（agent:manage）           — admin（增删改；所有用户可查看 Agent 列表用于对话）

个人中心（profile）                      — 所有角色可见
├── 个人信息（profile:info）
└── 修改密码（profile:password）
```

### 1.3 角色-权限分配

| 角色 | 权限 |
|---|---|
| admin | 全部 10 个权限 |
| user | business:* (2) + profile:* (2) = 4 个（不含 agent:manage） |
| user_vip | business:* (2) + profile:* (2) = 4 个（与 user 相同，后续可扩展） |

### 1.4 种子数据

通过 `data.sql` 预置：

- 3 个角色记录（admin / user / user_vip）
- 10 个权限记录（系统管理 4 + 业务功能 2 + Agent 管理 2 + 个人中心 2）
- 角色-权限关联（admin 绑全部，user/user_vip 绑业务+个人中心）
- admin 用户（用户名 `admin`，默认密码 `admin123`，BCrypt 加密，绑定 admin 角色）

---

## 二、后端设计

### 2.1 JWT 改造（acg-common）

**JwtUtil** 改动：

- accessToken 的 claims 增加 `roles` 字段（`List<String>`，角色 code 列表）
- 登录成功后查询用户角色，写入 JWT
- Token 刷新时重新查询角色，确保角色变更在刷新后生效
- refreshToken 不变，仍只有 userId

```
JWT payload 改造前: { sub: "123", type: "ACCESS", iat, exp }
JWT payload 改造后: { sub: "123", type: "ACCESS", roles: ["admin"], iat, exp }
```

### 2.2 Gateway 改造（acg-gateway）

**JwtAuthFilter** 改动：

- JWT 解析成功后，额外提取 `roles` claim
- 设置 `X-User-Roles` 请求头（逗号分隔的角色 code，如 `admin,user`）
- 白名单路径不变

```
当前:  X-User-Id: 123
改造后: X-User-Id: 123, X-User-Roles: admin
```

### 2.3 公共层新增（acg-common）

| 类 | 说明 |
|---|---|
| `@RequireRole(String... value)` | 注解，可标注在 Controller 方法或类上，声明所需角色 |
| `RoleAuthAspect` | AOP 切面，读取 `X-User-Roles` 请求头，校验用户是否拥有所需角色 |
| `ResultCode` 增加 `FORBIDDEN(403, "权限不足")` | 新增 403 响应码 |

**AOP 校验逻辑：**
1. 从 `HttpServletRequest` 获取 `X-User-Roles` 请求头
2. 按逗号分割为 `Set<String>`
3. 与 `@RequireRole` 注解声明的角色取交集
4. 交集为空 → 抛 `BizException(FORBIDDEN)`
5. 非空 → 放行

### 2.4 注册流程改造（acg-user）

**AuthServiceImpl.register()** 改动：

1. 现有逻辑不变（创建用户）
2. 新增：查询 `code = "user"` 的角色 ID
3. 新增：插入 `sys_user_role` 记录，绑定默认角色

依赖种子数据中 `user` 角色已存在。

### 2.5 Controller 层权限标注

**系统管理接口 — 类级别 `@RequireRole("admin")`：**

| Controller | 说明 |
|---|---|
| UserController（除个人中心外） | `/api/users/**` 下的 CRUD 接口 |
| RoleController | `/api/roles/**` |
| PermissionController | `/api/permissions/**` |

**Agent 管理接口 — 方法级别 `@RequireRole("admin")`：**

| Controller | 方法 | 标注 | 说明 |
|---|---|---|---|
| AgentController | `list()` / `getById()` | 无 | 所有用户可查看（用于对话时选择 Agent） |
| AgentController | `create()` / `update()` / `delete()` | `@RequireRole("admin")` | 仅 admin 可管理 |

### 2.6 业务数据隔离

**核心原则：** 所有用户（含 admin）只能访问自己的对话和消息数据，admin 也不能查看其他用户的聊天隐私。

**acg-chat 模块改动：**

| 接口 | 改动 |
|---|---|
| `GET /api/chat/conversations` | 查询条件增加 `userId = X-User-Id`，只返回当前用户的对话列表 |
| `POST /api/chat/conversations` | 创建对话时强制设置 `userId = X-User-Id`，不允许伪造 |
| `GET /api/chat/conversations/{id}` | 查询后校验 `conversation.userId == X-User-Id`，不匹配抛 403 |
| `POST /api/chat/conversations/{id}/send` | 同上，校验对话归属当前用户 |
| `GET /api/chat/conversations/{id}/messages` | 同上，校验对话归属当前用户 |
| `DELETE /api/chat/conversations/{id}` | 同上，校验对话归属当前用户 |

实现方式：在 ChatService 各方法中，从 `UserContext.getUserId()` 获取当前用户 ID，作为查询条件或归属校验依据。

**个人中心接口 — 无角色限制（所有登录用户可用）：**

新增 `ProfileController`（`com.darkness.user.profile.controller`）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/user/profile/me` | 获取当前用户完整信息（含角色、权限） |
| PUT | `/api/user/profile/me/profile` | 修改昵称/邮箱 |
| PUT | `/api/user/profile/me/avatar` | 修改头像（MultipartFile） |
| PUT | `/api/user/profile/me/phone` | 修改手机号（需验证码） |
| PUT | `/api/user/profile/me/password` | 修改密码（需旧密码） |
| GET | `/api/user/profile/me/wx-status` | 微信绑定状态 |
| POST | `/api/user/profile/me/wx-unbind` | 解绑微信 |

### 2.7 各接口详细逻辑

#### 修改密码 `PUT /api/user/profile/me/password`

- 请求参数：`oldPassword`（必填）、`newPassword`（必填，6-256 位）、`confirmPassword`（必填）
- 校验 newPassword == confirmPassword
- 通过 userId 查询用户，BCrypt 校验旧密码
- 更新为新密码（BCrypt 加密）
- 失败抛 BizException

#### 修改手机号 `PUT /api/user/profile/me/phone`

- 请求参数：`newPhone`（必填）、`verifyCode`（必填，6 位）
- 调用 SmsService 验证验证码（复用现有短信验证逻辑）
- 校验新手机号唯一性（排除当前用户自身）
- 更新手机号

#### 修改头像 `PUT /api/user/profile/me/avatar`

- 请求参数：`file`（MultipartFile，必填）
- 校验：文件大小 ≤ 2MB，类型限 jpg/png/gif/webp
- 生成唯一文件名（UUID + 原扩展名），保存到配置的磁盘目录
- 更新用户 avatar 字段为访问 URL

#### 修改昵称/邮箱 `PUT /api/user/profile/me/profile`

- 请求参数：`nickname`（可选）、`email`（可选）
- 直接更新非空字段

#### 微信绑定状态 `GET /api/user/profile/me/wx-status`

- 查询 wx_user 表是否有关联记录
- 返回 `{ bound: boolean, openid: "o***" }`（openid 脱敏显示）

#### 解绑微信 `POST /api/user/profile/me/wx-unbind`

- 删除 wx_user 表中当前用户的绑定记录
- 未绑定时抛 BizException

### 2.8 文件上传（头像）

**acg-user 新增：**

| 类 | 说明 |
|---|---|
| `FileUploadConfig` | 配置类，`@Value` 注入上传目录路径和访问 URL 前缀 |
| `FileUploadUtil` | 工具类，保存文件到磁盘，返回访问 URL |

配置项（Nacos 或 application.yml）：
```yaml
file:
  upload:
    dir: ${FILE_UPLOAD_DIR:/tmp/acg-avatar}
    url-prefix: ${FILE_UPLOAD_URL_PREFIX:/api/user/profile/avatars}
```

通过 Spring Boot 静态资源映射将上传目录映射为可访问 URL。

### 2.9 UserVO 扩展

UserVO 新增字段：

```java
/** 用户角色编码列表，如 ["admin"] */
private List<String> roles;

/** 用户权限编码列表，如 ["system:user", "business:agent"] */
private List<String> permissions;
```

`/api/user/profile/me` 接口通过联查 user → user_role → role → role_permission → permission 填充这两个字段。

### 2.10 新增包结构

```
acg-user/src/main/java/com/darkness/
├── user/profile/                        — 个人中心模块
│   ├── controller/ProfileController     — 个人中心接口
│   ├── service/ProfileService           — 个人中心服务接口
│   ├── service/impl/ProfileServiceImpl  — 个人中心服务实现
│   └── model/                           — 请求/响应模型
│       ├── ChangePasswordRequest
│       ├── ChangePhoneRequest
│       ├── UpdateProfileRequest
│       └── WxBindStatusVO
├── common/config/
│   ├── FileUploadConfig                 — 文件上传配置
│   └── WebMvcConfig                     — 静态资源映射（头像访问）
└── common/util/
    └── FileUploadUtil                   — 文件保存工具
```

acg-common 新增：
```
com/darkness/common/
├── annotation/RequireRole.java          — 角色校验注解
├── aspect/RoleAuthAspect.java           — 角色校验切面
└── model/
    ├── ChangePasswordRequest.java
    ├── ChangePhoneRequest.java
    ├── UpdateProfileRequest.java
    └── WxBindStatusVO.java
```

---

## 三、前端设计

### 3.1 Auth Store 改造

**文件：** `src/stores/auth.ts`

State 新增：
```typescript
userInfo: {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  roles: string[]        // ["admin"] 或 ["user"]
  permissions: string[]  // ["system:user", "business:agent", ...]
} | null
```

Actions 新增：
- `fetchUserInfo()` — 调用 `GET /api/user/profile/me`，存储用户信息
- `logout()` — 清除 token + userInfo

Getters 新增：
- `isAdmin` — roles 包含 "admin"
- `hasRole(role)` — 通用角色判断
- `hasPermission(code)` — 权限判断

### 3.2 路由守卫改造

**文件：** `src/router/index.ts`

```
router.beforeEach:
  1. 检查 token（现有逻辑）
  2. 有 token 但无 userInfo → 调用 fetchUserInfo()
  3. 检查目标路由 meta.roles
  4. 用户角色不匹配 → 跳转 403 页面或 Dashboard
```

路由 meta 配置：
```
/system/*       → meta: { requiresAuth: true, roles: ['admin'] }
/agents         → meta: { requiresAuth: true, roles: ['admin'] }  （Agent 管理页）
/profile        → meta: { requiresAuth: true }
/dashboard      → meta: { requiresAuth: true }
/chat, ...      → meta: { requiresAuth: true }
```

### 3.3 侧边栏菜单动态化

**文件：** `src/layouts/AdminLayout.vue`

将硬编码菜单改为数据驱动，根据用户角色过滤：

```typescript
const menuItems = computed(() => {
  const userRoles = authStore.userInfo?.roles || []
  return allMenuItems.filter(item => {
    if (!item.roles) return true
    return item.roles.some(r => userRoles.includes(r))
  })
})
```

菜单数据定义：
- 概览 → 所有角色
- Agent 管理 → 仅 admin
- 创作工坊 → 所有角色
- 素材库 → 所有角色
- 系统管理（子菜单） → 仅 admin
- 个人中心 → 所有角色

注意：
- 普通用户看不到 Agent 管理菜单，但在对话页面选择 Agent 时仍可查看 Agent 列表（通过 API 只读获取）。
- 对话功能从侧边栏移除，改为全局悬浮窗对话入口（见 3.5）。

### 3.4 右上角用户信息改造

**文件：** `src/layouts/AdminLayout.vue`（Header 部分）

- 显示用户头像（有头像显示头像，无头像显示用户名首字母）
- 下拉菜单：个人中心、退出登录

### 3.5 全局悬浮窗对话

对话功能从侧边栏移除，改为右下角全局悬浮窗入口，所有页面均可使用。

**悬浮球组件** `src/components/ChatBubble.vue`：

| 属性 | 说明 |
|---|---|
| 位置 | `position: fixed; bottom: 32px; right: 32px; z-index: 9999` |
| 外观 | 蓝色圆球（直径 56px），渐变色 + 径向高光模拟 3D 球体质感 |
| 动画 | 呼吸效果（CSS `@keyframes`：box-shadow 光晕脉冲 + 轻微 scale 缩放，周期 3s） |
| 旋转 | 缓慢旋转的经纬线纹理（CSS `conic-gradient` + `animation: rotate`），模拟地球自转 |
| 交互 | hover 时光晕增强、cursor: pointer；点击展开对话面板 |
| 未读提示 | 有未读消息时圆球右上角显示红色角标数字 |

**对话面板组件** `src/components/ChatPanel.vue`：

| 属性 | 说明 |
|---|---|
| 位置 | `position: fixed; top: 0; right: 0; width: calc(100vw / 6); height: 100vh; z-index: 9998` |
| 外观 | 白色背景卡片，左侧带阴影分隔，圆角 |
| 内容 | 顶部：Agent 选择下拉框（只显示 category=CHAT 的 Agent） → 对话列表/新建对话 → 消息区域 → 输入框（SSE 流式） |
| 收起逻辑 | 点击面板外的页面区域（backdrop click）或再次点击悬浮球 → 收起面板 |
| 动画 | 展开/收起使用 CSS `transform: translateX` 滑入滑出，300ms transition |

**展开状态：**
- 悬浮球隐藏或缩小到面板内左上角作为关闭按钮
- 对话面板从右侧滑入，覆盖在页面内容之上，不影响底层页面布局
- 面板宽度固定为视口的 1/6（约 `calc(100vw / 6)`），最小宽度 280px

**收起状态：**
- 对话面板滑出隐藏
- 悬浮球恢复显示在右下角

**后端对接：**
- 复用现有 ChatService 和 SSE 流式接口（`POST /api/chat/conversations/{id}/send`）
- Agent 选择列表通过 `GET /api/agents` 获取，过滤 `category=CHAT`
- 对话和消息按 userId 隔离（见 2.6 数据隔离）

### 3.6 Dashboard 改造

**文件：** `src/views/Dashboard.vue`

- 移除"新建对话"功能入口（按钮/卡片）
- Dashboard 仅保留概览统计等信息展示

### 3.6 个人中心页面

**路由：** `/profile` → `src/views/profile/Index.vue`

**页面布局：** 左侧头像区域（可点击上传），右侧 Tab 页签：

| Tab | 内容 |
|---|---|
| 基本信息 | 昵称（可编辑）、邮箱（可编辑）、手机号（修改需验证码）、注册时间、角色标签 |
| 修改密码 | 旧密码、新密码、确认密码表单 |
| 账号绑定 | 微信绑定状态、解绑按钮 |

### 3.8 前端新增/改造文件

```
src/
├── components/
│   ├── ChatBubble.vue           — 悬浮球组件（蓝色呼吸动画圆球）
│   └── ChatPanel.vue            — 对话面板组件（右侧 1/6 宽度，Agent 选择 + 对话 + SSE）
├── api/profile.ts               — 个人中心 API
├── stores/auth.ts               — 改造（增加 userInfo）
├── types/user.ts                — 改造（UserVO 增加 roles/permissions）
├── views/profile/Index.vue      — 个人中心页面
├── layouts/AdminLayout.vue      — 改造（动态菜单 + 用户头像 + 挂载 ChatBubble）
└── router/index.ts              — 改造（角色守卫 + 移除 /chat 路由）
```

### 3.8 API 新增

**文件：** `src/api/profile.ts`

```typescript
getProfile()                           → GET    /user/profile/me
updateProfile(data)                    → PUT    /user/profile/me/profile
uploadAvatar(file)                     → PUT    /user/profile/me/avatar
changePhone(data: { newPhone, verifyCode }) → PUT /user/profile/me/phone
changePassword(data: { oldPassword, newPassword, confirmPassword }) → PUT /user/profile/me/password
getWxBindStatus()                      → GET    /user/profile/me/wx-status
unbindWx()                             → POST   /user/profile/me/wx-unbind
```

---

## 四、数据变更

### 4.1 新增文件

- `acg-user/src/main/resources/db/data.sql` — 种子数据（角色、权限、关联、admin 用户）

### 4.2 表结构变更

**agent 表新增 `category` 字段：**

```sql
ALTER TABLE agent ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'CHAT' COMMENT 'Agent 分类：CHAT-对话, VIDEO-视频, IMAGE-生图';
```

**分类枚举 `AgentCategory`：**

| 枚举值 | 说明 |
|---|---|
| `CHAT` | 对话类型（默认） |
| `VIDEO` | 视频类型 |
| `IMAGE` | 生图类型 |

**AgentDO 新增字段：**
```java
/** Agent 分类：CHAT-对话, VIDEO-视频, IMAGE-生图 */
private AgentCategory category;
```

**AgentVO 新增字段：**
```java
/** Agent 分类：CHAT-对话, VIDEO-视频, IMAGE-生图 */
private AgentCategory category;
```

**Agent 管理页改动：**
- 创建/编辑 Agent 时增加分类选择（下拉框）
- 列表页增加分类筛选
- 普通用户在对话页选择 Agent 时，可按分类分组展示

其他表无需变更：
- `sys_user` 已有 avatar、phone、email 字段
- `wx_user` 已有微信绑定表
- RBAC 五张表结构完整

---

## 五、安全考量

1. **JWT 角色时效**：角色变更后需重新登录或刷新 Token 才生效。admin 修改某用户角色后，该用户的旧 Token 中仍包含旧角色。可接受的方案：admin 操作后提示"该用户需重新登录"。
2. **X-User-Roles 信任链**：Gateway 设置的请求头，下游服务直接信任。内网环境下安全，不暴露在公网。
3. **数据隔离不可绕过**：对话/消息的 userId 过滤在 Service 层强制执行，不依赖前端。即使用户直接调用 API，也无法获取他人数据。
4. **头像上传安全**：限制文件大小（≤2MB）和类型（jpg/png/gif/webp），生成随机文件名防止路径遍历。
5. **密码修改验证**：必须验证旧密码，防止 Cookie 劫持后直接改密。
6. **手机号修改验证**：必须短信验证码，防止恶意篡改。
7. **Gateway 路由**：个人中心接口 `/api/user/profile/**` 需要确认在 Gateway 路由规则中正确转发。

---

## 六、变更影响范围

| 模块 | 改动类型 | 影响 |
|---|---|---|
| acg-common | 新增注解/切面/模型 + 改造 JwtUtil/UserVO/ResultCode | 共享层，影响全部模块 |
| acg-gateway | 改造 JwtAuthFilter | 网关层，影响所有请求 |
| acg-user | 新增 ProfileController/Service + 改造 AuthServiceImpl + 新增文件上传 | 用户服务 |
| acg-chat | ChatService 增加 userId 过滤 + AgentController 权限标注 + Agent 分类 | 对话服务 |
| 前端 | 改造 Auth Store / 路由 / 侧边栏 + 新增个人中心页面 + Dashboard 去掉新建对话 | 前端全部 |

---

## 七、不包含的内容

以下功能明确不在本次范围内：

- 按钮级权限控制（页面内按钮的可见性）
- 邮箱验证（当前阶段直接修改，不发送验证邮件）
- VIP 差异化功能（角色预留，权限范围与 user 相同）
- 文件上传到 OSS（使用本地磁盘存储）
