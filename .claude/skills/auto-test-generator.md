---
name: auto-test-generator
description: 当用户说"自动生成测试用例"或"代码更改CR之前"时触发此 skill。扫描最近变更的 Controller 代码，在 src/test/java/ 下生成全量覆盖的 JUnit 5 + @WebMvcTest 测试用例。
---

# Auto Test Generator Skill

当用户输入包含以下关键词时触发：`自动生成测试用例`、`代码更改CR之前`。

## 触发后执行流程

### 第一步：扫描变更的 Controller

```bash
git diff --name-only HEAD~1
```

从输出中过滤出 `**/controller/**/*.java` 文件。如果有多个，告知用户并逐一处理。

如果 `HEAD~1` 无结果（首次提交），改用：

```bash
git log -1 --name-only --oneline
```

### 第二步：确认测试依赖

Spring Boot 3.x 的 `spring-boot-starter-test`（包含 JUnit 5 + MockMvc + Mockito）已在父 pom.xml 中声明（`<scope>test</scope>`），所有子模块均可直接使用，无需额外添加依赖。

### 第三步：读取并解析 Controller

读取变更的 Controller Java 文件，提取以下信息：

| 提取项 | 来源 |
|--------|------|
| 包路径 | `package com.xxx.yyy.controller;` |
| 类名 | `public class XxxController` |
| 基路径 | `@RequestMapping("/api/xxx")` |
| 依赖 Service | `private final XxxService xxxService;` 字段 |
| 接口列表 | 每个 `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping` 方法 |

对每个接口方法，提取：
- HTTP 方法和完整路径（基路径 + 方法路径）
- 路径变量（`@PathVariable`）及其类型
- 请求体（`@RequestBody`）及其类型
- 返回类型（`Result<User>` / `Result<List<User>>` / `Result<Void>`）
- 调用哪个 Service 的哪个方法
- Service 方法可能抛出什么异常（读取 Service 实现类中的 `BizException` throw）

### 第四步：枚举测试场景

对每个接口，按以下规则枚举测试用例（每个用例一个 `@Test` 方法，命名用 `{接口方法}_{场景}`）：

**所有接口通用：**

| 类别 | 说明 | 命名示例 |
|------|------|----------|
| Happy path | 正常请求，Service 返回有效数据 | `getUser_userExists_returns200` |
| 资源不存在 | Service 抛出 `BizException(404, ...)` | `getUser_userNotFound_returns404` |
| 边界值 — 有效 | 最小有效参数 | `getUser_minValidId_returns200` |
| 边界值 — 无效 | 非法参数值 | `getUser_zeroId_returns400` |
| 参数缺失 | 路径变量/请求体缺失 | 由 Spring MVC 自行处理 |

**按 HTTP 方法的额外场景：**

**_GET /{id}_**

| 场景 | Mock 设置 | 断言 |
|------|-----------|------|
| 正常查询 | Service 返回 User 对象 | 200, `$.code=200`, data 含 id/username/email，不含 password |
| 不存在 | Service throw `BizException(404, ...)` | 200（Result 包装）, `$.code=404` |
| ID=Long.MAX_VALUE | Service 返回 User | 200, data 完整 |
| ID=0 | Service 返回 null 或 throw | `$.code` 非 200 |
| ID=-1 | 同上 | `$.code` 非 200 |

**_GET list_**

| 场景 | Mock 设置 | 断言 |
|------|-----------|------|
| 有数据 | Service 返回 3 条记录 | 200, `$.data.length()==3` |
| 空列表 | Service 返回空 List | 200, `$.data.length()==0` |
| 单条 | Service 返回 1 条记录 | 200, `$.data.length()==1` |

**_POST_**

| 场景 | Mock 设置 | 断言 |
|------|-----------|------|
| 正常创建 | Service 返回带 ID 的 User | 200, `$.data.id` 不为 null, `$.data.username` 正确 |
| username 为 null | — | 断言响应包含错误信息 |
| email 为 null | Service 正常 | 200（email 非必填） |
| 空 body `{}` | — | 断言响应非 200 或 data 为 null |

**_PUT /{id}_**

| 场景 | Mock 设置 | 断言 |
|------|-----------|------|
| 正常更新 | Service 返回更新后 User | 200, `$.data.id==<id>`, 字段已更新 |
| ID 不存在 | Service throw `BizException(404, ...)` | `$.code=404` |
| 更新部分字段 | 只传 username | 200, 字段正确（PUT 通常全量更新） |

**_DELETE /{id}_**

| 场景 | Mock 设置 | 断言 |
|------|-----------|------|
| 正常删除 | Service 正常执行 | 200, `$.code=200` |
| 删除不存在 | Service throw `BizException(404, ...)` | `$.code=404` |
| 重复删除 | 同上 | `$.code=404`（幂等） |

### 第五步：生成测试文件

在 Controller 所在模块的 `src/test/java/` 下，以相同包路径创建测试类。

**路径映射：**
```
源代码：  {module}/src/main/java/{package}/controller/{Name}Controller.java
测试代码：{module}/src/test/java/{package}/controller/{Name}ControllerTest.java
```

**测试类标准结构：**

```java
package {package}.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({Name}Controller.class)
class {Name}ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private {Name}Service {name}Service;

    // test methods
}
```

注意：Spring Boot 3.3.5 使用 `@MockitoBean`（或兼容 `@MockBean`）。静态 import 使用：
- `org.mockito.Mockito.*` — when, verify 等
- `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*` — get, post, put, delete
- `org.springframework.test.web.servlet.result.MockMvcResultMatchers.*` — status, jsonPath, content

**每个测试方法模板（Given/When/Then 结构）：**

```java
@Test
void getById_userExists_returns200() throws Exception {
    // Given
    User user = new User();
    user.setId(1L);
    user.setUsername("testuser");
    user.setEmail("test@example.com");
    when(userService.getUserById(1L)).thenReturn(user);

    // When & Then
    mockMvc.perform(get("/api/user/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.username").value("testuser"))
            .andExpect(jsonPath("$.data.email").value("test@example.com"))
            .andExpect(jsonPath("$.data.password").doesNotExist());
}
```

**错误场景模板：**

```java
@Test
void getById_userNotFound_returns404() throws Exception {
    // Given
    when(userService.getUserById(999L))
            .thenThrow(new BizException(404, "User not found: 999"));

    // When & Then
    mockMvc.perform(get("/api/user/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("User not found: 999"));
}
```

**POST 请求模板：**

```java
@Test
void createUser_validInput_returns200() throws Exception {
    // Given
    User input = new User();
    input.setUsername("newuser");
    input.setEmail("new@example.com");

    User saved = new User();
    saved.setId(1L);
    saved.setUsername("newuser");
    saved.setEmail("new@example.com");
    when(userService.createUser(any(User.class))).thenReturn(saved);

    // When & Then
    mockMvc.perform(post("/api/user")
                    .contentType("application/json")
                    .content("{\"username\":\"newuser\",\"email\":\"new@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.username").value("newuser"));
}
```

### 第六步：报告结果

告知用户：

- 生成的测试文件路径（绝对路径）
- 测试用例数量（如：5 个接口 × 平均 4 个场景 = 20 个测试方法）
- 自行验证方式：`mvn test -pl {module} -Dtest={Name}ControllerTest`
