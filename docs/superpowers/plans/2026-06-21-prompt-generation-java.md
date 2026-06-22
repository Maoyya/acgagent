# 系统提示词生成（Java 侧）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Java 侧实现「系统提示词生成」：调用 Python prompt 接口、`prompt_template` 表（公共/私有双维度）CRUD、generate/moderate/estimate 封装、admin-only apply-to-agent。

**Architecture:** 沿用现有 `PythonAiClient`(唯一 Java→Python 出口) + DO/Mapper/Service/Controller + `Result<T>` 三层模式。Controller 读 `UserContext` 取 `userId`/`isAdmin` 透传给 Service（与 `ChatController`/`ChatServiceImpl` 一致，纯 Mockito 可测，无需静态 mock）。403-moderation 不抛、镜像 Python 返回 `Result(403,"blocked",verdict)`。

**Tech Stack:** Java 21, Spring Boot 3.3.5, MyBatis-Plus 3.5.9, WebClient, Jackson(`@JsonAlias`/`@JsonValue`/`@EnumValue`), JUnit 5 + Mockito + AssertJ。

**Spec:** `docs/superpowers/specs/2026-06-21-prompt-generation-java-design.md`

## Global Constraints

- **测试约定**：纯 Mockito 单元测试（`@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` + AssertJ），不启 Spring 上下文、不连 DB。WebClient HTTP 流程归集成测试（项目既有惯例，见 `PythonAiClientTest`）。Python 侧未实现 → 所有 Python 交互通过 mock `PythonAiClient` 验证。
- **Git 约定（项目 CLAUDE.md 规约八/九，优先级高于本 skill 默认）**：每个任务完成后**先跑 code-review skill**，CR 通过才 `git add <files>`；**不要自动 `git commit`**，提交由开发者决定。Commit 消息用约定式提交（feat/fix/docs/chore）。
- **注释约定（项目规约二）**：每个 Service/Controller/Entity/Mapper/枚举/VO 类有 Javadoc；DO/VO 每个字段有 Javadoc；Controller 方法 Javadoc 含 功能/HTTP方法+路径/认证/参数/返回；Service 接口方法 Javadoc 含 2-3 句业务逻辑。
- **命名**：Entity `DO`、视图 `VO`、Mapper `Mapper`、Service 接口 `Service`/实现 `ServiceImpl`。包按业务域 `com.darkness.prompt`。
- **map-underscore-to-camel-case** 全局开启；JSON 列用 `@TableField(typeHandler=JacksonTypeHandler.class)` + `@TableName(autoResultMap=true)`；逻辑删除 `deleted` 由 `@TableLogic` 自动过滤。
- **Python-facing VO** 用 `@JsonAlias({"snake_case"})` 接收 Python 响应（序列化仍 camelCase 给前端）；**请求体**由 `PythonAiClient` 用 `LinkedHashMap` 显式构造 snake_case 键（与 `buildCreateBody` 一致）。
- **ResultCode**：SUCCESS(200)/BAD_REQUEST(400)/UNAUTHORIZED(401)/FORBIDDEN(403)/NOT_FOUND(404)/INTERNAL_ERROR(500)/SERVICE_UNAVAILABLE(503)。403-blocked 用 public 全参构造 `new Result<>(403,"blocked",verdict)`。

---

## File Structure

**新建（acg-common）：**
- `enums/PromptMode.java` — 生成模式枚举（acg/compliant）
- `entity/PromptTemplateDO.java` — prompt_template 表实体
- `mapper/PromptTemplateMapper.java` — BaseMapper
- `model/PromptGenerateRequest.java` / `PromptModerateRequest.java` / `PromptEstimateRequest.java` — →Python 请求
- `model/ModerationVerdictVO.java` / `CostEstimateVO.java` / `PromptGenerateResponseVO.java` — ←Python 响应
- `model/PromptGenerateOutcome.java` — generate 成功/blocked 值对象
- `model/PromptTemplateVO.java` / `PromptTemplateRequest.java` — 对外模板 VO/请求

**新建（acg-chat）：**
- `prompt/service/PromptService.java` — 接口
- `prompt/service/impl/PromptServiceImpl.java` — 实现
- `prompt/controller/PromptController.java` — `/api/prompts` 控制器

**修改：**
- `acg-common/util/UserContext.java` — 加 `getRoles()`/`isAdmin()`
- `acg-chat/agent/client/PythonAiClient.java` — 加 Prompt 段（5 方法）
- `acg-user/src/main/resources/db/schema.sql` — 加 `prompt_template` 建表
- Nacos `acg-gateway.yaml`（+本地镜像 `docx/nacos_config/extracted/DEFAULT_GROUP/acg-gateway.yaml`）— 路由加 `/api/prompts/**`

---

## Task 1: PromptMode 枚举

**Files:**
- Create: `acg-common/src/main/java/com/darkness/common/enums/PromptMode.java`
- Test: `acg-common/src/test/java/com/darkness/common/enums/PromptModeTest.java`

**Interfaces:**
- Produces: `PromptMode.ACG`/`PromptMode.COMPLIANT`，`getValue()` 返回 `"acg"`/`"compliant"`，`PromptMode.fromValue(String)`。`@JsonValue` 序列化为小写、`@EnumValue` 落库为小写、`@JsonCreator` 反序列化。供后续 VO/DO/请求体使用。

- [ ] **Step 1: Write the failing test**

```java
package com.darkness.common.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PromptMode 序列化/反序列化测试。业务意义：mode 必须严格匹配 Python 契约的小写 "acg"/"compliant"，非法值拒绝。 */
class PromptModeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serialize_outputsLowercaseValue() throws Exception {
        assertThat(mapper.writeValueAsString(PromptMode.ACG)).isEqualTo("\"acg\"");
        assertThat(mapper.writeValueAsString(PromptMode.COMPLIANT)).isEqualTo("\"compliant\"");
    }

    @Test
    void deserialize_acceptsLowercase() throws Exception {
        assertThat(mapper.readValue("\"acg\"", PromptMode.class)).isEqualTo(PromptMode.ACG);
        assertThat(mapper.readValue("\"compliant\"", PromptMode.class)).isEqualTo(PromptMode.COMPLIANT);
    }

    @Test
    void deserialize_illegalValue_throws() {
        assertThatThrownBy(() -> mapper.readValue("\"sandbox\"", PromptMode.class))
                .isInstanceOf(Exception.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl acg-common -Dtest=PromptModeTest`
Expected: 编译失败（PromptMode 不存在）

- [ ] **Step 3: Write minimal implementation**

```java
package com.darkness.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统提示词生成模式枚举。
 * <p>ACG-拥抱二次元/动漫风格（仅挡暴力违法/超能力）；COMPLIANT-中性专业，额外限制二次元风格。
 * 序列化与 DB 存储均为小写 value，匹配 Python PromptMode(str, Enum)。
 */
@Getter
@AllArgsConstructor
public enum PromptMode {

    ACG("acg"),
    COMPLIANT("compliant");

    /** 序列化(@JsonValue)与 DB 存储(@EnumValue)值 */
    @EnumValue
    @JsonValue
    private final String value;

    /** 反序列化：大小写不敏感；非法值抛 IllegalArgumentException（Controller 绑定时转 400）。 */
    @JsonCreator
    public static PromptMode fromValue(String value) {
        for (PromptMode m : values()) {
            if (m.value.equalsIgnoreCase(value)) return m;
        }
        throw new IllegalArgumentException("非法的 PromptMode 值: " + value);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl acg-common -Dtest=PromptModeTest`
Expected: PASS（3 tests）

- [ ] **Step 5: CR + stage**

Run code-review skill → 通过后 `git add acg-common/src/main/java/com/darkness/common/enums/PromptMode.java acg-common/src/test/java/com/darkness/common/enums/PromptModeTest.java`（不自动 commit）。

---

## Task 2: Python-facing VO + PromptGenerateOutcome

**Files:**
- Create: 7 个类于 `acg-common/src/main/java/com/darkness/common/model/`：`PromptGenerateRequest`、`PromptModerateRequest`、`PromptEstimateRequest`、`ModerationVerdictVO`、`CostEstimateVO`、`PromptGenerateResponseVO`、`PromptGenerateOutcome`
- Test: `acg-common/src/test/java/com/darkness/common/model/PromptVoParsingTest.java`

**Interfaces:**
- Consumes: Task 1 的 `PromptMode`
- Produces: 上述 7 类。关键字段：`ModerationVerdictVO{passed,violatedRules(@JsonAlias violated_rules),reasons,confidence,mode}`、`CostEstimateVO{promptTokens(@JsonAlias prompt_tokens),estCompletionTokens(@JsonAlias est_completion_tokens),model}`、`PromptGenerateResponseVO{systemPrompt(@JsonAlias system_prompt),mode,moderation,estimate,templateId(Java追加)}`、`PromptGenerateOutcome.success(s)/blocked(v)/isBlocked()/getSuccess()/getVerdict()`。供 Task 5/6 使用。

- [ ] **Step 1: Write the failing test**

```java
package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Prompt VO 解析测试。业务意义：camelCase VO 必须能从 Python snake_case JSON 解析（@JsonAlias），保证契约对齐。 */
class PromptVoParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void moderationVerdictVO_fromPythonSnakeCase() throws Exception {
        String json = "{\"passed\":false,\"violated_rules\":[\"禁止二次元风格\"],\"reasons\":[\"动漫夸张人设\"],\"confidence\":0.9,\"mode\":\"compliant\"}";
        ModerationVerdictVO v = mapper.readValue(json, ModerationVerdictVO.class);
        assertThat(v.getPassed()).isFalse();
        assertThat(v.getViolatedRules()).containsExactly("禁止二次元风格");
        assertThat(v.getConfidence()).isEqualTo(0.9);
        assertThat(v.getMode()).isEqualTo(PromptMode.COMPLIANT);
    }

    @Test
    void costEstimateVO_fromPythonSnakeCase() throws Exception {
        String json = "{\"prompt_tokens\":120,\"est_completion_tokens\":0,\"model\":\"deepseek-chat\"}";
        CostEstimateVO e = mapper.readValue(json, CostEstimateVO.class);
        assertThat(e.getPromptTokens()).isEqualTo(120);
        assertThat(e.getEstCompletionTokens()).isEqualTo(0);
        assertThat(e.getModel()).isEqualTo("deepseek-chat");
    }

    @Test
    void promptGenerateResponseVO_fromPythonSnakeCase_templateIdNull() throws Exception {
        String json = "{\"system_prompt\":\"你是...\",\"mode\":\"acg\","
                + "\"moderation\":{\"passed\":true,\"violated_rules\":[],\"reasons\":[],\"confidence\":0.95,\"mode\":\"acg\"},"
                + "\"estimate\":{\"prompt_tokens\":120,\"est_completion_tokens\":0,\"model\":\"deepseek-chat\"}}";
        PromptGenerateResponseVO r = mapper.readValue(json, PromptGenerateResponseVO.class);
        assertThat(r.getSystemPrompt()).isEqualTo("你是...");
        assertThat(r.getMode()).isEqualTo(PromptMode.ACG);
        assertThat(r.getTemplateId()).isNull(); // Python 不带，Java 落库后填
        assertThat(r.getModeration().getPassed()).isTrue();
        assertThat(r.getEstimate().getPromptTokens()).isEqualTo(120);
    }

    @Test
    void outcome_factories() {
        PromptGenerateResponseVO s = new PromptGenerateResponseVO();
        PromptGenerateOutcome ok = PromptGenerateOutcome.success(s);
        assertThat(ok.isBlocked()).isFalse();
        assertThat(ok.getSuccess()).isSameAs(s);
        assertThat(ok.getVerdict()).isNull();

        ModerationVerdictVO v = new ModerationVerdictVO();
        PromptGenerateOutcome blocked = PromptGenerateOutcome.blocked(v);
        assertThat(blocked.isBlocked()).isTrue();
        assertThat(blocked.getVerdict()).isSameAs(v);
        assertThat(blocked.getSuccess()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl acg-common -Dtest=PromptVoParsingTest`
Expected: 编译失败（类不存在）

- [ ] **Step 3: Write minimal implementation**

`PromptGenerateRequest.java`：
```java
package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import lombok.Data;
import java.util.List;

/** 系统提示词生成请求（Java→Python generate）。字段 camelCase，由 PythonAiClient 构造 snake_case body。 */
@Data
public class PromptGenerateRequest {
    /** 用户零散要求，如 "毒舌但专业的客服" */
    private List<String> userHints;
    /** 生成模式，默认 ACG */
    private PromptMode mode = PromptMode.ACG;
    /** 可选；Agent 能力标签，用于"不超能力"约束 */
    private List<String> targetCapabilities;
}
```

`PromptModerateRequest.java`：
```java
package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/** 独立 moderation 请求（Java→Python moderate）。 */
@Data
public class PromptModerateRequest {
    /** 待校验的系统提示词正文 */
    @NotBlank(message = "systemPrompt 不能为空")
    private String systemPrompt;
    /** 校验模式 */
    private PromptMode mode = PromptMode.ACG;
    /** 能力标签，用于拼接能力边界 */
    private List<String> targetCapabilities;
}
```

`PromptEstimateRequest.java`：
```java
package com.darkness.common.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/** 独立消耗估算请求（Java→Python estimate）。 */
@Data
public class PromptEstimateRequest {
    /** 待估算的系统提示词正文 */
    @NotBlank(message = "systemPrompt 不能为空")
    private String systemPrompt;
    /** 用户 hints，其 token 计入估算 */
    private List<String> userHints;
}
```

`ModerationVerdictVO.java`：
```java
package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import java.util.List;

/** moderation 裁决结果（←Python）。blocked 时作为 403 的 data 返回前端。 */
@Data
public class ModerationVerdictVO {
    /** 是否通过 moderation */
    private Boolean passed;
    /** 违反的规则文案列表 */
    @JsonAlias("violated_rules")
    private List<String> violatedRules;
    /** 违规原因说明 */
    private List<String> reasons;
    /** 置信度 0.0-1.0（二期多裁判投票钩子） */
    private Double confidence;
    /** 校验时使用的模式 */
    private PromptMode mode;
}
```

`CostEstimateVO.java`：
```java
package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/** 消耗估算（←Python）。 */
@Data
public class CostEstimateVO {
    /** 估算的 prompt token 数（system_prompt + user_hints） */
    @JsonAlias("prompt_tokens")
    private Integer promptTokens;
    /** 一期恒为 0；二期接 LLM 真实 usage */
    @JsonAlias("est_completion_tokens")
    private Integer estCompletionTokens;
    /** 估算所用模型名 */
    private String model;
}
```

`PromptGenerateResponseVO.java`：
```java
package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/** generate 成功响应（←Python，Java 落库后追加 templateId）。 */
@Data
public class PromptGenerateResponseVO {
    /** 生成的系统提示词正文 */
    @JsonAlias("system_prompt")
    private String systemPrompt;
    /** 生成模式 */
    private PromptMode mode;
    /** moderation 裁决（成功时 passed=true） */
    private ModerationVerdictVO moderation;
    /** 消耗估算 */
    private CostEstimateVO estimate;
    /** Java 落库后追加的模板 id；Python 响应不带。前端据此知道存了哪条模板 */
    private Long templateId;
}
```

`PromptGenerateOutcome.java`：
```java
package com.darkness.common.model;

import lombok.Getter;

/**
 * generate 结果：要么成功带响应(success)，要么被 moderation 拦截带裁决(verdict)。
 * blocked=true 时 success=null。用于让 Service 把 403 当业务结果而非异常处理。
 */
@Getter
public class PromptGenerateOutcome {
    private final boolean blocked;
    private final PromptGenerateResponseVO success;
    private final ModerationVerdictVO verdict;

    private PromptGenerateOutcome(boolean blocked, PromptGenerateResponseVO success, ModerationVerdictVO verdict) {
        this.blocked = blocked;
        this.success = success;
        this.verdict = verdict;
    }

    public static PromptGenerateOutcome success(PromptGenerateResponseVO s) {
        return new PromptGenerateOutcome(false, s, null);
    }

    public static PromptGenerateOutcome blocked(ModerationVerdictVO v) {
        return new PromptGenerateOutcome(true, null, v);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl acg-common -Dtest=PromptVoParsingTest`
Expected: PASS（4 tests）

- [ ] **Step 5: CR + stage**

code-review → `git add` 上述 7 个源文件 + 测试文件（不自动 commit）。

---

## Task 3: PromptTemplateDO + Mapper + DDL + 模板 VO/Request

**Files:**
- Create: `acg-common/.../entity/PromptTemplateDO.java`、`acg-common/.../mapper/PromptTemplateMapper.java`、`acg-common/.../model/PromptTemplateVO.java`、`acg-common/.../model/PromptTemplateRequest.java`
- Modify: `acg-user/src/main/resources/db/schema.sql`（末尾追加建表）
- Test: `acg-common/src/test/java/com/darkness/common/model/PromptTemplateVOTest.java`

**Interfaces:**
- Consumes: Task 1 `PromptMode`、`BaseEntity`、`CommonStatus`、`JacksonTypeHandler` 约定
- Produces: `PromptTemplateDO{userId,name,systemPrompt,mode,targetCapabilities(JSON),estPromptTokens,status}`、`PromptTemplateMapper`（BaseMapper）、`PromptTemplateVO.from(DO)`（派生 `isPublic`=userId==null）、`PromptTemplateRequest{name,systemPrompt,mode,targetCapabilities,isPublic}`。供 Task 6/7/8 使用。

- [ ] **Step 1: Write the failing test**

```java
package com.darkness.common.model;

import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PromptMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PromptTemplateVO 转换测试。业务意义：isPublic 必须由 user_id 派生（公共库 vs 用户私有的展示区分）。 */
class PromptTemplateVOTest {

    @Test
    void from_publicTemplate_isPublicTrue() {
        PromptTemplateDO pub = new PromptTemplateDO();
        pub.setId(1L);
        pub.setUserId(null); // 公共
        pub.setName("P");
        pub.setSystemPrompt("s");
        pub.setMode(PromptMode.ACG);
        pub.setStatus(CommonStatus.ENABLED);
        PromptTemplateVO vo = PromptTemplateVO.from(pub);
        assertThat(vo.getIsPublic()).isTrue();
        assertThat(vo.getName()).isEqualTo("P");
    }

    @Test
    void from_privateTemplate_isPublicFalse() {
        PromptTemplateDO priv = new PromptTemplateDO();
        priv.setId(2L);
        priv.setUserId(5L); // 私有
        priv.setName("Q");
        priv.setSystemPrompt("s2");
        assertThat(PromptTemplateVO.from(priv).getIsPublic()).isFalse();
    }

    @Test
    void from_null_returnsNull() {
        assertThat(PromptTemplateVO.from(null)).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl acg-common -Dtest=PromptTemplateVOTest`
Expected: 编译失败（类不存在）

- [ ] **Step 3: Write minimal implementation**

`PromptTemplateDO.java`（镜像 `AgentDO` 的 JSON 列写法）：
```java
package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PromptMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 系统提示词模板实体，映射 prompt_template 表。
 * <p>双维度：user_id=NULL 为公共模板(管理员开放)，非 NULL 为该用户的私有模板。
 * target_capabilities 为 JSON 列，经 JacksonTypeHandler 映射为 List<String>。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "prompt_template", autoResultMap = true)
public class PromptTemplateDO extends BaseEntity {

    /** 归属用户 ID；NULL=公共模板，非 NULL=该用户的私有模板 */
    private Long userId;

    /** 模板名称，生成时自动取首条 hint 截断，可后续修改 */
    private String name;

    /** 系统提示词正文 */
    private String systemPrompt;

    /** 生成模式：ACG/COMPLIANT */
    private PromptMode mode;

    /** 能力标签数组，用于"不超能力"约束，如 ["chat","rag"] */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> targetCapabilities;

    /** 生成时估算的 prompt token 数(缓存)，未估算为 null */
    private Integer estPromptTokens;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;
}
```

`PromptTemplateMapper.java`（镜像 `AgentMapper`）：
```java
package com.darkness.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.common.entity.PromptTemplateDO;

/** 系统提示词模板数据访问层，基于 MyBatis-Plus BaseMapper。 */
public interface PromptTemplateMapper extends BaseMapper<PromptTemplateDO> {
}
```

`PromptTemplateVO.java`：
```java
package com.darkness.common.model;

import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PromptMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 提示词模板视图对象，对外暴露给前端。isPublic 为派生字段（user_id=NULL 时为 true）。 */
@Data
public class PromptTemplateVO {

    /** 模板主键 */
    private Long id;

    /** 归属用户 ID；null=公共模板 */
    private Long userId;

    /** 是否公共模板（user_id=NULL 时 true），派生展示字段 */
    private Boolean isPublic;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称长度不能超过128")
    private String name;

    /** 系统提示词正文 */
    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    /** 生成模式 */
    private PromptMode mode;

    /** 能力标签数组 */
    private List<String> targetCapabilities;

    /** 估算 prompt token（缓存） */
    private Integer estPromptTokens;

    /** 状态 */
    private CommonStatus status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** DO→VO，并派生 isPublic。entity 为 null 返回 null。 */
    public static PromptTemplateVO from(PromptTemplateDO entity) {
        if (entity == null) return null;
        PromptTemplateVO vo = new PromptTemplateVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setIsPublic(entity.getUserId() == null);
        vo.setName(entity.getName());
        vo.setSystemPrompt(entity.getSystemPrompt());
        vo.setMode(entity.getMode());
        vo.setTargetCapabilities(entity.getTargetCapabilities());
        vo.setEstPromptTokens(entity.getEstPromptTokens());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
```

`PromptTemplateRequest.java`（手建/更新入参）：
```java
package com.darkness.common.model;

import com.darkness.common.enums.PromptMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 模板手建/更新请求。isPublic 仅管理员可置 true（→user_id=NULL）；普通用户置 true 由 Service 拒绝(403)。 */
@Data
public class PromptTemplateRequest {

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称长度不能超过128")
    private String name;

    /** 系统提示词正文 */
    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    /** 生成模式，默认 ACG */
    private PromptMode mode = PromptMode.ACG;

    /** 能力标签数组 */
    private List<String> targetCapabilities;

    /** 是否公共模板；仅管理员可置 true。true→落库 user_id=NULL */
    private Boolean isPublic;
}
```

DDL（追加到 `acg-user/src/main/resources/db/schema.sql` 末尾）：
```sql

CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    user_id BIGINT COMMENT '归属用户 ID；NULL=公共模板(管理员开放)，非 NULL=该用户的私有模板',
    name VARCHAR(128) NOT NULL COMMENT '模板名称，生成时自动取首条 hint 截断，可后续修改',
    system_prompt TEXT NOT NULL COMMENT '生成的系统提示词正文',
    mode VARCHAR(16) NOT NULL DEFAULT 'acg' COMMENT '生成模式：acg-二次元, compliant-合规',
    target_capabilities JSON COMMENT '能力标签数组，用于"不超能力"约束，如 ["chat","rag"]',
    est_prompt_tokens INT COMMENT '生成时估算的 prompt token 数(缓存)，未估算为 NULL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) COMMENT '系统提示词模板表：公共库(user_id=NULL)+用户私有(user_id=非空)';
```

> 手动执行：开发库执行该 DDL（或重启时由既有迁移机制应用；本项目 schema.sql 为建库脚本，需在 MySQL `acg_agent` 库手动跑此段）。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl acg-common -Dtest=PromptTemplateVOTest`
Expected: PASS（3 tests）

- [ ] **Step 5: CR + stage**

code-review → `git add` 4 个源文件 + schema.sql + 测试文件（不自动 commit）。

---

## Task 4: UserContext.getRoles()/isAdmin()

**Files:**
- Modify: `acg-common/src/main/java/com/darkness/common/util/UserContext.java`
- Test: `acg-common/src/test/java/com/darkness/common/util/UserContextTest.java`

**Interfaces:**
- Produces: `UserContext.getRoles()` 返回 `Set<String>`（读 `X-User-Roles` 头，逗号分隔）、`UserContext.isAdmin()`（含 "admin" 为 true）。供 Task 9 Controller 调用、透传给 Service。

- [ ] **Step 1: Write the failing test**

```java
package com.darkness.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** UserContext 角色读取测试。业务意义：isAdmin 必须正确解析网关注入的 X-User-Roles 头（双维度权限前置）。 */
class UserContextTest {

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void isAdmin_true_whenHeaderContainsAdmin() {
        setRolesHeader("user,admin");
        assertThat(UserContext.isAdmin()).isTrue();
    }

    @Test
    void isAdmin_false_whenNoAdminRole() {
        setRolesHeader("user");
        assertThat(UserContext.isAdmin()).isFalse();
    }

    @Test
    void getRoles_empty_whenNoRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        assertThat(UserContext.getRoles()).isEmpty();
        assertThat(UserContext.isAdmin()).isFalse();
    }

    private void setRolesHeader(String roles) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-User-Roles")).thenReturn(roles);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl acg-common -Dtest=UserContextTest`
Expected: FAIL（getRoles/isAdmin 方法不存在，编译失败）

- [ ] **Step 3: Write minimal implementation**

在 `UserContext.java` 末尾（`getCurrentRequest` 私有方法之前或之后）追加：
```java
    /** Gateway 注入的角色头名（逗号分隔角色编码）。 */
    public static final String ROLES_HEADER = "X-User-Roles";

    /**
     * 读取当前用户的角色编码集合（来自 X-User-Roles 头）。
     * 无请求上下文或头缺失时返回空集。与 RoleAuthAspect 同源。
     *
     * @return 角色编码集合，不可变空集当无数据
     */
    public static java.util.Set<String> getRoles() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return java.util.Set.of();
        String header = request.getHeader(ROLES_HEADER);
        if (header == null || header.isBlank()) return java.util.Set.of();
        return java.util.Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 当前登录用户是否为管理员（角色含 "admin"）。
     *
     * @return true 当含 admin 角色
     */
    public static boolean isAdmin() {
        return getRoles().contains("admin");
    }
```

> 不改动既有 `getUserId()`/`getUsername()`（surgical）。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl acg-common -Dtest=UserContextTest`
Expected: PASS（3 tests）

- [ ] **Step 5: CR + stage**

code-review → `git add acg-common/src/main/java/com/darkness/common/util/UserContext.java acg-common/src/test/java/com/darkness/common/util/UserContextTest.java`（不自动 commit）。

---

## Task 5: PythonAiClient Prompt 段

**Files:**
- Modify: `acg-chat/src/main/java/com/darkness/agent/client/PythonAiClient.java`（加 Prompt 段：`generatePrompt`/`parseGenerateOutcome`/`buildPromptGenerateBody`/`moderatePrompt`/`estimatePrompt` + 私有 `postJson`）
- Modify: `acg-chat/src/test/java/com/darkness/agent/client/PythonAiClientTest.java`（加 4 个测试方法 + 必要 import）

**Interfaces:**
- Consumes: Task 2 全部 VO + `PromptMode`、既有 `readTree`/`extractData`/`objectMapper`/`pythonWebClient`/`props`
- Produces: `generatePrompt(req,userId)→PromptGenerateOutcome`（WebClient 调用 + 解析）、`parseGenerateOutcome(json)→PromptGenerateOutcome`（纯解析，可单测）、`buildPromptGenerateBody(req)→Map`（snake_case）、`moderatePrompt(req,userId)→ModerationVerdictVO`、`estimatePrompt(req,userId)→CostEstimateVO`。供 Task 6/8 使用。

> 约定：WebClient HTTP 流程归集成测试；本任务单测只覆盖纯解析/构造方法（与既有 `PythonAiClientTest` 用 `new PythonAiClient(null,null,new ObjectMapper())` 一致）。

- [ ] **Step 1: Write the failing test**

在 `PythonAiClientTest.java` 顶部 import 区追加：
```java
import com.darkness.common.enums.PromptMode;
import com.darkness.common.model.PromptGenerateOutcome;
import com.darkness.common.model.PromptGenerateRequest;
```
在类内末尾追加测试方法：
```java
    @Test
    void parseGenerateOutcome_success_returnsSuccessOutcome() {
        String json = "{\"code\":200,\"data\":{\"system_prompt\":\"你是...\",\"mode\":\"acg\","
                + "\"moderation\":{\"passed\":true},\"estimate\":{\"prompt_tokens\":120,\"est_completion_tokens\":0,\"model\":\"deepseek-chat\"}}}";
        PromptGenerateOutcome o = client.parseGenerateOutcome(json);
        assertThat(o.isBlocked()).isFalse();
        assertThat(o.getSuccess().getSystemPrompt()).isEqualTo("你是...");
        assertThat(o.getSuccess().getEstimate().getPromptTokens()).isEqualTo(120);
    }

    @Test
    void parseGenerateOutcome_blocked_returnsBlockedOutcome_notThrow() {
        String json = "{\"code\":403,\"message\":\"blocked\",\"data\":{\"passed\":false,\"violated_rules\":[\"r\"],\"mode\":\"compliant\"}}";
        PromptGenerateOutcome o = client.parseGenerateOutcome(json);
        assertThat(o.isBlocked()).isTrue();
        assertThat(o.getVerdict().getPassed()).isFalse();
    }

    @Test
    void parseGenerateOutcome_python500_throwsBizWithCode() {
        assertThatThrownBy(() -> client.parseGenerateOutcome("{\"code\":500,\"message\":\"llm down\"}"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(500);
    }

    @Test
    void buildPromptGenerateBody_snakeCase() {
        PromptGenerateRequest req = new PromptGenerateRequest();
        req.setUserHints(java.util.List.of("毒舌客服"));
        req.setMode(PromptMode.COMPLIANT);
        req.setTargetCapabilities(java.util.List.of("chat"));
        Map<String, Object> body = client.buildPromptGenerateBody(req);
        assertThat(body).containsEntry("user_hints", java.util.List.of("毒舌客服"));
        assertThat(body).containsEntry("mode", "compliant");
        assertThat(body).containsEntry("target_capabilities", java.util.List.of("chat"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl acg-chat -am -Dtest=PythonAiClientTest`
Expected: 编译失败（parseGenerateOutcome/buildPromptGenerateBody 不存在）

- [ ] **Step 3: Write minimal implementation**

在 `PythonAiClient.java` 顶部 import 区追加：
```java
import com.darkness.common.enums.PromptMode;
import com.darkness.common.model.PromptEstimateRequest;
import com.darkness.common.model.PromptGenerateOutcome;
import com.darkness.common.model.PromptGenerateRequest;
import com.darkness.common.model.PromptGenerateResponseVO;
import com.darkness.common.model.PromptModerateRequest;
import com.darkness.common.model.ModerationVerdictVO;
import com.darkness.common.model.CostEstimateVO;
```
在「工具」段之后（`// ==================== 内部 GET/DELETE 辅助 ====================` 之前）插入 Prompt 段：
```java
    // ==================== Prompt ====================

    /**
     * 调 Python POST /api/v1/prompts/generate。
     * code=200 → 成功响应；code=403 → 被 moderation 拦截(不抛，带裁决)；其余 → BizException(code)。
     *
     * @param req    生成请求
     * @param userId 当前用户 id，透传 X-User-Id（Python 据此写偏好）
     * @return 生成结果（success 或 blocked）
     */
    public PromptGenerateOutcome generatePrompt(PromptGenerateRequest req, Long userId) {
        String json = pythonWebClient.post()
                .uri("/api/v1/prompts/generate")
                .header("X-API-Key", props.getApiKey())
                .header("X-User-Id", String.valueOf(userId))
                .header("Content-Type", "application/json")
                .bodyValue(buildPromptGenerateBody(req))
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
        return parseGenerateOutcome(json);
    }

    /**
     * 解析 generate 响应信封：200→success，403→blocked(不抛)，其余→BizException(code)。
     * 纯解析方法，可单测（WebClient 流程归集成测试）。
     */
    public PromptGenerateOutcome parseGenerateOutcome(String json) {
        JsonNode root = readTree(json);
        int code = root.path("code").asInt(200);
        JsonNode data = root.path("data");
        try {
            if (code == 200) {
                return PromptGenerateOutcome.success(objectMapper.treeToValue(data, PromptGenerateResponseVO.class));
            } else if (code == 403) {
                return PromptGenerateOutcome.blocked(objectMapper.treeToValue(data, ModerationVerdictVO.class));
            }
        } catch (Exception e) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "AI 引擎响应解析失败");
        }
        throw new BizException(code, root.path("message").asText("Python generate prompt failed"));
    }

    /**
     * 构造 generate 请求体（snake_case 键，匹配 Python pydantic）。
     */
    public Map<String, Object> buildPromptGenerateBody(PromptGenerateRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_hints", req.getUserHints());
        body.put("mode", req.getMode() != null ? req.getMode().getValue() : PromptMode.ACG.getValue());
        if (req.getTargetCapabilities() != null) {
            body.put("target_capabilities", req.getTargetCapabilities());
        }
        return body;
    }

    /**
     * 调 Python POST /api/v1/prompts/moderate，返回裁决。code != 200 由 extractData 抛 BizException。
     */
    public ModerationVerdictVO moderatePrompt(PromptModerateRequest req, Long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_prompt", req.getSystemPrompt());
        body.put("mode", req.getMode() != null ? req.getMode().getValue() : PromptMode.ACG.getValue());
        if (req.getTargetCapabilities() != null) body.put("target_capabilities", req.getTargetCapabilities());
        return extractData(postJson("/api/v1/prompts/moderate", body, userId), ModerationVerdictVO.class);
    }

    /**
     * 调 Python POST /api/v1/prompts/estimate，返回消耗估算。code != 200 由 extractData 抛。
     */
    public CostEstimateVO estimatePrompt(PromptEstimateRequest req, Long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_prompt", req.getSystemPrompt());
        if (req.getUserHints() != null) body.put("user_hints", req.getUserHints());
        return extractData(postJson("/api/v1/prompts/estimate", body, userId), CostEstimateVO.class);
    }

    /**
     * POST JSON 通用辅助（带 X-API-Key + X-User-Id）。
     */
    private String postJson(String path, Map<String, Object> body, Long userId) {
        return pythonWebClient.post()
                .uri(path)
                .header("X-API-Key", props.getApiKey())
                .header("X-User-Id", String.valueOf(userId))
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl acg-chat -am -Dtest=PythonAiClientTest`
Expected: PASS（既有 + 新增 4 = 全绿）

- [ ] **Step 5: CR + stage**

code-review → `git add` PythonAiClient.java + PythonAiClientTest.java（不自动 commit）。

---

## Task 6: PromptService.generate

**Files:**
- Create: `acg-chat/src/main/java/com/darkness/prompt/service/PromptService.java`
- Create: `acg-chat/src/main/java/com/darkness/prompt/service/impl/PromptServiceImpl.java`
- Test: `acg-chat/src/test/java/com/darkness/prompt/service/impl/PromptServiceImplTest.java`

**Interfaces:**
- Consumes: Task 2/3/5（VO、PromptTemplateDO/Mapper、PythonAiClient.generatePrompt、ResultCode）
- Produces: `PromptService.generate(req,userId)→Result<PromptGenerateResponseVO>`。接口本任务只声明 `generate`（CRUD/apply/moderate/estimate 在 Task 7/8 追加声明）。

- [ ] **Step 1: Write the failing test**

```java
package com.darkness.prompt.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.agent.service.AgentService;
import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.PromptTemplateMapper;
import com.darkness.common.model.CostEstimateVO;
import com.darkness.common.model.ModerationVerdictVO;
import com.darkness.common.model.PromptGenerateOutcome;
import com.darkness.common.model.PromptGenerateRequest;
import com.darkness.common.model.PromptGenerateResponseVO;
import com.darkness.common.result.Result;
import com.darkness.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** PromptService 单元测试。业务意义：生成物归属当前用户；403 是业务结果不落库；Python 500 透传（Fail Loud）。 */
@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock private PromptTemplateMapper promptTemplateMapper;
    @Mock private PythonAiClient pythonAiClient;
    @Mock private AgentService agentService;
    @InjectMocks private PromptServiceImpl promptService;

    @Test
    void generate_success_persistsAsPrivateOfCurrentUser() {
        PromptGenerateRequest req = new PromptGenerateRequest();
        req.setUserHints(List.of("毒舌客服"));
        req.setMode(com.darkness.common.enums.PromptMode.ACG);
        PromptGenerateResponseVO resp = new PromptGenerateResponseVO();
        resp.setSystemPrompt("你是毒舌客服");
        CostEstimateVO est = new CostEstimateVO();
        est.setPromptTokens(120);
        resp.setEstimate(est);
        when(pythonAiClient.generatePrompt(req, 7L)).thenReturn(PromptGenerateOutcome.success(resp));
        when(promptTemplateMapper.insert(any(PromptTemplateDO.class))).thenAnswer(i -> {
            i.getArgument(0, PromptTemplateDO.class).setId(1001L);
            return 1;
        });

        Result<PromptGenerateResponseVO> r = promptService.generate(req, 7L);

        assertThat(r.getCode()).isEqualTo(200);
        ArgumentCaptor<PromptTemplateDO> captor = ArgumentCaptor.forClass(PromptTemplateDO.class);
        verify(promptTemplateMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);              // 归属当前用户
        assertThat(captor.getValue().getName()).isEqualTo("毒舌客服");         // 首条 hint 截断取名
        assertThat(captor.getValue().getEstPromptTokens()).isEqualTo(120);    // 缓存估算
        assertThat(r.getData().getTemplateId()).isEqualTo(1001L);             // Java 追加
    }

    @Test
    void generate_blocked_returns403AndDoesNotPersist() {
        PromptGenerateRequest req = new PromptGenerateRequest();
        req.setUserHints(List.of("暴力内容"));
        ModerationVerdictVO v = new ModerationVerdictVO();
        v.setPassed(false);
        when(pythonAiClient.generatePrompt(req, 7L)).thenReturn(PromptGenerateOutcome.blocked(v));

        Result<PromptGenerateResponseVO> r = promptService.generate(req, 7L);

        assertThat(r.getCode()).isEqualTo(403);
        assertThat(r.getMessage()).isEqualTo("blocked");
        assertThat(r.getData()).isSameAs(v);
        verify(promptTemplateMapper, never()).insert(any());
    }

    @Test
    void generate_python500_propagates_doesNotPersist() {
        PromptGenerateRequest req = new PromptGenerateRequest();
        req.setUserHints(List.of("x"));
        when(pythonAiClient.generatePrompt(req, 7L)).thenThrow(new BizException(500, "llm down"));

        assertThatThrownBy(() -> promptService.generate(req, 7L))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(500);
        verify(promptTemplateMapper, never()).insert(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl acg-chat -am -Dtest=PromptServiceImplTest`
Expected: 编译失败（PromptService/Impl 不存在）

- [ ] **Step 3: Write minimal implementation**

`PromptService.java`：
```java
package com.darkness.prompt.service;

import com.darkness.common.model.PromptGenerateRequest;
import com.darkness.common.model.PromptGenerateResponseVO;
import com.darkness.common.result.Result;

/**
 * 系统提示词生成与模板管理服务。
 * 编排 Python generate→moderation→estimate→落库；提供模板 CRUD 与 apply-to-agent。
 */
public interface PromptService {

    /**
     * 生成系统提示词。调 Python generate；moderation 不通过(403)则返回裁决且不落库；
     * 通过则把生成物落库为当前用户的私有模板，返回带 templateId 的响应。
     *
     * @param req    生成请求（userHints/mode/targetCapabilities）
     * @param userId 当前用户 id（落库归属 + 透传 Python）
     * @return 200 成功带响应；403 blocked 带裁决
     */
    Result<PromptGenerateResponseVO> generate(PromptGenerateRequest req, Long userId);
}
```

`PromptServiceImpl.java`：
```java
package com.darkness.prompt.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.agent.service.AgentService;
import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PromptMode;
import com.darkness.common.mapper.PromptTemplateMapper;
import com.darkness.common.model.PromptGenerateOutcome;
import com.darkness.common.model.PromptGenerateRequest;
import com.darkness.common.model.PromptGenerateResponseVO;
import com.darkness.common.result.Result;
import com.darkness.common.result.ResultCode;
import com.darkness.prompt.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提示词生成与模板管理服务实现。
 * generate：调 Python generate→403 不抛返回裁决→成功落库为当前用户私有模板。
 * 注：userId/isAdmin 由 Controller 从 UserContext 取后透传，便于纯 Mockito 单测。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final PythonAiClient pythonAiClient;
    private final AgentService agentService;

    @Override
    public Result<PromptGenerateResponseVO> generate(PromptGenerateRequest req, Long userId) {
        PromptGenerateOutcome outcome = pythonAiClient.generatePrompt(req, userId);
        if (outcome.isBlocked()) {
            // moderation 不通过：返回 403 + 裁决，不落库、不抛（镜像 Python 契约）
            return new Result<>(ResultCode.FORBIDDEN.getCode(), "blocked", outcome.getVerdict());
        }
        PromptGenerateResponseVO resp = outcome.getSuccess();
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setUserId(userId); // 生成物即当前用户的私有模板
        tpl.setName(autoName(req.getUserHints(), req.getMode()));
        tpl.setSystemPrompt(resp.getSystemPrompt());
        tpl.setMode(req.getMode() != null ? req.getMode() : PromptMode.ACG);
        tpl.setTargetCapabilities(req.getTargetCapabilities());
        if (resp.getEstimate() != null) {
            tpl.setEstPromptTokens(resp.getEstimate().getPromptTokens());
        }
        tpl.setStatus(CommonStatus.ENABLED);
        promptTemplateMapper.insert(tpl);
        resp.setTemplateId(tpl.getId()); // Java 追加，Python 响应不带
        return Result.success(resp);
    }

    /** 自动取名：首条 hint 截断 32 字；空则 "提示词-{mode}"。 */
    private String autoName(List<String> hints, PromptMode mode) {
        if (hints != null && !hints.isEmpty()) {
            String first = hints.get(0);
            if (first != null && !first.isBlank()) {
                return first.length() <= 32 ? first : first.substring(0, 32);
            }
        }
        return "提示词-" + (mode != null ? mode.getValue() : "acg");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl acg-chat -am -Dtest=PromptServiceImplTest`
Expected: PASS（3 tests）

- [ ] **Step 5: CR + stage**

code-review → `git add` PromptService.java + PromptServiceImpl.java + PromptServiceImplTest.java（不自动 commit）。

---

## Task 7: PromptService 模板 CRUD

**Files:**
- Modify: `acg-chat/.../prompt/service/PromptService.java`（加 5 方法声明）
- Modify: `acg-chat/.../prompt/service/impl/PromptServiceImpl.java`（加 5 方法 + 2 私有校验）
- Modify: `acg-chat/.../prompt/service/impl/PromptServiceImplTest.java`（加测试）

**Interfaces:**
- Produces: `listTemplates(userId,isAdmin,mode)`、`getTemplate(id,userId,isAdmin)`、`createTemplate(req,userId,isAdmin)`、`updateTemplate(id,req,userId,isAdmin)`、`deleteTemplate(id,userId,isAdmin)`。可见性矩阵：公共人人可读/仅 admin 可写；私有 owner 或 admin 可读写。

- [ ] **Step 1: Write the failing test**

在 `PromptServiceImplTest.java` 顶部 import 追加：
```java
import com.darkness.common.entity.PromptTemplateDO;
import com.darkness.common.enums.CommonStatus;
import com.darkness.common.enums.PromptMode;
import com.darkness.common.exception.BizException;
import com.darkness.common.model.PromptTemplateRequest;
import com.darkness.common.model.PromptTemplateVO;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.ServiceHelper;
```
在类内追加测试方法：
```java
    // ==================== listTemplates ====================

    @Test
    void listTemplates_nonAdmin_mapsToDoVO() {
        // 注意：WHERE 可见性过滤由 LambdaQueryWrapper 表达，单测 mock selectList(any) 无法验证 SQL；
        // 此处验证 DO→VO 映射 + 分支调用；可见性 SQL 正确性由集成/手动测试覆盖（与 ChatServiceImplTest 同惯例）。
        PromptTemplateDO pub = new PromptTemplateDO();
        pub.setId(1L); pub.setUserId(null); pub.setName("P");
        PromptTemplateDO priv = new PromptTemplateDO();
        priv.setId(2L); priv.setUserId(7L); priv.setName("Q");
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(pub, priv));

        List<PromptTemplateVO> r = promptService.listTemplates(7L, false, null);

        assertThat(r).hasSize(2);
        assertThat(r.get(0).getIsPublic()).isTrue();
        assertThat(r.get(1).getIsPublic()).isFalse();
    }

    // ==================== getTemplate ====================

    @Test
    void getTemplate_notFound_throws404() {
        when(promptTemplateMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> promptService.getTemplate(999L, 1L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void getTemplate_otherUserPrivate_forbidden_403() {
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(1L); tpl.setUserId(1L); // 别人的私有
        when(promptTemplateMapper.selectById(1L)).thenReturn(tpl);
        assertThatThrownBy(() -> promptService.getTemplate(1L, 999L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    void getTemplate_public_readableByAnyone() {
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(1L); tpl.setUserId(null); // 公共
        when(promptTemplateMapper.selectById(1L)).thenReturn(tpl);
        assertThat(promptService.getTemplate(1L, 999L, false).getIsPublic()).isTrue();
    }

    // ==================== createTemplate ====================

    @Test
    void createTemplate_nonAdminSetPublic_forbidden_403() {
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s"); req.setIsPublic(true);
        assertThatThrownBy(() -> promptService.createTemplate(req, 7L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(promptTemplateMapper, never()).insert(any());
    }

    @Test
    void createTemplate_adminPublic_userIdNull() {
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s"); req.setIsPublic(true);
        when(promptTemplateMapper.insert(any(PromptTemplateDO.class))).thenAnswer(i -> {
            i.getArgument(0, PromptTemplateDO.class).setId(1L);
            return 1;
        });
        ArgumentCaptor<PromptTemplateDO> captor = ArgumentCaptor.forClass(PromptTemplateDO.class);
        promptService.createTemplate(req, 1L, true);
        verify(promptTemplateMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull(); // 公共→NULL
    }

    @Test
    void createTemplate_userPrivate_userIdCurrentUser() {
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s"); // isPublic 未置
        when(promptTemplateMapper.insert(any(PromptTemplateDO.class))).thenAnswer(i -> {
            i.getArgument(0, PromptTemplateDO.class).setId(2L);
            return 1;
        });
        ArgumentCaptor<PromptTemplateDO> captor = ArgumentCaptor.forClass(PromptTemplateDO.class);
        promptService.createTemplate(req, 7L, false);
        verify(promptTemplateMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L); // 私有→当前用户
    }

    // ==================== updateTemplate / deleteTemplate ====================

    @Test
    void updateTemplate_otherUserPrivate_forbidden_403() {
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(1L); tpl.setUserId(1L); // 别人的私有
        when(promptTemplateMapper.selectById(1L)).thenReturn(tpl);
        PromptTemplateRequest req = new PromptTemplateRequest();
        req.setName("N"); req.setSystemPrompt("s");
        assertThatThrownBy(() -> promptService.updateTemplate(1L, req, 999L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    void deleteTemplate_publicByNonAdmin_forbidden_403() {
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(1L); tpl.setUserId(null); // 公共
        when(promptTemplateMapper.selectById(1L)).thenReturn(tpl);
        assertThatThrownBy(() -> promptService.deleteTemplate(1L, 7L, false))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(promptTemplateMapper, never()).deleteById(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl acg-chat -am -Dtest=PromptServiceImplTest`
Expected: 编译失败（5 方法不存在）

- [ ] **Step 3: Write minimal implementation**

在 `PromptService.java` 接口追加：
```java
    /**
     * 列出当前用户可见的模板：非 admin=自己的私有+全部公共；admin=全部。可按 mode 过滤。
     *
     * @param userId  当前用户 id
     * @param isAdmin 是否管理员
     * @param mode    可选 mode 过滤
     * @return 可见模板 VO 列表（按创建时间倒序）
     */
    java.util.List<com.darkness.common.model.PromptTemplateVO> listTemplates(Long userId, boolean isAdmin, com.darkness.common.enums.PromptMode mode);

    /**
     * 查询单条模板，做可见性校验。不存在抛 404，越权抛 403。
     */
    com.darkness.common.model.PromptTemplateVO getTemplate(Long id, Long userId, boolean isAdmin);

    /**
     * 手建模板。非 admin 建私有(user_id=当前)；admin 可 isPublic=true 建公共(user_id=NULL)。
     * 非 admin 置 isPublic=true 抛 403。
     */
    com.darkness.common.model.PromptTemplateVO createTemplate(com.darkness.common.model.PromptTemplateRequest req, Long userId, boolean isAdmin);

    /**
     * 更新模板（owner 或 admin）。admin 可 isPublic=true 开放为公共。越权抛 403，不存在抛 404。
     */
    com.darkness.common.model.PromptTemplateVO updateTemplate(Long id, com.darkness.common.model.PromptTemplateRequest req, Long userId, boolean isAdmin);

    /**
     * 逻辑删除模板（owner 或 admin）。越权抛 403，不存在抛 404。
     */
    void deleteTemplate(Long id, Long userId, boolean isAdmin);
```

在 `PromptServiceImpl.java` import 追加：
```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.darkness.common.exception.BizException;
import com.darkness.common.model.PromptTemplateDO;       // 与 entity.PromptTemplateDO 同一，省略若已 import
import com.darkness.common.model.PromptTemplateRequest;
import com.darkness.common.model.PromptTemplateVO;
import com.darkness.common.util.ServiceHelper;
```
> 注意：`PromptTemplateDO` 在 `entity` 包，上面 import 写 `com.darkness.common.entity.PromptTemplateDO`（Task 6 已 import）；此处勿重复。仅新增 `LambdaQueryWrapper`、`PromptTemplateRequest`、`PromptTemplateVO`、`ServiceHelper`、`BizException`。

在 `PromptServiceImpl` 类内（`autoName` 之后）追加：
```java
    @Override
    public java.util.List<PromptTemplateVO> listTemplates(Long userId, boolean isAdmin, PromptMode mode) {
        LambdaQueryWrapper<PromptTemplateDO> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(PromptTemplateDO::getCreatedAt);
        if (mode != null) qw.eq(PromptTemplateDO::getMode, mode);
        if (!isAdmin) {
            // 非管理员：自己的私有 OR 全部公共
            qw.and(w -> w.eq(PromptTemplateDO::getUserId, userId)
                    .or().isNull(PromptTemplateDO::getUserId));
        }
        return promptTemplateMapper.selectList(qw).stream().map(PromptTemplateVO::from).toList();
    }

    @Override
    public PromptTemplateVO getTemplate(Long id, Long userId, boolean isAdmin) {
        PromptTemplateDO tpl = ServiceHelper.findOrThrow(promptTemplateMapper.selectById(id), "PromptTemplate", id);
        checkReadable(tpl, userId, isAdmin);
        return PromptTemplateVO.from(tpl);
    }

    @Override
    public PromptTemplateVO createTemplate(PromptTemplateRequest req, Long userId, boolean isAdmin) {
        boolean makePublic = Boolean.TRUE.equals(req.getIsPublic());
        if (makePublic && !isAdmin) {
            throw new BizException(ResultCode.FORBIDDEN, "仅管理员可创建公共模板");
        }
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setUserId(makePublic ? null : userId); // 公共→NULL；私有→当前用户
        tpl.setName(req.getName());
        tpl.setSystemPrompt(req.getSystemPrompt());
        tpl.setMode(req.getMode() != null ? req.getMode() : PromptMode.ACG);
        tpl.setTargetCapabilities(req.getTargetCapabilities());
        tpl.setStatus(CommonStatus.ENABLED);
        promptTemplateMapper.insert(tpl);
        return PromptTemplateVO.from(tpl);
    }

    @Override
    public PromptTemplateVO updateTemplate(Long id, PromptTemplateRequest req, Long userId, boolean isAdmin) {
        PromptTemplateDO existing = ServiceHelper.findOrThrow(promptTemplateMapper.selectById(id), "PromptTemplate", id);
        checkWritable(existing, userId, isAdmin);
        boolean makePublic = Boolean.TRUE.equals(req.getIsPublic());
        if (makePublic && !isAdmin) {
            throw new BizException(ResultCode.FORBIDDEN, "仅管理员可开放公共模板");
        }
        if (makePublic) existing.setUserId(null); // admin 开放为公共；非 admin 不改归属
        existing.setName(req.getName());
        existing.setSystemPrompt(req.getSystemPrompt());
        existing.setMode(req.getMode() != null ? req.getMode() : existing.getMode());
        existing.setTargetCapabilities(req.getTargetCapabilities());
        promptTemplateMapper.updateById(existing);
        return PromptTemplateVO.from(promptTemplateMapper.selectById(id));
    }

    @Override
    public void deleteTemplate(Long id, Long userId, boolean isAdmin) {
        PromptTemplateDO existing = ServiceHelper.findOrThrow(promptTemplateMapper.selectById(id), "PromptTemplate", id);
        checkWritable(existing, userId, isAdmin);
        promptTemplateMapper.deleteById(id);
    }

    /** 可读性：公共人人可读；私有仅 owner 或 admin，否则 403。 */
    private void checkReadable(PromptTemplateDO tpl, Long userId, boolean isAdmin) {
        if (tpl.getUserId() == null) return; // 公共
        if (isAdmin || tpl.getUserId().equals(userId)) return;
        throw new BizException(ResultCode.FORBIDDEN, "无权访问该模板");
    }

    /** 可写性：公共仅 admin；私有仅 owner 或 admin，否则 403。 */
    private void checkWritable(PromptTemplateDO tpl, Long userId, boolean isAdmin) {
        if (tpl.getUserId() == null && !isAdmin) {
            throw new BizException(ResultCode.FORBIDDEN, "仅管理员可修改公共模板");
        }
        if (tpl.getUserId() != null && !isAdmin && !tpl.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权修改该模板");
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl acg-chat -am -Dtest=PromptServiceImplTest`
Expected: PASS（Task 6 的 3 + 本任务 9 = 12 tests）

- [ ] **Step 5: CR + stage**

code-review → `git add` PromptService.java + PromptServiceImpl.java + PromptServiceImplTest.java（不自动 commit）。

---

## Task 8: apply-to-agent + moderate/estimate 转发

**Files:**
- Modify: `acg-chat/.../prompt/service/PromptService.java`（加 3 方法声明）
- Modify: `acg-chat/.../prompt/service/impl/PromptServiceImpl.java`（加 3 方法）
- Modify: `acg-chat/.../prompt/service/impl/PromptServiceImplTest.java`（加测试）

**Interfaces:**
- Consumes: `AgentService.getAgentById`/`updateAgent`（既有）、Task 5 的 `moderatePrompt`/`estimatePrompt`
- Produces: `moderate(req,userId)`、`estimate(req,userId)`、`applyToAgent(templateId,agentId)`（admin-only，由 Controller 注解保证）。apply 只搬 system_prompt，复用 AgentService.updateAgent 同步 Python。

- [ ] **Step 1: Write the failing test**

在 `PromptServiceImplTest.java` import 追加：
```java
import com.darkness.common.model.AgentVO;
import com.darkness.common.model.PromptEstimateRequest;
import com.darkness.common.model.PromptModerateRequest;
import com.darkness.common.model.ModerationVerdictVO;
import com.darkness.common.model.CostEstimateVO;
import static org.mockito.ArgumentMatchers.eq;
```
在类内追加测试方法：
```java
    // ==================== moderate / estimate ====================

    @Test
    void moderate_forwardsToPython() {
        PromptModerateRequest req = new PromptModerateRequest();
        req.setSystemPrompt("x"); req.setMode(PromptMode.ACG);
        ModerationVerdictVO v = new ModerationVerdictVO(); v.setPassed(true);
        when(pythonAiClient.moderatePrompt(req, 7L)).thenReturn(v);
        assertThat(promptService.moderate(req, 7L)).isSameAs(v);
    }

    @Test
    void estimate_forwardsToPython() {
        PromptEstimateRequest req = new PromptEstimateRequest();
        req.setSystemPrompt("x");
        CostEstimateVO e = new CostEstimateVO(); e.setPromptTokens(50);
        when(pythonAiClient.estimatePrompt(req, 7L)).thenReturn(e);
        assertThat(promptService.estimate(req, 7L).getPromptTokens()).isEqualTo(50);
    }

    // ==================== applyToAgent ====================

    @Test
    void applyToAgent_updatesAgentSystemPromptAndSyncs() {
        PromptTemplateDO tpl = new PromptTemplateDO();
        tpl.setId(3L); tpl.setSystemPrompt("你是客服");
        when(promptTemplateMapper.selectById(3L)).thenReturn(tpl);
        AgentVO agent = new AgentVO(); agent.setId(9L); agent.setSystemPrompt("old");
        when(agentService.getAgentById(9L)).thenReturn(agent);
        when(agentService.updateAgent(eq(9L), any(AgentVO.class))).thenReturn(agent);

        promptService.applyToAgent(3L, 9L);

        ArgumentCaptor<AgentVO> captor = ArgumentCaptor.forClass(AgentVO.class);
        verify(agentService).updateAgent(eq(9L), captor.capture());
        assertThat(captor.getValue().getSystemPrompt()).isEqualTo("你是客服"); // 只搬 system_prompt
    }

    @Test
    void applyToAgent_templateNotFound_throws404() {
        when(promptTemplateMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> promptService.applyToAgent(99L, 9L))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
        verify(agentService, never()).getAgentById(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl acg-chat -am -Dtest=PromptServiceImplTest`
Expected: 编译失败（moderate/estimate/applyToAgent 不存在）

- [ ] **Step 3: Write minimal implementation**

在 `PromptService.java` 接口追加：
```java
    /** 独立 moderation 校验，转发 Python。 */
    com.darkness.common.model.ModerationVerdictVO moderate(com.darkness.common.model.PromptModerateRequest req, Long userId);

    /** 独立消耗估算，转发 Python。 */
    com.darkness.common.model.CostEstimateVO estimate(com.darkness.common.model.PromptEstimateRequest req, Long userId);

    /**
     * 把模板的 system_prompt 应用到指定 Agent 并同步 Python（admin-only，由 Controller @RequireRole 保证）。
     * 只搬 system_prompt，不动 mode/capabilities；复用 AgentService.updateAgent 的 Python 同步。
     * 模板/Agent 不存在分别抛 404。
     */
    void applyToAgent(Long templateId, Long agentId);
```

在 `PromptServiceImpl.java` import 追加（若未存在）：
```java
import com.darkness.common.model.AgentVO;
import com.darkness.common.model.CostEstimateVO;
import com.darkness.common.model.ModerationVerdictVO;
import com.darkness.common.model.PromptEstimateRequest;
import com.darkness.common.model.PromptModerateRequest;
```
在类内（`checkWritable` 之后）追加：
```java
    @Override
    public ModerationVerdictVO moderate(PromptModerateRequest req, Long userId) {
        return pythonAiClient.moderatePrompt(req, userId);
    }

    @Override
    public CostEstimateVO estimate(PromptEstimateRequest req, Long userId) {
        return pythonAiClient.estimatePrompt(req, userId);
    }

    @Override
    public void applyToAgent(Long templateId, Long agentId) {
        // Controller 已 @RequireRole("admin") 限定；admin 可取任意模板
        PromptTemplateDO tpl = ServiceHelper.findOrThrow(
                promptTemplateMapper.selectById(templateId), "PromptTemplate", templateId);
        AgentVO vo = agentService.getAgentById(agentId); // 不存在抛 404
        vo.setSystemPrompt(tpl.getSystemPrompt());       // 只搬 system_prompt
        agentService.updateAgent(agentId, vo);           // 复用既有 Python 同步
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl acg-chat -am -Dtest=PromptServiceImplTest`
Expected: PASS（全部累计 tests）

- [ ] **Step 5: CR + stage**

code-review → `git add` 3 个文件（不自动 commit）。

---

## Task 9: PromptController（wiring）

**Files:**
- Create: `acg-chat/src/main/java/com/darkness/prompt/controller/PromptController.java`

**Interfaces:**
- Consumes: `PromptService` 全部方法、`UserContext.getUserId()`/`isAdmin()`
- Produces: `/api/prompts` 下 9 个端点（见 spec §7）。

> 约定：本项目 Controller 无单测（AgentController/ChatController 等均无；HTTP 契约由 Service 层单测保证）。本任务验证=编译 + 启动后手动 curl（Python 未实现时 generate/moderate/estimate 会因 Python 端 404/连接失败返回 503/500，属预期；模板 CRUD 可对真实 DB 手测）。

- [ ] **Step 1: Write the implementation**

```java
package com.darkness.prompt.controller;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.enums.PromptMode;
import com.darkness.common.model.CostEstimateVO;
import com.darkness.common.model.ModerationVerdictVO;
import com.darkness.common.model.PromptEstimateRequest;
import com.darkness.common.model.PromptGenerateRequest;
import com.darkness.common.model.PromptGenerateResponseVO;
import com.darkness.common.model.PromptModerateRequest;
import com.darkness.common.model.PromptTemplateRequest;
import com.darkness.common.model.PromptTemplateVO;
import com.darkness.common.result.Result;
import com.darkness.common.util.UserContext;
import com.darkness.prompt.service.PromptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统提示词生成与模板管理 REST 控制器，提供 /api/prompts 下的接口。
 * 普通用户：生成/手建私有模板、读公共+自己私有、moderate/estimate；
 * 管理员：额外可开放公共模板、apply-to-agent。userId/isAdmin 由 UserContext 取后透传 Service。
 */
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    /**
     * 生成系统提示词（调 Python generate→校验→估算→落库为当前用户私有模板）。
     * POST /api/prompts/generate（需登录）
     *
     * @param req 生成请求（userHints/mode/targetCapabilities）
     * @return 200 成功带响应（含 templateId）；403 blocked 带裁决
     */
    @PostMapping("/generate")
    public Result<PromptGenerateResponseVO> generate(@Valid @RequestBody PromptGenerateRequest req) {
        return promptService.generate(req, UserContext.getUserId());
    }

    /**
     * 独立 moderation 校验（复检已存/手写 system_prompt）。
     * POST /api/prompts/moderate（需登录）
     */
    @PostMapping("/moderate")
    public Result<ModerationVerdictVO> moderate(@Valid @RequestBody PromptModerateRequest req) {
        return Result.success(promptService.moderate(req, UserContext.getUserId()));
    }

    /**
     * 独立消耗估算。
     * POST /api/prompts/estimate（需登录）
     */
    @PostMapping("/estimate")
    public Result<CostEstimateVO> estimate(@Valid @RequestBody PromptEstimateRequest req) {
        return Result.success(promptService.estimate(req, UserContext.getUserId()));
    }

    /**
     * 列出可见模板（非 admin=公共+自己私有；admin=全部），可按 mode 过滤。
     * GET /api/prompts/templates（需登录）
     */
    @GetMapping("/templates")
    public Result<List<PromptTemplateVO>> list(@RequestParam(required = false) PromptMode mode) {
        return Result.success(promptService.listTemplates(UserContext.getUserId(), UserContext.isAdmin(), mode));
    }

    /**
     * 查询单条模板。越权 403，不存在 404。
     * GET /api/prompts/templates/{id}（需登录）
     */
    @GetMapping("/templates/{id}")
    public Result<PromptTemplateVO> get(@PathVariable Long id) {
        return Result.success(promptService.getTemplate(id, UserContext.getUserId(), UserContext.isAdmin()));
    }

    /**
     * 手建模板（用户建私有；admin 可 isPublic=true 建公共）。
     * POST /api/prompts/templates（需登录）
     */
    @PostMapping("/templates")
    public Result<PromptTemplateVO> create(@Valid @RequestBody PromptTemplateRequest req) {
        return Result.success(promptService.createTemplate(req, UserContext.getUserId(), UserContext.isAdmin()));
    }

    /**
     * 更新模板（owner/admin；admin 可 isPublic=true 开放为公共）。
     * PUT /api/prompts/templates/{id}（需登录）
     */
    @PutMapping("/templates/{id}")
    public Result<PromptTemplateVO> update(@PathVariable Long id, @Valid @RequestBody PromptTemplateRequest req) {
        return Result.success(promptService.updateTemplate(id, req, UserContext.getUserId(), UserContext.isAdmin()));
    }

    /**
     * 逻辑删除模板（owner/admin）。
     * DELETE /api/prompts/templates/{id}（需登录）
     */
    @DeleteMapping("/templates/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptService.deleteTemplate(id, UserContext.getUserId(), UserContext.isAdmin());
        return Result.success();
    }

    /**
     * 把模板 system_prompt 应用到 Agent 并同步 Python。POST /api/prompts/templates/{id}/apply/{agentId}（仅管理员）
     *
     * @param id      模板 id
     * @param agentId 目标 Agent id
     */
    @PostMapping("/templates/{id}/apply/{agentId}")
    @RequireRole("admin")
    public Result<Void> apply(@PathVariable Long id, @PathVariable Long agentId) {
        promptService.applyToAgent(id, agentId);
        return Result.success();
    }
}
```

- [ ] **Step 2: Compile the whole acg-chat module**

Run: `mvn clean compile -pl acg-chat -am`
Expected: BUILD SUCCESS（无编译错误）

- [ ] **Step 3: Run all acg-chat tests to confirm no regressions**

Run: `mvn test -pl acg-chat -am`
Expected: 全绿（含 PromptServiceImplTest、PythonAiClientTest 等既有测试）

- [ ] **Step 4: Manual smoke（模板 CRUD，对真实 DB）**

启动 acg-chat（依赖 MySQL/Nacos）。用带 admin token 的请求测：
- `POST /api/prompts/templates`（body `{"name":"t","systemPrompt":"你是客服","mode":"acg","isPublic":true}`，admin token）→ 200，返回 id。
- `GET /api/prompts/templates`（普通用户 token）→ 列表含该公共模板。
- `GET /api/prompts/templates/{id}`（另一普通用户）→ 200（公共可读）。
- `POST /api/prompts/templates/{id}/apply/{agentId}`（admin token）→ 200，Agent system_prompt 被更新。
Expected: 上述通过。generate/moderate/estimate 因 Python 未实现会返回 503/500（预期，不阻断）。

- [ ] **Step 5: CR + stage**

code-review → `git add PromptController.java`（不自动 commit）。

---

## Task 10: Gateway 路由 + 变更日志

**Files:**
- Modify: Nacos 配置 `acg-gateway.yaml`（dataId，命名空间 acg_agent / DEFAULT_GROUP）—— 路由 predicate 加 `/api/prompts/**`
- Modify: `docx/nacos_config/extracted/DEFAULT_GROUP/acg-gateway.yaml`（本地镜像同步，文档一致性）
- Create: `docs/changelogs/2026-06-21-prompt-template.md`

**Interfaces:** 无代码接口；运维配置 + 文档。

> 现状：`acg-gateway.yaml` 的 `agent-service` 路由 predicate 为 `Path=/api/agents/**,/api/chat/**`，**未含 `/api/prompts/**`**，不加则网关 404。`JwtAuthFilter` 已注入 `X-User-Roles` 且 `/api/prompts/**` 非白名单（需登录，符合预期），无需改 filter。

- [ ] **Step 1: 更新 Nacos `acg-gateway.yaml` 路由**

在 Nacos 控制台编辑 `acg-gateway.yaml`（命名空间 acg_agent / DEFAULT_GROUP），将 `agent-service` 路由的 predicate 改为：
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://acg-user
          predicates:
            - Path=/api/auth/**,/api/users/**,/api/roles/**,/api/permissions/**,/druid/**
        - id: agent-service
          uri: lb://acg-chat
          predicates:
            - Path=/api/agents/**,/api/chat/**,/api/prompts/**
```
（仅追加 `,/api/prompts/**` 到 agent-service 的 Path；其余不变。）

- [ ] **Step 2: 同步本地镜像**

将 `docx/nacos_config/extracted/DEFAULT_GROUP/acg-gateway.yaml` 的 `agent-service` predicate 同步为上述内容（保持文档与 Nacos 一致）。

- [ ] **Step 3: 写变更日志**

创建 `docs/changelogs/2026-06-21-prompt-template.md`：
```markdown
# 2026-06-21 系统提示词模板功能（Java 侧）

## 变更原因
为 acgagent-ai「系统提示词生成」提供 Java 侧落地：调 Python prompt 接口 + 模板库（公共/私有双维度）CRUD + apply-to-agent。对应 spec `docs/superpowers/specs/2026-06-21-prompt-generation-java-design.md`。

## 影响范围
- **数据库**：新增表 `prompt_template`（acg-user/schema.sql）。
- **API（新增，/api/prompts）**：generate / moderate / estimate / templates CRUD / apply-to-agent。
- **网关**：`acg-gateway.yaml` 的 agent-service 路由新增 `/api/prompts/**`。
- **公共代码**：`UserContext` 新增 `getRoles()/isAdmin()`；`PythonAiClient` 新增 Prompt 段。

## 变更前后对比
- 路由 predicate：`/api/agents/**,/api/chat/**` → 追加 `,/api/prompts/**`。
- 新表 `prompt_template`：user_id=NULL 公共 / 非 NULL 私有。
- 403 语义：generate 被 moderation 拦截时返回 `Result(403,"blocked",ModerationVerdict)`，区别于鉴权 403(Forbidden)。

## 注意点
- **Python 侧（acgagent-ai）prompt 接口尚未实现**；generate/moderate/estimate 在 Python 落地前会返回 503/500，模板 CRUD 与 apply-to-agent 可独立使用。
- `/api/prompts/**` 需登录（非白名单）；apply-to-agent 仅 admin。
- `UserContext.isAdmin()` 依赖网关 `X-User-Roles` 头（JwtAuthFilter 已注入）。
- 部署时必须在 Nacos 更新 `acg-gateway.yaml`，否则 /api/prompts 网关 404。
```

- [ ] **Step 4: 验证路由生效**

更新 Nacos 后（refresh 或重启 acg-gateway），对 `/api/prompts/templates` 发请求：
- 无 token → 401（JwtAuthFilter）。
- 带 token → 路由到 acg-chat（不再 404）。
Expected: 路由通到 acg-chat。

- [ ] **Step 5: CR + stage**

code-review → `git add docx/nacos_config/extracted/DEFAULT_GROUP/acg-gateway.yaml docs/changelogs/2026-06-21-prompt-template.md`（Nacos 配置在控制台改，不在 git；不自动 commit）。

---

## Self-Review（写计划后自检）

**1. Spec 覆盖**：
- §3 文件分布 → Task 1-9 全覆盖（DO/Mapper/VO/枚举/Service/Controller/PythonAiClient/UserContext/schema）✓
- §4 数据模型 → Task 3 DDL + PromptMode ✓
- §5 PythonAiClient 契约（含 403 不抛、snake_case）→ Task 5 ✓
- §6 权限模型（getRoles/isAdmin + 矩阵）→ Task 4 + Task 7（checkReadable/checkWritable）✓
- §7 全部 9 端点 → Task 9 ✓
- §8 generate/apply/CRUD 编排 → Task 6/7/8 ✓
- §9 错误处理（403 blocked/400 mode/500 python/404/403 越权）→ Task 5/6/7/8/9 ✓
- §10 测试用例（含 generate blocked/owner 隔离/apply/public 开放）→ Task 6/7/8 ✓
- §11 surgical 改动 → UserContext(Task4)/schema(Task3)/PythonAiClient(Task5)，其余全新 ✓
- §12 开放项（X-User-Roles 已确认注入；changelog；gateway 路由）→ Task 10 ✓

**2. 占位符扫描**：无 TBD/TODO；每个代码步骤含完整代码；命令含预期输出。Controller 无单测已显式说明原因（项目惯例），非静默跳过 ✓

**3. 类型/签名一致性**：
- `PromptMode.getValue()` 一致用于请求体（Task 5 buildPromptGenerateBody）✓
- `PromptGenerateOutcome.success/blocked/isBlocked/getSuccess/getVerdict` 在 Task 2 定义、Task 5/6 使用一致 ✓
- `generatePrompt/parseGenerateOutcome/buildPromptGenerateBody/moderatePrompt/estimatePrompt` 在 Task 5 定义、Task 6/8 使用一致 ✓
- Service 方法签名 `(req, userId)` / `(id, userId, isAdmin)` / `(templateId, agentId)` 在 Task 6/7/8 接口与 Task 9 Controller 调用一致 ✓
- `PromptTemplateVO.from`/`getIsPublic` 在 Task 3 定义、Task 7 使用一致 ✓
- `ServiceHelper.findOrThrow(mapper.selectById(id), "PromptTemplate", id)` 与既有 AgentServiceImpl 用法一致 ✓

**已知限制（已在计划中显式标注，非缺口）**：
- `listTemplates` 的 WHERE 可见性过滤正确性单测无法验证（mock selectList），由集成/手测覆盖——与 `ChatServiceImplTest` 同惯例。
- generate/moderate/estimate 端到端依赖 Python 落地；此前返回 503/500（预期）。
