
---
name: auto-test-generator
description: 当用户说"自动生成测试用例"或"代码更改CR之前"时触发此 skill。扫描最近变更的 Controller 代码，在 src/test/java/ 下生成全量覆盖的 JUnit 5 + @SpringBootTest 测试用例。
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

如果最近变更均不涉及 Controller 文件，告知用户并询问是否对现有 Controller 生成测试。

### 第二步：确认测试依赖

检查 CONTROLLER 所在模块的 `pom.xml`（及父 `pom.xml`）是否包含 `spring-boot-starter-test`（`<scope>test</scope>`）。若缺失则在该模块的 `<dependencies>` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

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
- Service 方法可能抛出的异常（读取 Service 实现类中的 `BizException` throw）

**同时读取对应 Service 实现类**，了解每个方法的异常处理逻辑，以便正确设置 Mock。

### 第四步：枚举测试场景

对每个接口，按以下规则枚举测试用例（每个用例一个 `@Test` 方法，命名 `{接口方法}_{场景}`）：

**所有接口通用：**

| 类别 | 说明 | 命名示例 |
|------|------|----------|
| Happy path | 正常请求，Service 返回有效数据 | `getUser_userExists_returns200` |
| 资源不存在 | Service 抛出 `BizException(404, ...)` | `getUser_userNotFound_returns404` |
| 边界值 — 有效 | 最小值、最大值等有效边界 | `getUser_maxLongId_returns200` |
| 边界值 — 无效 | 零、负数等无效值 | `getUser_zeroId_returns404` |
| 参数缺失 | 路径变量/请求体缺失/null | 由 Spring MVC 自行处理 |

**按 HTTP 方法的额外场景：**

**_GET /{id}_**

| 场景 | Mock 设置 | 断言 |
|------|-----------|------|
| 正常查询 | Service 返回实体对象 | 200, `$.code=200`, data 含 id/username/email，不含 password |
| 不存在 | Service `thenThrow(BizException(404, ...))` | `$.code=404` |
| ID=Long.MAX_VALUE | Service 返回实体 | 200, data 完整 |
| ID=0 | Service `thenThrow(BizException(404, ...))` | `$.code=404` |
| ID=-1 | Service `thenThrow(BizException(404, ...))` | `$.code=404` |

**_GET list_**

| 场景 | Mock 设置 | 断言 |
|------|-----------|------|
| 有数据 | Service 返回 3 条记录 | 200, `$.data.length()==3` |
| 空列表 | Service 返回空 List | 200, `$.data.length()==0` |
| 单条 | Service 返回 1 条记录 | 200, `$.data.length()==1` |

**_POST_**

| 场景 | Mock 设置 | 断言 |
|------|-----------|------|
| 正常创建 | Service 返回带 ID 的实体 | 200, `$.data.id` 不为 null, `$.data.username` 正确 |
| username 为 null | — | 断言响应包含错误信息 |
| email 为 null | Service 正常 | 200（email 非必填） |
| 空 body `{}` | — | 断言响应包含错误信息 |
| 字段超长 | — | 断言响应包含错误信息（如 username 超过 50 字符） |

**_PUT /{id}_**

| 场景 | Mock 设置 | 断言 |
|------|-----------|------|
| 正常更新 | Service 返回更新后实体 | 200, `$.data.id==<id>`, 字段已更新 |
| ID 不存在 | Service `thenThrow(BizException(404, ...))` | `$.code=404` |
| 更新部分字段 | 只传 username | 200, 字段正确 |

**_DELETE /{id}_**

| 场景 | Mock 设置 | 断言 |
|------|-----------|------|
| 正常删除 | Service `doNothing()` | 200, `$.code=200` |
| 删除不存在 | Service `doThrow(BizException(404, ...))` | `$.code=404` |
| 重复删除 | 同上 | `$.code=404`（幂等） |

### 第五步：生成测试文件

在 Controller 所在模块的 `src/test/java/` 下，以相同包路径创建测试类。

**路径映射：**
```
源代码：  {module}/src/main/java/{package}/controller/{Name}Controller.java
测试代码：{module}/src/test/java/{package}/controller/{Name}ControllerTest.java
```

**测试类标准结构：**

> **重要：** 本项目使用 MyBatis-Plus + Nacos + MySQL，`@WebMvcTest` 无法工作（`@MapperScan` 会导致 Mapper bean 初始化失败）。必须使用 `@SpringBootTest` + `@AutoConfigureMockMvc`，并排除 DataSource、Nacos、MybatisPlus 自动配置，同时 Mock 掉 UserMapper。

```java
package {package}.controller;

import com.darkness.common.exception.BizException;
import {package}.entity.{Entity};
import {package}.mapper.{Name}Mapper;
import {package}.service.{Name}Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,"
                + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@AutoConfigureMockMvc
class {Name}ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private {Name}Mapper {name}Mapper;

    @MockBean
    private {Name}Service {name}Service;

    // test methods
}
```

**注意事项：**
- 使用 `@MockBean`（Spring Boot 3.3.5 标准注解），**不要使用** `@MockitoBean`（该注解在 3.4.0+ 才正式引入）
- 必须额外 Mock `{Name}Mapper`，否则 `@MapperScan` 会尝试创建真实的 Mapper bean 并失败
- 排除三个自动配置：`DataSourceAutoConfiguration`、`NacosDiscoveryAutoConfiguration`、`MybatisPlusAutoConfiguration`

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

**错误场景模板（使用 thenThrow，不用 thenReturn(null)）：**

Mock 了 Service 后，真实的 Service 实现中的 null-check 逻辑不会执行。因此所有错误场景必须用 `thenThrow(BizException)` 而非 `thenReturn(null)`：

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

**DELETE 方法模板（使用 doNothing / doThrow）：**

```java
@Test
void deleteUser_exists_returns200() throws Exception {
    doNothing().when(userService).deleteUser(1L);

    mockMvc.perform(delete("/api/user/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
}

@Test
void deleteUser_notFound_returns404() throws Exception {
    doThrow(new BizException(404, "User not found: 999"))
            .when(userService).deleteUser(999L);

    mockMvc.perform(delete("/api/user/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
}
```

### 第六步：验证并报告

生成测试文件后，使用 JAVA_HOME 指向 JDK 21 运行验证：

```bash
export JAVA_HOME="/d/jdk/jdk21" && export PATH="$JAVA_HOME/bin:$PATH" && mvn test -pl {module} -Dtest={Name}ControllerTest
```

告知用户：
- 生成的测试文件路径
- 测试用例数量
- 测试运行结果（通过/失败数量）
