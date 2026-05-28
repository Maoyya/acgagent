# RBAC 权限管控 + 个人中心 + 悬浮窗对话 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 acgagent 微服务项目中实现基于角色的菜单级权限管控、个人中心、Agent 分类、数据隔离和全局悬浮窗对话。

**Architecture:** JWT 自包含角色信息，Gateway 传递 X-User-Roles 请求头，下游通过自定义 @RequireRole 注解 + AOP 切面校验角色。前端根据角色动态过滤菜单和路由。对话数据按 userId 强制隔离。

**Tech Stack:** Java 21, Spring Boot 3.3.5, MyBatis-Plus 3.5.9, jjwt 0.12.6, Vue 3.5, TypeScript, Pinia, Element Plus

**Design Spec:** `docs/superpowers/specs/2026-05-28-rbac-profile-design.md`

---

## Phase 1: 数据库与公共层（acg-common）

### Task 1: 种子数据与数据库变更

**Files:**
- Create: `acg-user/src/main/resources/db/data.sql`

- [ ] **Step 1: 创建 data.sql 种子数据文件**

```sql
-- ========================================
-- 种子数据：角色、权限、角色-权限关联、管理员用户
-- ========================================

-- 1. 角色
INSERT INTO sys_role (id, name, code, sort, status, remark) VALUES
(1, '管理员', 'admin', 1, 1, '系统管理员，拥有全部权限'),
(2, '普通用户', 'user', 2, 1, '注册默认角色，可使用业务功能'),
(3, 'VIP用户', 'user_vip', 3, 1, '付费用户，预留角色');

-- 2. 权限（树形结构）
INSERT INTO sys_permission (id, parent_id, name, code, type, path, icon, sort, status) VALUES
-- 系统管理
(1,  0, '系统管理', 'system',    1, NULL,        'Setting', 1, 1),
(2,  1, '用户管理', 'system:user', 1, '/system/users', 'User', 1, 1),
(3,  1, '角色管理', 'system:role', 1, '/system/roles', 'Lock', 2, 1),
(4,  1, '权限管理', 'system:perm', 1, '/system/permissions', 'Key', 3, 1),
-- 业务功能
(5,  0, '业务功能', 'business',  1, NULL,        'Grid', 2, 1),
(6,  5, '素材库',   'business:assets', 1, '/assets', 'FolderOpened', 1, 1),
(7,  5, '创作工坊', 'business:workshop', 1, '/workshop/new', 'Film', 2, 1),
-- Agent 管理
(8,  0, 'Agent管理', 'agent',     1, NULL,        'Monitor', 3, 1),
(9,  8, 'Agent管理', 'agent:manage', 1, '/agents', 'Monitor', 1, 1),
-- 个人中心
(10, 0, '个人中心', 'profile',   1, NULL,        'UserFilled', 4, 1),
(11, 10, '个人信息', 'profile:info', 1, '/profile', 'User', 1, 1),
(12, 10, '修改密码', 'profile:password', 1, '/profile', 'Lock', 2, 1);

-- 3. 角色-权限关联
-- admin: 全部权限
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(1,11),(1,12);
-- user: 业务 + 个人中心
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2,5),(2,6),(2,7),(2,10),(2,11),(2,12);
-- user_vip: 同 user
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3,5),(3,6),(3,7),(3,10),(3,11),(3,12);

-- 4. admin 用户（密码: admin123，BCrypt 加密）
INSERT INTO sys_user (id, username, password, nickname, status) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '管理员', 1);

-- 5. admin 用户绑定 admin 角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
```

注意：BCrypt 密码哈希需要通过 `PasswordUtil.encode("admin123")` 生成真实值后替换上面的占位哈希。在实现时先运行一个简单的 main 方法生成。

- [ ] **Step 2: 修改 schema.sql — agent 表增加 category 字段**

在 `acg-user/src/main/resources/db/schema.sql` 的 `agent` 表定义中，在 `config_json` 之后、`status` 之前添加：

```sql
`category` VARCHAR(32) NOT NULL DEFAULT 'CHAT' COMMENT 'Agent分类：CHAT-对话,VIDEO-视频,IMAGE-生图',
```

并在数据库中执行：
```sql
ALTER TABLE agent ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'CHAT' COMMENT 'Agent分类：CHAT-对话,VIDEO-视频,IMAGE-生图' AFTER config_json;
```

- [ ] **Step 3: 验证数据可插入**

```bash
mysql -u root -p acg_agent < acg-user/src/main/resources/db/data.sql
```

---

### Task 2: AgentCategory 枚举 + AgentDO/AgentVO 分类字段

**Files:**
- Create: `acg-common/src/main/java/com/darkness/common/enums/AgentCategory.java`
- Modify: `acg-common/src/main/java/com/darkness/common/entity/AgentDO.java`
- Modify: `acg-common/src/main/java/com/darkness/common/model/AgentVO.java`

- [ ] **Step 1: 创建 AgentCategory 枚举**

```java
package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 分类枚举，区分对话、视频、生图等不同类型。
 */
@Getter
@AllArgsConstructor
public enum AgentCategory {

    CHAT("CHAT", "对话"),
    VIDEO("VIDEO", "视频"),
    IMAGE("IMAGE", "生图");

    @EnumValue
    @JsonValue
    private final String value;
    private final String description;

    @JsonCreator
    public static AgentCategory fromValue(String value) {
        for (AgentCategory category : values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        return CHAT;
    }
}
```

- [ ] **Step 2: AgentDO 增加 category 字段**

在 `AgentDO.java` 的 `configJson` 字段之后添加：

```java
/** Agent 分类：CHAT-对话, VIDEO-视频, IMAGE-生图 */
private AgentCategory category;
```

- [ ] **Step 3: AgentVO 增加 category 字段**

在 `AgentVO.java` 的 `configJson` 字段之后添加：

```java
/** Agent 分类：CHAT-对话, VIDEO-视频, IMAGE-生图 */
private AgentCategory category;
```

同步更新 `from()` 方法，添加 `vo.setCategory(entity.getCategory())`。
同步更新 `toEntity()` 方法，添加 `entity.setCategory(this.category)`。

- [ ] **Step 4: 编译验证**

```bash
cd C:/Users/10173/IdeaProjects/acgagent && mvn compile -pl acg-common -am
```

---

### Task 3: 个人中心请求/响应模型

**Files:**
- Create: `acg-common/src/main/java/com/darkness/common/model/ChangePasswordRequest.java`
- Create: `acg-common/src/main/java/com/darkness/common/model/ChangePhoneRequest.java`
- Create: `acg-common/src/main/java/com/darkness/common/model/UpdateProfileRequest.java`
- Create: `acg-common/src/main/java/com/darkness/common/model/WxBindStatusVO.java`

- [ ] **Step 1: 创建 ChangePasswordRequest**

```java
package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求，需验证旧密码。
 */
@Data
public class ChangePasswordRequest {

    /** 旧密码 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码，6-256位 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 256, message = "新密码长度需在6-256位之间")
    private String newPassword;

    /** 确认新密码 */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
```

- [ ] **Step 2: 创建 ChangePhoneRequest**

```java
package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改手机号请求，需短信验证码验证。
 */
@Data
public class ChangePhoneRequest {

    /** 新手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String newPhone;

    /** 短信验证码，6位 */
    @NotBlank(message = "验证码不能为空")
    private String verifyCode;
}
```

- [ ] **Step 3: 创建 UpdateProfileRequest**

```java
package com.darkness.common.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人基本信息请求（昵称、邮箱）。
 */
@Data
public class UpdateProfileRequest {

    /** 昵称 */
    @Size(max = 64, message = "昵称长度不能超过64个字符")
    private String nickname;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    private String email;
}
```

- [ ] **Step 4: 创建 WxBindStatusVO**

```java
package com.darkness.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信绑定状态视图对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WxBindStatusVO {

    /** 是否已绑定微信 */
    private boolean bound;

    /** 绑定的微信openid（脱敏） */
    private String openid;
}
```

---

### Task 4: UserVO 扩展（roles、permissions 字段）

**Files:**
- Modify: `acg-common/src/main/java/com/darkness/common/model/UserVO.java`

- [ ] **Step 1: UserVO 增加角色和权限字段**

在 `UserVO.java` 的 `updatedAt` 字段之后添加：

```java
/** 用户角色编码列表，如 ["admin"] */
private List<String> roles;

/** 用户权限编码列表，如 ["system:user", "business:agent"] */
private List<String> permissions;
```

在文件顶部添加 `import java.util.List;`。

注意：这两个字段不由 `from(UserDO)` 填充，由 ProfileService 在获取完整用户信息时单独设置。

---

### Task 5: @RequireRole 注解 + RoleAuthAspect 切面

**Files:**
- Create: `acg-common/src/main/java/com/darkness/common/annotation/RequireRole.java`
- Create: `acg-common/src/main/java/com/darkness/common/aspect/RoleAuthAspect.java`

- [ ] **Step 1: 创建 @RequireRole 注解**

```java
package com.darkness.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 角色校验注解，标注在 Controller 类或方法上。
 * AOP 切面会校验当前用户的角色是否包含注解声明的任一角色。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色编码，满足任一即可 */
    String[] value();
}
```

- [ ] **Step 2: 创建 RoleAuthAspect 切面**

```java
package com.darkness.common.aspect;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色校验切面，读取 X-User-Roles 请求头与 @RequireRole 注解声明的角色取交集，
 * 交集为空时抛出 BizException(FORBIDDEN)。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RoleAuthAspect {

    @Before("@within(com.darkness.common.annotation.RequireRole) || " +
            "@annotation(com.darkness.common.annotation.RequireRole)")
    public void checkRole(JoinPoint joinPoint) {
        HttpServletRequest request = getRequest();
        String rolesHeader = request.getHeader("X-User-Roles");

        Set<String> userRoles = rolesHeader != null && !rolesHeader.isBlank()
                ? Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet())
                : Set.of();

        // 方法级注解优先于类级
        RequireRole methodAnnotation = getMethodAnnotation(joinPoint);
        RequireRole classAnnotation = getClassAnnotation(joinPoint);
        RequireRole annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;

        if (annotation == null) {
            return;
        }

        boolean hasRole = Arrays.stream(annotation.value())
                .anyMatch(userRoles::contains);

        if (!hasRole) {
            throw new BizException(ResultCode.FORBIDDEN, "权限不足");
        }
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BizException(ResultCode.FORBIDDEN, "无法获取请求上下文");
        }
        return attrs.getRequest();
    }

    private RequireRole getMethodAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getAnnotation(RequireRole.class);
    }

    private RequireRole getClassAnnotation(JoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().getAnnotation(RequireRole.class);
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd C:/Users/10173/IdeaProjects/acgagent && mvn compile -pl acg-common -am
```

---

### Task 6: JwtUtil 改造（JWT 写入角色）

**Files:**
- Modify: `acg-common/src/main/java/com/darkness/common/util/JwtUtil.java`

- [ ] **Step 1: 修改 generateAccessToken 方法，增加 roles 参数**

新增一个重载方法，接受 `List<String> roles`：

```java
public String generateAccessToken(Long userId, List<String> roles) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("type", TokenType.ACCESS.getValue());
    if (roles != null) {
        claims.put("roles", roles);
    }
    return Jwts.builder()
            .claims(claims)
            .subject(String.valueOf(userId))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(getSigningKey())
            .compact();
}
```

保留原 `generateAccessToken(Long userId)` 方法向后兼容，内部调用 `generateAccessToken(userId, null)`。

在文件顶部添加 `import java.util.List;` 和 `import java.util.Map;` 和 `import java.util.HashMap;`。

- [ ] **Step 2: 添加从 JWT 提取角色的方法**

```java
/**
 * 从 JWT token 中提取角色列表。
 * @param token JWT token
 * @return 角色编码列表，无角色信息时返回空列表
 */
@SuppressWarnings("unchecked")
public List<String> getRoles(String token) {
    Claims claims = parseToken(token);
    Object roles = claims.get("roles");
    if (roles instanceof List) {
        return (List<String>) roles;
    }
    return List.of();
}
```

---

## Phase 2: Gateway（acg-gateway）

### Task 7: JwtAuthFilter 传递 X-User-Roles

**Files:**
- Modify: `acg-gateway/src/main/java/com/darkness/gateway/filter/JwtAuthFilter.java`

- [ ] **Step 1: 在 filter 方法中提取 roles 并设置请求头**

找到设置 `X-User-Id` 的 mutate 部分（约 line 75-80），在 `header("X-User-Id", userId)` 后面追加：

```java
// 从 JWT 提取角色信息
Object rolesClaim = claims.get("roles");
String rolesStr = "";
if (rolesClaim instanceof List<?> rolesList && !rolesList.isEmpty()) {
    rolesStr = rolesList.stream()
            .map(Object::toString)
            .collect(Collectors.joining(","));
}
String finalRolesStr = rolesStr;
```

然后在 `.header("X-User-Id", userId)` 之后添加：
```java
.header("X-User-Roles", finalRolesStr)
```

在文件顶部添加 `import java.util.List;` 和 `import java.util.stream.Collectors;`。

- [ ] **Step 2: 编译验证**

```bash
cd C:/Users/10173/IdeaProjects/acgagent && mvn compile -pl acg-gateway -am
```

---

## Phase 3: acg-user 服务

### Task 8: AuthServiceImpl 改造（注册分配默认角色 + 登录写入角色）

**Files:**
- Modify: `acg-user/src/main/java/com/darkness/auth/service/impl/AuthServiceImpl.java`

- [ ] **Step 1: 注入 RoleMapper 和 UserRoleMapper**

在 AuthServiceImpl 的字段声明区添加：

```java
private final RoleMapper roleMapper;
private final UserRoleMapper userRoleMapper;
```

在文件顶部添加对应的 import。

- [ ] **Step 2: 修改 register() 方法，注册后绑定默认角色**

在 `register()` 方法的 `userMapper.insert(user)` 之后，`return UserVO.from(user)` 之前，添加：

```java
// 查询默认角色（code = "user"）并绑定
RoleDO defaultRole = roleMapper.selectOne(
        new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getCode, "user"));
if (defaultRole != null) {
    UserRoleDO userRole = new UserRoleDO();
    userRole.setUserId(user.getId());
    userRole.setRoleId(defaultRole.getId());
    userRoleMapper.insert(userRole);
}
```

添加 import：`RoleDO`, `UserRoleDO`, `RoleMapper`, `UserRoleMapper`, `LambdaQueryWrapper`。

- [ ] **Step 3: 修改 generateTokenPair() 方法，查询角色并写入 JWT**

```java
public TokenVO generateTokenPair(Long userId) {
    // 查询用户角色
    List<UserRoleDO> userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
    List<String> roleCodes = List.of();
    if (!userRoles.isEmpty()) {
        List<Long> roleIds = userRoles.stream().map(UserRoleDO::getRoleId).toList();
        roleCodes = roleMapper.selectBatchIds(roleIds).stream()
                .map(RoleDO::getCode)
                .toList();
    }

    String accessToken = jwtUtil.generateAccessToken(userId, roleCodes);
    String refreshToken = jwtUtil.generateRefreshToken(userId);
    return new TokenVO(accessToken, refreshToken, 7200L);
}
```

- [ ] **Step 4: 编译验证**

```bash
cd C:/Users/10173/IdeaProjects/acgagent && mvn compile -pl acg-user -am
```

---

### Task 9: ProfileService 接口与实现

**Files:**
- Create: `acg-user/src/main/java/com/darkness/user/profile/service/ProfileService.java`
- Create: `acg-user/src/main/java/com/darkness/user/profile/service/impl/ProfileServiceImpl.java`

- [ ] **Step 1: 创建 ProfileService 接口**

```java
package com.darkness.user.profile.service;

import com.darkness.common.model.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心服务，提供用户信息查询、资料修改、密码修改、手机号修改、头像上传、微信绑定等功能。
 */
public interface ProfileService {

    /** 获取当前用户完整信息（含角色和权限） */
    UserVO getCurrentUser(Long userId);

    /** 修改昵称/邮箱 */
    UserVO updateProfile(Long userId, UpdateProfileRequest request);

    /** 修改头像，返回头像 URL */
    String uploadAvatar(Long userId, MultipartFile file);

    /** 修改手机号（需验证码） */
    void changePhone(Long userId, ChangePhoneRequest request);

    /** 修改密码（需旧密码验证） */
    void changePassword(Long userId, ChangePasswordRequest request);

    /** 查询微信绑定状态 */
    WxBindStatusVO getWxBindStatus(Long userId);

    /** 解绑微信 */
    void unbindWx(Long userId);
}
```

- [ ] **Step 2: 创建 ProfileServiceImpl**

```java
package com.darkness.user.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.auth.service.SmsService;
import com.darkness.auth.util.PasswordUtil;
import com.darkness.common.entity.*;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.*;
import com.darkness.common.model.*;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.ServiceHelper;
import com.darkness.user.profile.service.ProfileService;
import com.darkness.user.common.util.FileUploadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 个人中心服务实现，处理用户资料修改、密码修改、手机号修改、头像上传、微信绑定等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final WxUserMapper wxUserMapper;
    private final SmsCodeMapper smsCodeMapper;
    private final PasswordUtil passwordUtil;
    private final FileUploadUtil fileUploadUtil;

    @Override
    public UserVO getCurrentUser(Long userId) {
        UserDO user = ServiceHelper.findOrThrow(userMapper.selectById(userId), "User", userId);
        UserVO vo = UserVO.from(user);
        vo.setRoles(getUserRoleCodes(userId));
        vo.setPermissions(getUserPermissionCodes(userId));
        return vo;
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        UserDO user = ServiceHelper.findOrThrow(userMapper.selectById(userId), "User", userId);
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        userMapper.updateById(user);
        return UserVO.from(userMapper.selectById(userId));
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        ServiceHelper.findOrThrow(userMapper.selectById(userId), "User", userId);
        String avatarUrl = fileUploadUtil.saveFile(file);
        UserDO update = new UserDO();
        update.setId(userId);
        update.setAvatar(avatarUrl);
        userMapper.updateById(update);
        return avatarUrl;
    }

    @Override
    public void changePhone(Long userId, ChangePhoneRequest request) {
        ServiceHelper.findOrThrow(userMapper.selectById(userId), "User", userId);

        // 验证短信验证码
        SmsCodeDO smsCode = smsCodeMapper.selectOne(
                new LambdaQueryWrapper<SmsCodeDO>()
                        .eq(SmsCodeDO::getPhone, request.getNewPhone())
                        .eq(SmsCodeDO::getCode, request.getVerifyCode())
                        .eq(SmsCodeDO::getUsed, 0)
                        .orderByDesc(SmsCodeDO::getCreatedAt)
                        .last("LIMIT 1"));
        if (smsCode == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "验证码无效或已过期");
        }
        // 标记已使用
        smsCode.setUsed(com.darkness.common.enums.UsedStatus.USED);
        smsCodeMapper.updateById(smsCode);

        // 校验手机号唯一性（排除当前用户）
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getPhone, request.getNewPhone())
                        .ne(UserDO::getId, userId));
        if (count > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该手机号已被其他用户使用");
        }

        UserDO update = new UserDO();
        update.setId(userId);
        update.setPhone(request.getNewPhone());
        userMapper.updateById(update);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "两次输入的新密码不一致");
        }
        UserDO user = ServiceHelper.findOrThrow(userMapper.selectById(userId), "User", userId);
        if (!passwordUtil.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "旧密码不正确");
        }
        UserDO update = new UserDO();
        update.setId(userId);
        update.setPassword(passwordUtil.encode(request.getNewPassword()));
        userMapper.updateById(update);
    }

    @Override
    public WxBindStatusVO getWxBindStatus(Long userId) {
        WxUserDO wxUser = wxUserMapper.selectOne(
                new LambdaQueryWrapper<WxUserDO>().eq(WxUserDO::getUserId, userId));
        if (wxUser == null) {
            return new WxBindStatusVO(false, null);
        }
        // openid 脱敏：保留前2位和后2位
        String openid = wxUser.getOpenid();
        String masked = openid.length() > 4
                ? openid.substring(0, 2) + "***" + openid.substring(openid.length() - 2)
                : "***";
        return new WxBindStatusVO(true, masked);
    }

    @Override
    public void unbindWx(Long userId) {
        WxUserDO wxUser = wxUserMapper.selectOne(
                new LambdaQueryWrapper<WxUserDO>().eq(WxUserDO::getUserId, userId));
        if (wxUser == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "未绑定微信");
        }
        wxUserMapper.deleteById(wxUser.getId());
    }

    /** 查询用户角色编码列表 */
    private List<String> getUserRoleCodes(Long userId) {
        List<UserRoleDO> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        if (userRoles.isEmpty()) return List.of();
        List<Long> roleIds = userRoles.stream().map(UserRoleDO::getRoleId).toList();
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(RoleDO::getCode).toList();
    }

    /** 查询用户权限编码列表（通过角色关联） */
    private List<String> getUserPermissionCodes(Long userId) {
        List<UserRoleDO> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        if (userRoles.isEmpty()) return List.of();
        List<Long> roleIds = userRoles.stream().map(UserRoleDO::getRoleId).toList();
        List<RolePermissionDO> rolePerms = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionDO>().in(RolePermissionDO::getRoleId, roleIds));
        if (rolePerms.isEmpty()) return List.of();
        List<Long> permIds = rolePerms.stream().map(RolePermissionDO::getPermissionId).distinct().toList();
        return permissionMapper.selectBatchIds(permIds).stream()
                .map(PermissionDO::getCode).toList();
    }
}
```

---

### Task 10: ProfileController

**Files:**
- Create: `acg-user/src/main/java/com/darkness/user/profile/controller/ProfileController.java`

- [ ] **Step 1: 创建 ProfileController**

```java
package com.darkness.user.profile.controller;

import com.darkness.common.model.*;
import com.darkness.common.result.Result;
import com.darkness.common.util.UserContext;
import com.darkness.user.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心控制器，提供当前用户的资料查询和修改功能。
 * 所有接口需认证，无角色限制。
 */
@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * 获取当前用户完整信息（含角色和权限）。
     * GET /api/user/profile/me（需认证）
     *
     * @return 当前用户的完整信息
     */
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        Long userId = UserContext.getUserId();
        return Result.success(profileService.getCurrentUser(userId));
    }

    /**
     * 修改昵称和邮箱。
     * PUT /api/user/profile/me/profile（需认证）
     *
     * @param request 昵称和邮箱（均可选）
     * @return 更新后的用户信息
     */
    @PutMapping("/me/profile")
    public Result<UserVO> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        Long userId = UserContext.getUserId();
        return Result.success(profileService.updateProfile(userId, request));
    }

    /**
     * 修改头像。
     * PUT /api/user/profile/me/avatar（需认证）
     *
     * @param file 头像图片文件（≤2MB，jpg/png/gif/webp）
     * @return 头像 URL
     */
    @PutMapping("/me/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        return Result.success(profileService.uploadAvatar(userId, file));
    }

    /**
     * 修改手机号，需短信验证码。
     * PUT /api/user/profile/me/phone（需认证）
     *
     * @param request 新手机号和验证码
     * @return 操作结果
     */
    @PutMapping("/me/phone")
    public Result<Void> changePhone(@RequestBody @Valid ChangePhoneRequest request) {
        Long userId = UserContext.getUserId();
        profileService.changePhone(userId, request);
        return Result.success();
    }

    /**
     * 修改密码，需验证旧密码。
     * PUT /api/user/profile/me/password（需认证）
     *
     * @param request 旧密码、新密码、确认密码
     * @return 操作结果
     */
    @PutMapping("/me/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        profileService.changePassword(userId, request);
        return Result.success();
    }

    /**
     * 查询微信绑定状态。
     * GET /api/user/profile/me/wx-status（需认证）
     *
     * @return 微信绑定状态（是否绑定、脱敏openid）
     */
    @GetMapping("/me/wx-status")
    public Result<WxBindStatusVO> getWxBindStatus() {
        Long userId = UserContext.getUserId();
        return Result.success(profileService.getWxBindStatus(userId));
    }

    /**
     * 解绑微信。
     * POST /api/user/profile/me/wx-unbind（需认证）
     *
     * @return 操作结果
     */
    @PostMapping("/me/wx-unbind")
    public Result<Void> unbindWx() {
        Long userId = UserContext.getUserId();
        profileService.unbindWx(userId);
        return Result.success();
    }
}
```

---

### Task 11: 文件上传配置与工具类

**Files:**
- Create: `acg-user/src/main/java/com/darkness/user/common/config/FileUploadConfig.java`
- Create: `acg-user/src/main/java/com/darkness/user/common/util/FileUploadUtil.java`
- Create: `acg-user/src/main/java/com/darkness/user/common/config/WebMvcConfig.java`

- [ ] **Step 1: 创建 FileUploadConfig**

```java
package com.darkness.user.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传配置，指定上传目录和访问 URL 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {

    /** 上传文件保存的磁盘目录 */
    private String dir = "/tmp/acg-avatar";

    /** 访问上传文件的 URL 前缀 */
    private String urlPrefix = "/api/user/profile/avatars";
}
```

- [ ] **Step 2: 创建 FileUploadUtil**

```java
package com.darkness.user.common.util;

import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
import com.darkness.user.common.config.FileUploadConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传工具类，处理头像等文件的保存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadUtil {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_SIZE = 2 * 1024 * 1024; // 2MB

    private final FileUploadConfig config;

    /**
     * 保存上传文件到磁盘，返回访问 URL。
     * 校验文件大小（≤2MB）和类型（jpg/png/gif/webp），使用 UUID 生成唯一文件名。
     */
    public String saveFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件大小不能超过2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持 jpg/png/gif/webp 格式");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;

        try {
            Path uploadDir = Paths.get(config.getDir());
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            Path target = uploadDir.resolve(filename);
            file.transferTo(target.toFile());
            log.info("文件上传成功: {}", target);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件上传失败");
        }

        return config.getUrlPrefix() + "/" + filename;
    }
}
```

- [ ] **Step 3: 创建 WebMvcConfig（静态资源映射）**

```java
package com.darkness.user.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置，将上传目录映射为静态资源访问路径。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FileUploadConfig fileUploadConfig;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(fileUploadConfig.getDir()).toUri().toString();
        registry.addResourceHandler(fileUploadConfig.getUrlPrefix() + "/**")
                .addResourceLocations(location);
    }
}
```

- [ ] **Step 4: Gateway 路由确认**

确认 Gateway 的 Nacos 配置 `acg-gateway.yaml` 中，`/api/user/profile/**` 路径能匹配到 `lb://acg-user` 路由。当前路由规则 `/api/users/**` 已覆盖 `/api/users/`，但个人中心接口路径是 `/api/user/profile/`，需要在 Gateway 路由中新增：

```yaml
- id: acg-user-profile
  uri: lb://acg-user
  predicates:
    - Path=/api/user/profile/**
```

---

## Phase 4: acg-chat 服务

### Task 12: ChatService 数据隔离（userId 过滤）

**Files:**
- Modify: `acg-chat/src/main/java/com/darkness/agent/service/impl/ChatServiceImpl.java`

- [ ] **Step 1: 确认现有数据隔离逻辑**

当前 ChatServiceImpl 已在所有方法中接收 `userId` 参数并用于查询和归属校验。检查以下方法是否正确实现：

- `createConversation(userId, ...)` — 确认创建时使用传入的 userId（而非请求体中的值）
- `listConversations(userId)` — 确认使用 `eq(ConversationDO::getUserId, userId)` 过滤
- `getMessages(conversationId, userId)` — 确认查询对话后校验 `conversation.getUserId().equals(userId)`
- `sendMessage(userId, conversationId, ...)` — 确认校验对话归属
- `deleteConversation(conversationId, userId)` — 确认校验对话归属

如果归属校验缺失，在查询对话后添加：

```java
ConversationDO conversation = ServiceHelper.findOrThrow(
        conversationMapper.selectById(conversationId), "Conversation", conversationId);
if (!conversation.getUserId().equals(userId)) {
    throw new BizException(ResultCode.FORBIDDEN, "无权访问该对话");
}
```

### Task 13: AgentController 添加 @RequireRole 权限标注

**Files:**
- Modify: `acg-chat/src/main/java/com/darkness/agent/controller/AgentController.java`

- [ ] **Step 1: 在写操作方法上添加 @RequireRole("admin")**

```java
import com.darkness.common.annotation.RequireRole;

// GET 方法不加注解 — 所有用户可查看
@PostMapping
@RequireRole("admin")
public Result<AgentVO> createAgent(...) { ... }

@PutMapping("/{id}")
@RequireRole("admin")
public Result<AgentVO> updateAgent(...) { ... }

@DeleteMapping("/{id}")
@RequireRole("admin")
public Result<Void> deleteAgent(...) { ... }
```

- [ ] **Step 2: 编译验证**

```bash
cd C:/Users/10173/IdeaProjects/acgagent && mvn compile -pl acg-chat -am
```

---

### Task 14: 系统管理 Controller 添加 @RequireRole("admin")

**Files:**
- Modify: `acg-user/src/main/java/com/darkness/user/controller/RoleController.java`
- Modify: `acg-user/src/main/java/com/darkness/user/controller/PermissionController.java`

- [ ] **Step 1: 在 RoleController 类上添加 @RequireRole("admin")**

```java
import com.darkness.common.annotation.RequireRole;

@RestController
@RequestMapping("/api/roles")
@RequireRole("admin")
@RequiredArgsConstructor
public class RoleController { ... }
```

- [ ] **Step 2: 在 PermissionController 类上添加 @RequireRole("admin")**

```java
import com.darkness.common.annotation.RequireRole;

@RestController
@RequestMapping("/api/permissions")
@RequireRole("admin")
@RequiredArgsConstructor
public class PermissionController { ... }
```

---

## Phase 5: 前端（acgagent-web）

### Task 15: 类型定义更新

**Files:**
- Modify: `D:/vscodeproject/acgagent-web/src/types/user.ts`
- Modify: `D:/vscodeproject/acgagent-web/src/types/agent.ts`

- [ ] **Step 1: UserVO 增加 roles 和 permissions 字段**

在 `src/types/user.ts` 的 `UserVO` 接口中添加：

```typescript
export interface UserVO {
  id: number
  username: string
  nickname: string | null
  email: string | null
  phone: string | null
  avatar: string | null
  status: number
  createdAt: string
  updatedAt: string
  roles: string[]         // 新增
  permissions: string[]   // 新增
}
```

- [ ] **Step 2: AgentVO 增加 category 字段**

在 `src/types/agent.ts` 的 `AgentVO` 接口中添加：

```typescript
export interface AgentVO {
  // ... 现有字段
  category: 'CHAT' | 'VIDEO' | 'IMAGE'  // 新增
}
```

---

### Task 16: Auth Store 改造

**Files:**
- Modify: `D:/vscodeproject/acgagent-web/src/stores/auth.ts`
- Create: `D:/vscodeproject/acgagent-web/src/api/profile.ts`

- [ ] **Step 1: 创建 profile.ts API 文件**

```typescript
import request from '@/utils/request'
import type { Result, UserVO, UpdateProfileRequest, ChangePasswordRequest, ChangePhoneRequest, WxBindStatusVO } from '@/types'

/** 文件类型需要从 @/types 导入或在此定义 */
interface WxBindStatus {
  bound: boolean
  openid: string | null
}

export function getProfile() {
  return request.get<Result<UserVO>>('/user/profile/me')
}

export function updateProfile(data: UpdateProfileRequest) {
  return request.put<Result<UserVO>>('/user/profile/me/profile', data)
}

export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.put<Result<string>>('/user/profile/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function changePhone(data: ChangePhoneRequest) {
  return request.put<Result<void>>('/user/profile/me/phone', data)
}

export function changePassword(data: ChangePasswordRequest) {
  return request.put<Result<void>>('/user/profile/me/password', data)
}

export function getWxBindStatus() {
  return request.get<Result<WxBindStatus>>('/user/profile/me/wx-status')
}

export function unbindWx() {
  return request.post<Result<void>>('/user/profile/me/wx-unbind')
}
```

- [ ] **Step 2: 改造 auth store，增加 userInfo 和角色判断**

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, refreshToken as refreshTokenApi } from '@/api/auth'
import { getProfile } from '@/api/profile'
import type { LoginRequest, RegisterRequest, UserVO } from '@/types'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem('accessToken') || '')
  const refreshToken = ref(localStorage.getItem('refreshToken') || '')
  const isLoggedIn = ref(!!accessToken.value)
  const userInfo = ref<UserVO | null>(null)

  const isAdmin = computed(() => userInfo.value?.roles?.includes('admin') ?? false)

  function hasRole(role: string): boolean {
    return userInfo.value?.roles?.includes(role) ?? false
  }

  function hasPermission(code: string): boolean {
    return userInfo.value?.permissions?.includes(code) ?? false
  }

  async function fetchUserInfo() {
    try {
      const res = await getProfile()
      userInfo.value = res.data.data
    } catch {
      // token 无效，走 401 处理
    }
  }

  async function login(data: LoginRequest) {
    const res = await loginApi(data)
    const { accessToken: at, refreshToken: rt } = res.data.data
    localStorage.setItem('accessToken', at)
    localStorage.setItem('refreshToken', rt)
    accessToken.value = at
    refreshToken.value = rt
    isLoggedIn.value = true
    await fetchUserInfo()
  }

  async function register(data: RegisterRequest) {
    await registerApi(data)
    await login({ username: data.username, password: data.password })
  }

  async function refresh() {
    if (!refreshToken.value) return
    const res = await refreshTokenApi({ refreshToken: refreshToken.value })
    const { accessToken: at, refreshToken: rt } = res.data.data
    localStorage.setItem('accessToken', at)
    localStorage.setItem('refreshToken', rt)
    accessToken.value = at
    refreshToken.value = rt
  }

  function logout() {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    accessToken.value = ''
    refreshToken.value = ''
    isLoggedIn.value = false
    userInfo.value = null
    router.push('/login')
  }

  return {
    accessToken, refreshToken, isLoggedIn, userInfo, isAdmin,
    hasRole, hasPermission, fetchUserInfo,
    login, register, refresh, logout,
  }
})
```

---

### Task 17: 路由守卫改造

**Files:**
- Modify: `D:/vscodeproject/acgagent-web/src/router/index.ts`

- [ ] **Step 1: 修改路由配置和守卫**

```typescript
import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/Login.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/auth/Register.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: AdminLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue') },
        { path: 'agents', name: 'Agents', component: () => import('@/views/agents/Index.vue'), meta: { roles: ['admin'] } },
        { path: 'workshop/:id', name: 'Workshop', component: () => import('@/views/workshop/Index.vue') },
        { path: 'assets', name: 'Assets', component: () => import('@/views/assets/Index.vue') },
        { path: 'profile', name: 'Profile', component: () => import('@/views/profile/Index.vue') },
        { path: 'system/users', name: 'Users', component: () => import('@/views/system/Users.vue'), meta: { roles: ['admin'] } },
        { path: 'system/roles', name: 'Roles', component: () => import('@/views/system/Roles.vue'), meta: { roles: ['admin'] } },
        { path: 'system/permissions', name: 'Permissions', component: () => import('@/views/system/Permissions.vue'), meta: { roles: ['admin'] } },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem('accessToken')
  if (to.meta.requiresAuth !== false && !token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'Login' || to.name === 'Register') && token) {
    return { name: 'Dashboard' }
  }

  // 角色守卫
  const requiredRoles = to.meta.roles as string[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    const authStore = useAuthStore()
    if (!authStore.userInfo) {
      await authStore.fetchUserInfo()
    }
    const userRoles = authStore.userInfo?.roles || []
    const hasAccess = requiredRoles.some((r) => userRoles.includes(r))
    if (!hasAccess) {
      return { name: 'Dashboard' }
    }
  }
})

export default router
```

注意：移除了 `/chat` 和 `/chat/:id` 路由（对话功能改为悬浮窗）。

---

### Task 18: AdminLayout 改造（动态菜单 + 用户头像 + 悬浮窗挂载）

**Files:**
- Modify: `D:/vscodeproject/acgagent-web/src/layouts/AdminLayout.vue`

- [ ] **Step 1: 改造 script 部分**

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import ChatBubble from '@/components/ChatBubble.vue'
import {
  Odometer, Monitor, Film, FolderOpened,
  Setting, User, Lock, Key, SwitchButton, UserFilled,
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const sidebarCollapsed = ref(false)
const showChatPanel = ref(false)

const activeMenu = computed(() => route.path)

const userDisplayName = computed(() => {
  const name = authStore.userInfo?.nickname || authStore.userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const userAvatar = computed(() => authStore.userInfo?.avatar)

// 动态菜单：根据用户角色过滤
interface MenuItem {
  index: string
  title: string
  icon: any
  roles?: string[]
  children?: MenuItem[]
  dividerBefore?: boolean
}

const allMenuItems: MenuItem[] = [
  { index: '/dashboard', title: '概览', icon: Odometer },
  { index: '/agents', title: 'Agent管理', icon: Monitor, roles: ['admin'] },
  { index: '_divider1', title: '', icon: null, dividerBefore: true },
  { index: '/workshop/new', title: '创作工坊', icon: Film },
  { index: '/assets', title: '素材库', icon: FolderOpened },
  { index: '_divider2', title: '', icon: null, dividerBefore: true, roles: ['admin'] },
  {
    index: 'system',
    title: '系统管理',
    icon: Setting,
    roles: ['admin'],
    children: [
      { index: '/system/users', title: '用户管理', icon: User },
      { index: '/system/roles', title: '角色管理', icon: Lock },
      { index: '/system/permissions', title: '权限管理', icon: Key },
    ],
  },
  { index: '/profile', title: '个人中心', icon: UserFilled },
]

const visibleMenuItems = computed(() => {
  const userRoles = authStore.userInfo?.roles || []
  return allMenuItems.filter((item) => {
    if (!item.roles) return true
    return item.roles.some((r) => userRoles.includes(r))
  })
})

function handleCommand(command: string) {
  if (command === 'logout') {
    authStore.logout()
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

onMounted(async () => {
  if (authStore.isLoggedIn && !authStore.userInfo) {
    await authStore.fetchUserInfo()
  }
})
</script>
```

- [ ] **Step 2: 改造 template 部分（侧边栏 + 头部 + 悬浮窗）**

```vue
<template>
  <el-container class="admin-layout">
    <el-aside :width="sidebarCollapsed ? '64px' : '220px'" class="sidebar">
      <div class="sidebar-logo" @click="router.push('/dashboard')">
        <span v-if="!sidebarCollapsed" class="logo-text">ACG Agent</span>
        <span v-else class="logo-text-short">A</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        router
        class="sidebar-menu"
        background-color="transparent"
        text-color="rgba(255,255,255,0.7)"
        active-text-color="#ffffff"
      >
        <template v-for="item in visibleMenuItems" :key="item.index">
          <el-divider v-if="item.dividerBefore" style="border-color: rgba(255,255,255,0.1); margin: 8px 16px;" />
          <el-sub-menu v-if="item.children" :index="item.index">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.index" :index="child.index">
              <el-icon><component :is="child.icon" /></el-icon>
              <template #title>{{ child.title }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else-if="!item.dividerBefore" :index="item.index">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header" :height="'60px'">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
              <path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z" />
            </svg>
          </el-icon>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar v-if="userAvatar" :size="32" :src="userAvatar" />
              <el-avatar v-else :size="32" class="user-avatar">{{ userDisplayName }}</el-avatar>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>

    <!-- 全局悬浮窗对话 -->
    <ChatBubble v-model:showPanel="showChatPanel" />
  </el-container>
</template>
```

样式部分基本保持不变，新增 `.user-avatar` 背景色样式。

---

### Task 19: 个人中心页面

**Files:**
- Create: `D:/vscodeproject/acgagent-web/src/views/profile/Index.vue`

- [ ] **Step 1: 创建个人中心页面**

完整的 Vue 3 组件，包含三个 Tab：基本信息、修改密码、账号绑定。使用 Element Plus 的 `el-tabs`、`el-form`、`el-upload` 等组件。

核心逻辑：
- `onMounted` 调用 `getProfile()` 加载用户信息
- 基本信息表单：昵称（可编辑）、邮箱（可编辑）、手机号（修改弹窗需验证码）、注册时间（只读）、角色标签（只读）
- 修改密码表单：旧密码 + 新密码 + 确认密码，调用 `changePassword()`
- 账号绑定 Tab：微信绑定状态（调用 `getWxBindStatus()`），解绑按钮（调用 `unbindWx()`）
- 头像区域：点击上传，使用 `el-upload` 调用 `uploadAvatar()`

此文件较大（约 300-400 行含模板和样式），实现时参考 `views/system/Users.vue` 的表单模式。

---

### Task 20: 悬浮窗对话组件（ChatBubble + ChatPanel）

**Files:**
- Create: `D:/vscodeproject/acgagent-web/src/components/ChatBubble.vue`
- Create: `D:/vscodeproject/acgagent-web/src/components/ChatPanel.vue`

- [ ] **Step 1: 创建 ChatBubble.vue（悬浮球）**

核心要点：
- `position: fixed; bottom: 32px; right: 32px; z-index: 9999`
- 蓝色渐变球体：`background: radial-gradient(circle at 30% 30%, #4fc3f7, #0288d1, #01579b)`
- 呼吸动画：`@keyframes breathe { 0%, 100% { box-shadow: 0 0 20px rgba(2,136,209,0.4); transform: scale(1); } 50% { box-shadow: 0 0 40px rgba(2,136,209,0.8); transform: scale(1.05); } }`
- 地球纹理效果：`conic-gradient(from 0deg, transparent, rgba(255,255,255,0.1), transparent, rgba(255,255,255,0.1))` + `animation: rotate 20s linear infinite`
- 点击 emit `update:showPanel` 切换面板
- Props: `showPanel: boolean`; Emits: `update:showPanel`

- [ ] **Step 2: 创建 ChatPanel.vue（对话面板）**

核心要点：
- `position: fixed; top: 0; right: 0; width: calc(100vw / 6); min-width: 280px; height: 100vh; z-index: 9998`
- 展开/收起动画：`transform: translateX(100%)` → `translateX(0)`，300ms transition
- 点击面板外区域收起（backdrop click）
- 内容布局：
  - 顶部：Agent 选择下拉框（从 `getAgentList()` 获取，过滤 `category === 'CHAT'`）
  - 中部：对话列表（`getConversations()`）→ 选择对话后显示消息
  - 底部：输入框 + 发送按钮（SSE 流式，使用 fetch API）
- Props: `visible: boolean`; Emits: `update:visible`

SSE 发送消息逻辑参考现有 `views/chat/Detail.vue` 的实现。

---

### Task 21: Dashboard 改造

**Files:**
- Modify: `D:/vscodeproject/acgagent-web/src/views/Dashboard.vue`

- [ ] **Step 1: 移除"新建对话"功能入口**

找到 Dashboard.vue 中的 `quickActions` 数组和相关模板，移除"新建对话"卡片。保留其他快速操作。如果 `quickActions` 只有"新建对话"一项，移除整个 Quick Actions 区域。

同时移除相关的 `recentConversations`、`navigateTo`、`continueConversation` 等对话相关代码。

Dashboard 改为只展示概览统计（如 Agent 数量、用户数量等简单统计）。

---

## 编译与验证清单

- [ ] **后端全量编译**

```bash
cd C:/Users/10173/IdeaProjects/acgagent && mvn clean compile
```

- [ ] **前端编译**

```bash
cd D:/vscodeproject/acgagent-web && npm run build
```

- [ ] **数据库初始化**

```sql
-- 执行 ALTER TABLE
ALTER TABLE agent ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'CHAT' COMMENT 'Agent分类：CHAT-对话,VIDEO-视频,IMAGE-生图' AFTER config_json;

-- 导入种子数据
source acg-user/src/main/resources/db/data.sql;
```

- [ ] **功能验证**

1. 启动 Nacos + MySQL
2. 启动 acg-user、acg-chat、acg-gateway
3. 使用 admin/admin123 登录 → 验证能看到系统管理和 Agent 管理
4. 注册新用户 → 验证默认角色为 user → 验证看不到系统管理和 Agent 管理
5. 两个用户各自创建对话 → 验证只能看到自己的对话
6. 点击右下角悬浮球 → 验证对话面板展开/收起
7. 个人中心 → 修改昵称、密码、头像
