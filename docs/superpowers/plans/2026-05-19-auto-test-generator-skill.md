
# Auto Test Generator Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `.claude/skills/auto-test-generator.md` — a skill that auto-generates JUnit 5 + @SpringBootTest test cases when triggered by `自动生成测试用例` or `代码更改CR之前`.

**Architecture:** Single markdown skill file in `.claude/skills/` following the same frontmatter + instruction format as the existing `project-architecture` skill. The skill instructs Claude to scan git diff for changed Controllers, parse endpoints, enumerate full coverage test scenarios, and write test classes to `src/test/java/`.

**Tech Stack:** JUnit 5, MockMvc, @SpringBootTest + @AutoConfigureMockMvc, @MockBean. Spring Boot 3.3.5 provides `spring-boot-starter-test` in parent pom.xml. Must exclude DataSource/Nacos/MybatisPlus auto-config and mock Mapper beans due to @MapperScan.

---

### Task 1: Create the skill file

**Files:**
- Create: `.claude/skills/auto-test-generator.md`

- [ ] **Step 1: Write the skill file**

The skill file follows the same frontmatter + markdown format as the existing `project-architecture` skill.

**Key implementation details verified by compiling and running 18 test cases:**

1. **`@WebMvcTest` does NOT work** in this project — `@MapperScan` on the main application class creates real Mapper beans that require `sqlSessionFactory`. Use `@SpringBootTest` + `@AutoConfigureMockMvc` instead.

2. **`@MockitoBean` does NOT exist** in Spring Boot 3.3.5 — use `@MockBean` from `org.springframework.boot.test.mock.mockito.MockBean`.

3. **Must mock `UserMapper`** — even with MybatisPlusAutoConfiguration excluded, `@MapperScan` registers Mapper beans. Add `@MockBean XxxMapper` to prevent real Mapper initialization.

4. **Must exclude three auto-configs:**
```java
@SpringBootTest(properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,"
        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@AutoConfigureMockMvc
```

5. **Error scenario mocks must use `thenThrow`**, never `thenReturn(null)` — the mocked Service bypasses the real implementation's null-check logic.

6. **DELETE mocks use `doNothing()` / `doThrow()`** because `deleteUser` has void return type.

7. **Step 2 must actively verify dependencies** — check the module's `pom.xml` for `spring-boot-starter-test`, add if missing.

**Test class template (full):**

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

**Happy path template:**

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

**Error scenario template (thenThrow, NOT thenReturn null):**

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

**DELETE templates (doNothing / doThrow):**

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

**Coverage rules (per endpoint):**

| GET /{id} | Mock | Assert |
|-----------|------|--------|
| Normal | Service returns User | 200, data has id/username/email, no password |
| Not found | Service thenThrow(BizException(404)) | $.code=404 |
| ID=Long.MAX_VALUE | Service returns User | 200 |
| ID=0 | Service thenThrow(BizException(404)) | $.code=404 |
| ID=-1 | Service thenThrow(BizException(404)) | $.code=404 |

| GET list | Mock | Assert |
|----------|------|--------|
| Has data | Service returns 3 items | $.data.length()==3 |
| Empty | Service returns empty list | $.data.length()==0 |
| Single | Service returns 1 item | $.data.length()==1 |

| POST | Mock | Assert |
|------|------|--------|
| Valid | Service returns User with ID | 200, $.data.id not null |
| username=null | — | $.code indicates error |
| email=null | Service returns User | 200 |
| Empty body {} | — | $.code indicates error |
| Field overflow | — | $.code indicates error |

| PUT /{id} | Mock | Assert |
|-----------|------|--------|
| Valid | Service returns updated User | 200, $.data.id matches |
| Not found | Service thenThrow(BizException(404)) | $.code=404 |
| Partial fields | Service returns User | 200 |

| DELETE /{id} | Mock | Assert |
|-------------|------|--------|
| Exists | Service doNothing() | 200 |
| Not found | Service doThrow(BizException(404)) | $.code=404 |
| Duplicate delete | Service doThrow(BizException(404)) | $.code=404 |

- [ ] **Step 2: Verify the skill file exists and matches format**

```bash
ls -la .claude/skills/auto-test-generator.md && head -6 .claude/skills/auto-test-generator.md
```

Expected: File exists with frontmatter containing `name: auto-test-generator` and `description:`.

- [ ] **Step 3: Verify the skill generates working test cases**

Generate test cases for UserController using the skill, then run:

```bash
export JAVA_HOME="/d/jdk/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"
mvn test -pl acgagent-service-user -Dtest=UserControllerTest
```

Expected: BUILD SUCCESS, Tests run: 18, Failures: 0, Errors: 0

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/auto-test-generator.md
git commit -m "feat: add auto-test-generator skill"
```
