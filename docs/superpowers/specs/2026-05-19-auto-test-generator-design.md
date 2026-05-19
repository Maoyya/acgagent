
# Auto Test Generator Skill Design

## Overview

在 `.claude/skills/` 下创建 `auto-test-generator.md` skill，当用户输入包含触发词时自动扫描最近变更的 Controller，生成 JUnit 5 + @WebMvcTest 测试用例。

## Trigger Phrases

- `自动生成测试用例`
- `代码更改CR之前`

## Execution Flow

1. **Scan changes:** `git diff --name-only HEAD~1` 或 `git log -1 --name-only` 获取变更文件，过滤 `**/controller/**/*.java`
2. **Check dependencies:** 确认对应模块 `pom.xml` 包含 `spring-boot-starter-test`，缺失则补充
3. **Parse endpoints:** 读取 Controller 源码，提取每个方法的 HTTP 方法、路径、入参、返回类型、依赖 Service
4. **Design cases:** 按覆盖规则为每个接口枚举全部测试场景
5. **Generate test file:** 在 `src/test/java/` 下创建同包路径的测试类，类名加 `Test` 后缀
6. **Report:** 告知用户生成的测试文件路径和用例数量

## Test Framework & Conventions

- **Framework:** JUnit 5 + @WebMvcTest + @MockitoBean + MockMvc
- **No database:** Service 层全部 Mock，不连数据库
- **Test data:** 每个测试方法内联构造，使用 Lombok setter
- **Method naming:** `{接口方法}_{场景}`, e.g. `getUser_userExists_returns200`
- **Structure:** Given (mock setup) / When (MockMvc request) / Then (assertions)

## Test File Template

```
变更:  {module}/src/main/java/{package}/controller/{Name}Controller.java
测试:  {module}/src/test/java/{package}/controller/{Name}ControllerTest.java
```

```java
@WebMvcTest({Name}Controller.class)
class {Name}ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private {Name}Service {name}Service;

    // test methods...
}
```

## Coverage Rules (per endpoint)

| Category | Examples (GET /{id}) |
|----------|---------------------|
| Happy path | Normal query returns 200 with full data |
| Resource not found | Service throws BizException(404), assert code=404 |
| Boundary — valid | ID=1, ID=Long.MAX_VALUE |
| Boundary — invalid | ID=0, ID=-1 |
| Null/missing | Missing path variable, POST without body |
| Data integrity | Response JSON contains id/username/email, excludes password |

### Type-specific extras

- **POST/PUT:** missing required fields (username=null), field length overflow
- **DELETE:** idempotent deletion (delete already-deleted resource)
- **GET list:** empty list, single item, multiple items

## Assertions

- HTTP status code
- `Result.code` equals expected
- `Result.data` field completeness
- `Result.message` on errors

## Requirements Summary

| Dimension | Decision |
|-----------|----------|
| Skill location | `.claude/skills/auto-test-generator.md` |
| Trigger | `自动生成测试用例`, `代码更改CR之前` |
| Scope | git diff filtered to Controller files |
| Test type | @WebMvcTest with mocked services |
| Database | None (services mocked) |
| Test data | Inline, via Lombok setters |
| Coverage depth | Full: happy + error + boundary + null |
| File convention | Same package, class name + Test suffix
