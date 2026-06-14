# Java ↔ Python AI 集成 Phase A 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Java acg-chat 的对话流改走 Python AI 引擎，并让 Java 成为 Agent 配置的真相源（CUD 同步 Python），删除直连 LLM 的旧逻辑。

**Architecture:** 新增 `PythonAiClient`（WebClient）作为唯一 Python 出口；`AgentDO` 扩展丰富配置字段 + `python_agent_id` 锚点；`AgentServiceImpl` 的 CUD 用 `@Transactional` 包裹同步、失败回滚；`ChatServiceImpl.sendMessage` 改为调用 Python 的 SSE 结构化对话并透传 `ChatEvent`。详见 `docs/superpowers/specs/2026-06-14-java-python-ai-integration-design.md`。

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring WebFlux (WebClient + ServerSentEvent), MyBatis-Plus 3.5.9 (JacksonTypeHandler), jjwt/Jackson, JUnit5 + Mockito + AssertJ。

---

## 全局约定（每个 Task 都要遵守）

1. **分支**：在 `acgagent_dev` 分支工作（项目规约九.1）。
2. **Code Review**：每个 Task 的 `git add` **之前**必须先运行 `/code-review` skill 审查本 Task 变更；CR 发现的问题修复后才能 `git add`（规约八.1）。
3. **不自动 commit**：本计划只执行 `git add`，**不执行 `git commit`**。提交时机和消息由开发者决定（规约九.2）。
4. **测试命令**：acg-chat 单测用 `mvn test -pl acg-chat -am -Dtest=<类名>`（`-am` 重建被改动的 acg-common）；acg-common 单测用 `mvn test -pl acg-common -Dtest=<类名>`。
5. **Phase B（KB/Doc/Tool 代理）不在此计划**，单独成文。

---

## File Structure

| 文件 | 责任 | 动作 |
|---|---|---|
| `acg-common/src/main/java/com/darkness/common/result/ResultCode.java` | 响应码枚举 | 改：加 `SERVICE_UNAVAILABLE(503)` |
| `acg-user/src/main/resources/db/schema.sql` | 建表 DDL | 改：agent 表加列 |
| `acg-common/src/main/java/com/darkness/common/entity/AgentDO.java` | Agent 实体 | 改：加字段 + JSON TypeHandler |
| `acg-common/src/main/java/com/darkness/common/model/AgentVO.java` | Agent VO | 改：加字段 + from/to |
| `acg-common/src/main/java/com/darkness/common/model/ChatEvent.java` | SSE 事件 DTO（前端契约） | 新建 |
| `acg-chat/src/main/java/com/darkness/config/PythonAgentProperties.java` | python-agent 配置 | 新建 |
| `acg-chat/src/main/java/com/darkness/config/WebClientConfig.java` | WebClient Bean | 改：pythonWebClient（带连接超时） |
| `acg-chat/src/main/java/com/darkness/agent/client/PythonAiClient.java` | Python HTTP 出口 | 新建 |
| `acg-chat/src/main/java/com/darkness/agent/client/AgentClient.java` | 旧直连 LLM | **删除** |
| `acg-chat/src/main/java/com/darkness/agent/service/impl/AgentServiceImpl.java` | Agent CUD + 同步 | 改 |
| `acg-chat/src/main/java/com/darkness/agent/service/impl/ChatServiceImpl.java` | 对话 SSE | 改 |
| `acg-chat/src/main/java/com/darkness/agent/migration/AgentSyncMigrationRunner.java` | 存量迁移 | 新建 |
| `docs/changelogs/2026-06-14-agent-python-sync.md` | 变更日志 | 新建 |

---

## Task 1：agent 表加列 + 变更日志（DDL）

**Files:**
- Modify: `acg-user/src/main/resources/db/schema.sql`（agent 表 CREATE 段）
- Create: `docs/changelogs/2026-06-14-agent-python-sync.md`

> DDL 无单元测试；以应用启动 + Task 10 迁移脚本能跑通为验证。

- [ ] **Step 1：修改 schema.sql 的 agent 表**

把 `CREATE TABLE IF NOT EXISTS agent (...)` 整段替换为（在 `category` 之后、`deleted` 之前插入新列；`description` 已存在，不重复加）：

```sql
CREATE TABLE IF NOT EXISTS agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    name VARCHAR(128) NOT NULL COMMENT '智能体名称，用于前端展示',
    description VARCHAR(512) COMMENT '智能体功能描述',
    avatar VARCHAR(512) COMMENT '头像图片 URL',
    api_url VARCHAR(512) NOT NULL COMMENT '外部 LLM API 的完整地址',
    api_key VARCHAR(512) NOT NULL COMMENT '调用外部 API 的密钥',
    model VARCHAR(128) COMMENT '模型标识',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    config_json TEXT COMMENT '额外配置参数（已废弃，改用下方独立列），保留以向前兼容',
    category VARCHAR(32) NOT NULL DEFAULT 'CHAT' COMMENT 'Agent分类',
    system_prompt TEXT COMMENT '系统提示词，传给 Python 作为 Agent system_prompt',
    provider VARCHAR(32) COMMENT 'LLM 厂商：doubao/qwen/deepseek',
    temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT '生成温度',
    max_tokens INT DEFAULT 4096 COMMENT '最大输出 token',
    top_p DECIMAL(3,2) DEFAULT 0.90 COMMENT 'Top-P 采样',
    memory_type VARCHAR(32) NOT NULL DEFAULT 'conversation_window' COMMENT '记忆类型：conversation_window/summary/none',
    memory_max_tokens INT DEFAULT 8000 COMMENT '上下文窗口 token 数',
    capabilities JSON COMMENT '能力标签数组，如 ["chat","rag","tool_use"]',
    knowledge_base_ids JSON COMMENT '关联知识库 id 列表（Python 侧 string id）',
    tool_ids JSON COMMENT '关联工具 id 列表（Python 侧 string id）',
    python_agent_id VARCHAR(64) COMMENT '同步锚点：Python 侧 agent 的 string id，未同步时为 NULL',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT 'AI 智能体配置表';
```

> 存量库执行迁移时，对已存在的表用 `ALTER TABLE agent ADD COLUMN ...`（schema.sql 仅用于全新建库）。在变更日志里给出 ALTER 语句。

- [ ] **Step 2：创建变更日志** `docs/changelogs/2026-06-14-agent-python-sync.md`

```markdown
# 2026-06-14 Agent 表扩展：接入 Python AI 引擎

## 变更原因
Java acg-chat 对话流改为调用 Python acgagent-ai 引擎（spec：docs/superpowers/specs/2026-06-14-java-python-ai-integration-design.md）。Java 成为 Agent 配置真相源，需存储 Python 所需的丰富配置与同步锚点。

## 影响范围
- 数据库：agent 表新增 11 列（description 已存在，未重复加）。
- API：无破坏性变更；Agent CUD 行为变为"同步推送 Python"。
- 架构：删除 Java 直连 LLM 的 AgentClient，对话流改走 Python。

## 变更前后对比
新增列：system_prompt / provider / temperature / max_tokens / top_p / memory_type / memory_max_tokens / capabilities(JSON) / knowledge_base_ids(JSON) / tool_ids(JSON) / python_agent_id。
config_json 标记废弃但保留（向前兼容，不删字段语义）。

## 存量迁移
对已存在的 agent 表执行（幂等）：

​```sql
ALTER TABLE agent
  ADD COLUMN IF NOT EXISTS system_prompt TEXT COMMENT '系统提示词' AFTER category,
  ADD COLUMN IF NOT EXISTS provider VARCHAR(32) COMMENT 'LLM 厂商' AFTER system_prompt,
  ADD COLUMN IF NOT EXISTS temperature DECIMAL(3,2) DEFAULT 0.70 AFTER provider,
  ADD COLUMN IF NOT EXISTS max_tokens INT DEFAULT 4096 AFTER temperature,
  ADD COLUMN IF NOT EXISTS top_p DECIMAL(3,2) DEFAULT 0.90 AFTER max_tokens,
  ADD COLUMN IF NOT EXISTS memory_type VARCHAR(32) NOT NULL DEFAULT 'conversation_window' AFTER top_p,
  ADD COLUMN IF NOT EXISTS memory_max_tokens INT DEFAULT 8000 AFTER memory_type,
  ADD COLUMN IF NOT EXISTS capabilities JSON AFTER memory_max_tokens,
  ADD COLUMN IF NOT EXISTS knowledge_base_ids JSON AFTER capabilities,
  ADD COLUMN IF NOT EXISTS tool_ids JSON AFTER knowledge_base_ids,
  ADD COLUMN IF NOT EXISTS python_agent_id VARCHAR(64) AFTER tool_ids;
​```

执行后由 Task 10 的迁移程序为每条存量 agent 创建 Python agent 并回写 python_agent_id。

## 注意
- 存量 agent 在 python_agent_id 回写前，对话接口会返回"Agent 未同步到 AI 引擎"。
- capabilities/knowledge_base_ids/tool_ids 用 JSON 列，MyBatis-Plus 通过 JacksonTypeHandler 映射为 List<String>。
```

- [ ] **Step 3：运行 `/code-review`，通过后 git add**

```bash
git add acg-user/src/main/resources/db/schema.sql docs/changelogs/2026-06-14-agent-python-sync.md
```

---

## Task 2：AgentDO 扩展字段 + JSON TypeHandler

**Files:**
- Modify: `acg-common/src/main/java/com/darkness/common/entity/AgentDO.java`

> 数据类无行为单测；映射正确性由 Task 3（AgentVO from/to 测试）与 Task 7（AgentServiceImpl 测试）间接覆盖。

- [ ] **Step 1：整体替换 AgentDO.java**

```java
package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.darkness.common.enums.AgentCategory;
import com.darkness.common.enums.CommonStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agent 实体，映射 agent 表。存储 AI 智能体的配置信息。
 * <p>
 * 既包含 Java 侧的展示/分类字段，也包含同步到 Python 引擎所需的丰富配置
 * （system_prompt、provider、temperature、capabilities、knowledge_base_ids、tool_ids 等），
 * 以及同步锚点 python_agent_id。
 * capabilities/knowledge_base_ids/tool_ids 为 JSON 列，通过 JacksonTypeHandler 映射为 List<String>。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent", autoResultMap = true)
public class AgentDO extends BaseEntity {

    /** 智能体名称，用于前端展示 */
    private String name;

    /** 智能体功能描述 */
    private String description;

    /** 头像图片 URL */
    private String avatar;

    /** 外部 LLM API 的完整地址，如 https://api.openai.com/v1/chat/completions */
    private String apiUrl;

    /** 调用外部 API 的密钥，明文存储，生产环境需加密 */
    private String apiKey;

    /** 模型标识，如 gpt-4、doubao-pro 等 */
    private String model;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;

    /** 额外配置参数（已废弃，改用下方独立列），保留以向前兼容 */
    private String configJson;

    /** Agent 分类：CHAT-对话，VIDEO-视频，IMAGE-图像 */
    private AgentCategory category;

    /** 系统提示词，同步到 Python 作为 Agent 的 system_prompt */
    private String systemPrompt;

    /** LLM 厂商标识：doubao/qwen/deepseek，同步到 Python llm_config.provider */
    private String provider;

    /** 生成温度，同步到 Python llm_config.temperature */
    private BigDecimal temperature;

    /** 最大输出 token 数，同步到 Python llm_config.max_tokens */
    private Integer maxTokens;

    /** Top-P 采样，同步到 Python llm_config.top_p */
    private BigDecimal topP;

    /** 记忆类型：conversation_window/summary/none，同步到 Python memory_config.type */
    private String memoryType;

    /** 上下文窗口 token 数，同步到 Python memory_config.max_tokens */
    private Integer memoryMaxTokens;

    /** 能力标签数组，如 ["chat","rag","tool_use"]，同步到 Python capabilities */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> capabilities;

    /** 关联知识库 id 列表（Python 侧 string id），同步到 Python knowledge_base_ids */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgeBaseIds;

    /** 关联工具 id 列表（Python 侧 string id），同步到 Python tool_ids */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> toolIds;

    /** 同步锚点：Python 侧 agent 的 string id；未同步时为 null，对话接口会拒绝 */
    private String pythonAgentId;
}
```

- [ ] **Step 2：编译验证 acg-common**

```bash
mvn clean compile -pl acg-common
```
Expected: BUILD SUCCESS。

- [ ] **Step 3：运行 `/code-review`，通过后 git add**

```bash
git add acg-common/src/main/java/com/darkness/common/entity/AgentDO.java
```

---

## Task 3：AgentVO 扩展字段 + from/to 映射（TDD）

**Files:**
- Modify: `acg-common/src/main/java/com/darkness/common/model/AgentVO.java`
- Test: `acg-common/src/test/java/com/darkness/common/model/AgentVOTest.java`（新建）

- [ ] **Step 1：写失败测试** `acg-common/src/test/java/com/darkness/common/model/AgentVOTest.java`

```java
package com.darkness.common.model;

import com.darkness.common.entity.AgentDO;
import com.darkness.common.enums.AgentCategory;
import com.darkness.common.enums.CommonStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentVO from/to 转换测试：验证新增丰富配置字段在 VO↔DO 间正确传递，
 * 且 apiKey 在 from() 时被脱敏、toEntity() 时不做逆脱敏。
 */
class AgentVOTest {

    @Test
    void from_carriesRichFields_andMasksApiKey() {
        AgentDO entity = new AgentDO();
        entity.setId(7L);
        entity.setApiKey("real-secret");
        entity.setSystemPrompt("你是一个助手");
        entity.setProvider("doubao");
        entity.setTemperature(new BigDecimal("0.5"));
        entity.setMaxTokens(2048);
        entity.setTopP(new BigDecimal("0.8"));
        entity.setMemoryType("conversation_window");
        entity.setMemoryMaxTokens(8000);
        entity.setCapabilities(List.of("chat", "rag"));
        entity.setKnowledgeBaseIds(List.of("kb1"));
        entity.setToolIds(List.of("calculator"));
        entity.setPythonAgentId("py-abc");
        entity.setCategory(AgentCategory.CHAT);
        entity.setStatus(CommonStatus.ENABLED);

        AgentVO vo = AgentVO.from(entity);

        assertThat(vo.getSystemPrompt()).isEqualTo("你是一个助手");
        assertThat(vo.getProvider()).isEqualTo("doubao");
        assertThat(vo.getTemperature()).isEqualByComparingTo("0.5");
        assertThat(vo.getCapabilities()).containsExactly("chat", "rag");
        assertThat(vo.getKnowledgeBaseIds()).containsExactly("kb1");
        assertThat(vo.getToolIds()).containsExactly("calculator");
        assertThat(vo.getPythonAgentId()).isEqualTo("py-abc");
        // apiKey 必须脱敏
        assertThat(vo.getApiKey()).isEqualTo("******");
    }

    @Test
    void toEntity_carriesRichFields_withoutUnmasking() {
        AgentVO vo = new AgentVO();
        vo.setName("A");
        vo.setApiUrl("http://x");
        vo.setApiKey("******");
        vo.setSystemPrompt("prompt");
        vo.setCapabilities(List.of("chat"));
        vo.setPythonAgentId("py-1");

        AgentDO entity = vo.toEntity();

        assertThat(entity.getSystemPrompt()).isEqualTo("prompt");
        assertThat(entity.getCapabilities()).containsExactly("chat");
        assertThat(entity.getPythonAgentId()).isEqualTo("py-1");
        // toEntity 不逆脱敏，apiKey 原样传入（由 Service 层判断掩码）
        assertThat(entity.getApiKey()).isEqualTo("******");
    }
}
```

- [ ] **Step 2：运行测试，确认失败**

```bash
mvn test -pl acg-common -Dtest=AgentVOTest
```
Expected: 编译失败（AgentVO 无 getSystemPrompt 等方法）。

- [ ] **Step 3：整体替换 AgentVO.java**

```java
package com.darkness.common.model;

import com.darkness.common.constant.AgentConstants;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.enums.AgentCategory;
import com.darkness.common.enums.CommonStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 视图对象，用于 Controller 层与前端之间的数据传输，对敏感字段做脱敏处理。
 * <p>
 * 既承载 Java 侧展示字段，也承载同步到 Python 引擎所需的丰富配置。apiKey 返回前端时固定脱敏为 "******"。
 */
@Data
public class AgentVO {

    /** Agent 主键 */
    private Long id;

    /** 智能体名称，用于前端展示 */
    @NotBlank(message = "智能体名称不能为空")
    @Size(max = 128, message = "智能体名称长度不能超过128个字符")
    private String name;

    /** 智能体功能描述 */
    @Size(max = 512, message = "描述长度不能超过512个字符")
    private String description;

    /** 头像图片 URL */
    @Size(max = 512, message = "头像URL长度不能超过512个字符")
    private String avatar;

    /** 外部 LLM API 的完整地址 */
    @NotBlank(message = "API地址不能为空")
    @Size(max = 512, message = "API地址长度不能超过512个字符")
    private String apiUrl;

    /** API 密钥，前端展示时已脱敏为 "******"，更新时若仍为 "******" 则保留原值不变 */
    @NotBlank(message = "API密钥不能为空")
    @Size(max = 512, message = "API密钥长度不能超过512个字符")
    private String apiKey;

    /** 模型标识 */
    @Size(max = 128, message = "模型标识长度不能超过128个字符")
    private String model;

    /** 状态：ENABLED-启用，DISABLED-禁用 */
    private CommonStatus status;

    /** 额外配置参数（已废弃，保留向前兼容） */
    private String configJson;

    /** Agent 分类：CHAT-对话，VIDEO-视频，IMAGE-图像 */
    private AgentCategory category;

    /** 系统提示词，同步到 Python system_prompt */
    private String systemPrompt;

    /** LLM 厂商：doubao/qwen/deepseek */
    private String provider;

    /** 生成温度 */
    private BigDecimal temperature;

    /** 最大输出 token 数 */
    private Integer maxTokens;

    /** Top-P 采样 */
    private BigDecimal topP;

    /** 记忆类型：conversation_window/summary/none */
    private String memoryType;

    /** 上下文窗口 token 数 */
    private Integer memoryMaxTokens;

    /** 能力标签数组 */
    private List<String> capabilities;

    /** 关联知识库 id 列表（Python 侧 string id） */
    private List<String> knowledgeBaseIds;

    /** 关联工具 id 列表（Python 侧 string id） */
    private List<String> toolIds;

    /** 同步锚点：Python 侧 agent 的 string id，未同步时为 null */
    private String pythonAgentId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 将实体转换为 VO，同时对 apiKey 进行脱敏处理。
     *
     * @param entity Agent 实体
     * @return 脱敏后的 AgentVO，若 entity 为 null 则返回 null
     */
    public static AgentVO from(AgentDO entity) {
        if (entity == null) return null;
        AgentVO vo = new AgentVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setAvatar(entity.getAvatar());
        vo.setApiUrl(entity.getApiUrl());
        vo.setApiKey(entity.getApiKey() != null ? AgentConstants.API_KEY_MASK : null); // 脱敏：不将真实 API Key 返回前端
        vo.setModel(entity.getModel());
        vo.setStatus(entity.getStatus());
        vo.setConfigJson(entity.getConfigJson());
        vo.setCategory(entity.getCategory());
        vo.setSystemPrompt(entity.getSystemPrompt());
        vo.setProvider(entity.getProvider());
        vo.setTemperature(entity.getTemperature());
        vo.setMaxTokens(entity.getMaxTokens());
        vo.setTopP(entity.getTopP());
        vo.setMemoryType(entity.getMemoryType());
        vo.setMemoryMaxTokens(entity.getMemoryMaxTokens());
        vo.setCapabilities(entity.getCapabilities());
        vo.setKnowledgeBaseIds(entity.getKnowledgeBaseIds());
        vo.setToolIds(entity.getToolIds());
        vo.setPythonAgentId(entity.getPythonAgentId());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 将 VO 转换为实体，供新增或更新操作使用。
     * 注意：此方法不做脱敏逆处理，apiKey 字段直接传入（更新时由 Service 层判断是否为掩码值）。
     *
     * @return AgentDO 实体
     */
    public AgentDO toEntity() {
        AgentDO entity = new AgentDO();
        entity.setId(this.id);
        entity.setName(this.name);
        entity.setDescription(this.description);
        entity.setAvatar(this.avatar);
        entity.setApiUrl(this.apiUrl);
        entity.setApiKey(this.apiKey);
        entity.setModel(this.model);
        entity.setStatus(this.status);
        entity.setConfigJson(this.configJson);
        entity.setCategory(this.category);
        entity.setSystemPrompt(this.systemPrompt);
        entity.setProvider(this.provider);
        entity.setTemperature(this.temperature);
        entity.setMaxTokens(this.maxTokens);
        entity.setTopP(this.topP);
        entity.setMemoryType(this.memoryType);
        entity.setMemoryMaxTokens(this.memoryMaxTokens);
        entity.setCapabilities(this.capabilities);
        entity.setKnowledgeBaseIds(this.knowledgeBaseIds);
        entity.setToolIds(this.toolIds);
        entity.setPythonAgentId(this.pythonAgentId);
        return entity;
    }
}
```

- [ ] **Step 4：运行测试，确认通过**

```bash
mvn test -pl acg-common -Dtest=AgentVOTest
```
Expected: BUILD SUCCESS，2 个测试通过。

- [ ] **Step 5：运行 `/code-review`，通过后 git add**

```bash
git add acg-common/src/main/java/com/darkness/common/model/AgentVO.java acg-common/src/test/java/com/darkness/common/model/AgentVOTest.java
```

---

## Task 4：ChatEvent DTO（前端 SSE 契约，TDD）

**Files:**
- Create: `acg-common/src/main/java/com/darkness/common/model/ChatEvent.java`
- Test: `acg-common/src/test/java/com/darkness/common/model/ChatEventTest.java`

- [ ] **Step 1：写失败测试** `acg-common/src/test/java/com/darkness/common/model/ChatEventTest.java`

```java
package com.darkness.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatEvent 序列化测试：镜像 Python 的 SSE 事件 schema（snake_case）。
 * 防止字段命名漂移导致前端解析失败。
 */
class ChatEventTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_pythonContentEvent() throws Exception {
        // Python 实际下发的 snake_case 报文
        String json = "{\"type\":\"content\",\"content\":\"你好\"}";
        ChatEvent event = mapper.readValue(json, ChatEvent.class);
        assertThat(event.getType()).isEqualTo("content");
        assertThat(event.getContent()).isEqualTo("你好");
    }

    @Test
    void deserialize_pythonToolCallEvent_snakeCase() throws Exception {
        String json = "{\"type\":\"tool_call\",\"tool_name\":\"knowledge_search\",\"tool_input\":{\"q\":\"x\"}}";
        ChatEvent event = mapper.readValue(json, ChatEvent.class);
        assertThat(event.getType()).isEqualTo("tool_call");
        assertThat(event.getToolName()).isEqualTo("knowledge_search");
        assertThat(event.getToolInput()).containsEntry("q", "x");
    }

    @Test
    void deserialize_pythonDoneEvent() throws Exception {
        String json = "{\"type\":\"done\",\"usage\":{\"total_tokens\":12}}";
        ChatEvent event = mapper.readValue(json, ChatEvent.class);
        assertThat(event.getType()).isEqualTo("done");
        assertThat(event.getUsage()).containsEntry("total_tokens", 12);
    }
}
```

- [ ] **Step 2：运行测试，确认失败**

```bash
mvn test -pl acg-common -Dtest=ChatEventTest
```
Expected: 编译失败（ChatEvent 不存在）。

- [ ] **Step 3：创建 ChatEvent.java**

```java
package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.Map;

/**
 * 对话 SSE 事件 DTO，镜像 Python acgagent-ai 的 ChatEvent schema。
 * <p>
 * 线上报文为 snake_case（Python pydantic 默认输出），故使用 SnakeCaseStrategy 命名。
 * type 取值：content / tool_call / tool_result / thinking / error / done。
 * 该 schema 同时是前端 SSE 解析契约，变更需走版本化。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatEvent {

    /** 事件类型：content | tool_call | tool_result | thinking | error | done */
    private String type;

    /** 文本片段（content/thinking 事件使用） */
    private String content;

    /** 工具名（tool_call/tool_result 事件使用） */
    private String toolName;

    /** 工具输入参数（tool_call 事件使用） */
    private Map<String, Object> toolInput;

    /** 工具输出（tool_result 事件使用） */
    private String toolOutput;

    /** 错误码（error 事件使用） */
    private Integer code;

    /** 错误/状态信息 */
    private String message;

    /** token 用量（done 事件使用） */
    private Map<String, Object> usage;
}
```

- [ ] **Step 4：运行测试，确认通过**

```bash
mvn test -pl acg-common -Dtest=ChatEventTest
```
Expected: BUILD SUCCESS，3 个测试通过。

- [ ] **Step 5：运行 `/code-review`，通过后 git add**

```bash
git add acg-common/src/main/java/com/darkness/common/model/ChatEvent.java acg-common/src/test/java/com/darkness/common/model/ChatEventTest.java
```

---

## Task 5：PythonAgentProperties 配置 + pythonWebClient Bean

**Files:**
- Create: `acg-chat/src/main/java/com/darkness/config/PythonAgentProperties.java`
- Modify: `acg-chat/src/main/java/com/darkness/config/WebClientConfig.java`
- Test: `acg-chat/src/test/java/com/darkness/config/PythonAgentPropertiesTest.java`

- [ ] **Step 1：写失败测试** `acg-chat/src/test/java/com/darkness/config/PythonAgentPropertiesTest.java`

```java
package com.darkness.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PythonAgentProperties 绑定测试：验证 python-agent.* 前缀的配置正确注入并带有默认值。
 */
@SpringBootTest(classes = PythonAgentProperties.class)
@TestPropertySource(properties = {
        "python-agent.base-url=http://127.0.0.1:8100",
        "python-agent.api-key=test-key",
        "python-agent.connect-timeout=3000"
})
class PythonAgentPropertiesTest {

    @org.springframework.beans.factory.annotation.Autowired
    private PythonAgentProperties props;

    @Test
    void bindsProperties_andAppliesDefaults() {
        assertThat(props.getBaseUrl()).isEqualTo("http://127.0.0.1:8100");
        assertThat(props.getApiKey()).isEqualTo("test-key");
        assertThat(props.getConnectTimeout()).isEqualTo(3000);
        // 未显式设置时使用默认值
        assertThat(props.getReadTimeout()).isEqualTo(10000);
        assertThat(props.getStreamReadTimeout()).isEqualTo(300000);
    }
}
```

- [ ] **Step 2：运行测试，确认失败**

```bash
mvn test -pl acg-chat -am -Dtest=PythonAgentPropertiesTest
```
Expected: 编译失败（PythonAgentProperties 不存在）。

- [ ] **Step 3：创建 PythonAgentProperties.java**

```java
package com.darkness.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python AI 引擎连接配置，绑定 Nacos acg-chat.yaml 中 python-agent.* 前缀的配置。
 * <p>
 * api-key 仅在 Java 服务端使用（调用 Python 的 X-API-Key），绝不返回前端。
 */
@Data
@Component
@ConfigurationProperties(prefix = "python-agent")
public class PythonAgentProperties {

    /** Python 服务基础地址，如 http://127.0.0.1:8100 */
    private String baseUrl;

    /** 调用 Python 的 X-API-Key（与 Python acgagent-ai/.env 的 ACG_AI_API_KEY 一致） */
    private String apiKey;

    /** 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** 普通请求读超时（毫秒），通过 reactor .timeout() 在调用处生效 */
    private int readTimeout = 10000;

    /** SSE 流式读超时（毫秒），对齐 chat.sse-timeout（默认 5 分钟） */
    private int streamReadTimeout = 300000;
}
```

- [ ] **Step 4：改 WebClientConfig.java，提供带连接超时的 pythonWebClient**

整体替换 `acg-chat/src/main/java/com/darkness/config/WebClientConfig.java`：

```java
package com.darkness.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient 配置。
 * <p>
 * pythonWebClient：调用 Python AI 引擎的专用客户端，预置 baseUrl 与连接超时；
 * 普通请求与 SSE 流式的读超时在 PythonAiClient 调用处通过 reactor .timeout() 区分应用。
 */
@Configuration
public class WebClientConfig {

    /**
     * Python 引擎专用 WebClient。
     * 连接超时由 python-agent.connect-timeout 控制（默认 5s）。
     *
     * @param props Python 连接配置
     * @return 预置 baseUrl 与连接超时的 WebClient
     */
    @Bean
    public WebClient pythonWebClient(PythonAgentProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeout());
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
```

> 说明：原 `webClient()` Bean 删除。下一步验证无其他引用。

- [ ] **Step 5：确认旧 webClient Bean 无引用**

```bash
mvn clean compile -pl acg-chat -am
```
Expected: BUILD SUCCESS（说明无代码依赖被删的 webClient Bean）。若失败，按编译错误把对 `webClient` Bean 的注入改为 `pythonWebClient`（本计划范围内仅 AgentClient 用 WebClient，且 AgentClient 会在 Task 9 删除）。

- [ ] **Step 6：运行测试，确认通过**

```bash
mvn test -pl acg-chat -am -Dtest=PythonAgentPropertiesTest
```
Expected: BUILD SUCCESS，1 个测试通过。

- [ ] **Step 7：运行 `/code-review`，通过后 git add**

```bash
git add acg-chat/src/main/java/com/darkness/config/PythonAgentProperties.java acg-chat/src/main/java/com/darkness/config/WebClientConfig.java acg-chat/src/test/java/com/darkness/config/PythonAgentPropertiesTest.java
```

---

## Task 6：PythonAiClient（Python HTTP 出口）

**Files:**
- Create: `acg-chat/src/main/java/com/darkness/agent/client/PythonAiClient.java`
- Test: `acg-chat/src/test/java/com/darkness/agent/client/PythonAiClientTest.java`
- Modify: `acg-common/src/main/java/com/darkness/common/result/ResultCode.java`

> 覆盖范围：纯解析方法（`parseChatEvent` / `extractPythonAgentId` / `verifySuccess`）走单测；WebClient 实际 HTTP/SSE 流按项目惯例归集成测试（参考既有 ChatServiceImplTest 注释）。

- [ ] **Step 1：ResultCode 加 SERVICE_UNAVAILABLE**

修改 `acg-common/src/main/java/com/darkness/common/result/ResultCode.java`，在 `INTERNAL_ERROR` 后加：

```java
    INTERNAL_ERROR(500, "Internal server error"),
    SERVICE_UNAVAILABLE(503, "Service unavailable");
```

> 即把 `INTERNAL_ERROR(500, "...");` 的分号改为逗号，新增 `SERVICE_UNAVAILABLE(503, "Service unavailable");`。

- [ ] **Step 2：写失败测试** `acg-chat/src/test/java/com/darkness/agent/client/PythonAiClientTest.java`

```java
package com.darkness.agent.client;

import com.darkness.common.exception.BizException;
import com.darkness.common.model.ChatEvent;
import com.darkness.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PythonAiClient 纯解析逻辑测试（WebClient HTTP/SSE 流按项目惯例归集成测试）。
 * 构造时传真实 ObjectMapper（纯解析方法依赖它），WebClient/Properties 传 null（不被调用）。
 */
class PythonAiClientTest {

    private final PythonAiClient client = new PythonAiClient(null, null, new ObjectMapper());

    @Test
    void parseChatEvent_contentEvent() {
        ChatEvent event = client.parseChatEvent("{\"type\":\"content\",\"content\":\"hi\"}");
        assertThat(event.getType()).isEqualTo("content");
        assertThat(event.getContent()).isEqualTo("hi");
    }

    @Test
    void parseChatEvent_doneEvent() {
        ChatEvent event = client.parseChatEvent("{\"type\":\"done\",\"usage\":{\"total_tokens\":5}}");
        assertThat(event.getType()).isEqualTo("done");
        assertThat(event.getUsage()).containsEntry("total_tokens", 5);
    }

    @Test
    void extractPythonAgentId_success_returnsDataId() {
        String json = "{\"code\":200,\"message\":\"success\",\"data\":{\"id\":\"py-123\",\"name\":\"A\"}}";
        assertThat(client.extractPythonAgentId(json)).isEqualTo("py-123");
    }

    @Test
    void extractPythonAgentId_pythonError_throwsBizExceptionWithCode() {
        String json = "{\"code\":400,\"message\":\"Agent is disabled\"}";
        assertThatThrownBy(() -> client.extractPythonAgentId(json))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(400);
    }

    @Test
    void verifySuccess_pythonError_throws() {
        assertThatThrownBy(() -> client.verifySuccess("{\"code\":500,\"message\":\"boom\"}"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(500);
    }

    @Test
    void verifySuccess_ok_doesNotThrow() {
        client.verifySuccess("{\"code\":200,\"message\":\"success\"}");
    }

    @Test
    void buildCreateBody_mapsAllFields_snakeCase() {
        com.darkness.common.entity.AgentDO agent = new com.darkness.common.entity.AgentDO();
        agent.setName("A");
        agent.setDescription("desc");
        agent.setSystemPrompt("sys");
        agent.setApiUrl("http://llm");
        agent.setApiKey("secret");
        agent.setModel("m");
        agent.setProvider("doubao");
        agent.setTemperature(new java.math.BigDecimal("0.7"));
        agent.setMaxTokens(4096);
        agent.setTopP(new java.math.BigDecimal("0.9"));
        agent.setMemoryType("conversation_window");
        agent.setMemoryMaxTokens(8000);
        agent.setCapabilities(java.util.List.of("chat", "rag"));
        agent.setKnowledgeBaseIds(java.util.List.of("kb1"));
        agent.setToolIds(java.util.List.of("calc"));

        Map<String, Object> body = client.buildCreateBody(agent);

        assertThat(body).containsEntry("name", "A");
        assertThat(body).containsEntry("system_prompt", "sys");
        @SuppressWarnings("unchecked")
        Map<String, Object> llm = (Map<String, Object>) body.get("llm_config");
        assertThat(llm).containsEntry("base_url", "http://llm");
        assertThat(llm).containsEntry("api_key", "secret");
        assertThat(llm).containsEntry("provider", "doubao");
        @SuppressWarnings("unchecked")
        Map<String, Object> mem = (Map<String, Object>) body.get("memory_config");
        assertThat(mem).containsEntry("type", "conversation_window");
        assertThat(body).containsEntry("capabilities", java.util.List.of("chat", "rag"));
        assertThat(body).containsEntry("knowledge_base_ids", java.util.List.of("kb1"));
        assertThat(body).containsEntry("tool_ids", java.util.List.of("calc"));
    }
}
```

- [ ] **Step 3：运行测试，确认失败**

```bash
mvn test -pl acg-chat -am -Dtest=PythonAiClientTest
```
Expected: 编译失败（PythonAiClient 不存在）。

- [ ] **Step 4：创建 PythonAiClient.java**

```java
package com.darkness.agent.client;

import com.darkness.common.entity.AgentDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.model.ChatEvent;
import com.darkness.common.result.ResultCode;
import com.darkness.config.PythonAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python AI 引擎（acgagent-ai）远程调用客户端，是 acg-chat 调用 Python 的唯一出口。
 * <p>
 * 职责：
 * <ul>
 *   <li>Agent 配置同步：createAgent / updateAgent / deleteAgent（同步、非流式）</li>
 *   <li>对话流式：streamChat 返回结构化 ChatEvent 的 Flux</li>
 * </ul>
 * 所有请求带 X-API-Key；对话请求额外带 X-User-Id 透传当前用户。
 * 普通/流式请求分别应用 readTimeout / streamReadTimeout（reactor .timeout()）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonAiClient {

    private final WebClient pythonWebClient;
    private final PythonAgentProperties props;
    private final ObjectMapper objectMapper;

    /**
     * 流式调用 Python 对话接口，返回结构化 ChatEvent 流。
     * <p>
     * Python SSE 每行为 {@code data: {json}}；本方法按既有 AgentClient 的解析方式逐行处理，
     * 反序列化为 ChatEvent。流式读超时由 streamReadTimeout 控制。
     *
     * @param pythonAgentId Python 侧 agent id（同步锚点）
     * @param conversationId Java 会话 id，转字符串作为 Python conversation_id（Python 据此自管 memory）
     * @param message       用户输入
     * @param userId        当前用户 id，透传 X-User-Id
     * @return ChatEvent 流
     */
    public Flux<ChatEvent> streamChat(String pythonAgentId, Long conversationId, String message, Long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversation_id", String.valueOf(conversationId));
        body.put("message", message);
        body.put("stream", true);

        return pythonWebClient.post()
                .uri("/api/v1/chat/{agentId}/completions", pythonAgentId)
                .header("X-API-Key", props.getApiKey())
                .header("X-User-Id", String.valueOf(userId))
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line != null && line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .filter(data -> !data.isEmpty())
                .map(this::parseChatEvent)
                .timeout(Duration.ofMillis(props.getStreamReadTimeout()));
    }

    /**
     * 在 Python 创建 Agent，返回 Python 分配的 string id（用于回写 python_agent_id）。
     * Python 返回非 200 时抛 BizException（携带 Python 的 code）。
     */
    public String createAgent(AgentDO agent) {
        String json = pythonWebClient.post()
                .uri("/api/v1/agents")
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(buildCreateBody(agent))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout()))
                .block();
        return extractPythonAgentId(json);
    }

    /**
     * 同步更新 Python 侧 Agent。Python 返回非 200 时抛 BizException。
     */
    public void updateAgent(String pythonAgentId, AgentDO agent) {
        String json = pythonWebClient.put()
                .uri("/api/v1/agents/{agentId}", pythonAgentId)
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(buildCreateBody(agent))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout()))
                .block();
        verifySuccess(json);
    }

    /**
     * 删除 Python 侧 Agent。Python 返回非 200 时抛 BizException。
     */
    public void deleteAgent(String pythonAgentId) {
        String json = pythonWebClient.delete()
                .uri("/api/v1/agents/{agentId}", pythonAgentId)
                .header("X-API-Key", props.getApiKey())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout()))
                .block();
        verifySuccess(json);
    }

    /**
     * 把 SSE data 行的 JSON 反序列化为 ChatEvent。
     * 非法 JSON 记日志并抛 BizException（中断流，触发 doOnError）。
     */
    public ChatEvent parseChatEvent(String data) {
        try {
            return objectMapper.readValue(data, ChatEvent.class);
        } catch (Exception e) {
            log.warn("Failed to parse Python SSE chunk: {}", data, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "AI 流式响应解析失败");
        }
    }

    /**
     * 从 Python Agent 创建响应中提取 data.id。
     * Python 返回 code != 200 时抛携带其 code 的 BizException。
     */
    public String extractPythonAgentId(String json) {
        JsonNode root = readTree(json);
        int code = root.path("code").asInt(200);
        if (code != 200) {
            throw new BizException(code, root.path("message").asText("Python create agent failed"));
        }
        String id = root.path("data").path("id").asText(null);
        if (id == null || id.isEmpty()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "Python 未返回 agent id");
        }
        return id;
    }

    /**
     * 校验 Python 通用 Result 响应是否成功（code == 200），否则抛携带其 code 的 BizException。
     */
    public void verifySuccess(String json) {
        JsonNode root = readTree(json);
        int code = root.path("code").asInt(200);
        if (code != 200) {
            throw new BizException(code, root.path("message").asText("Python request failed"));
        }
    }

    /**
     * 构造 Python AgentCreateRequest 请求体（snake_case 键，匹配 Python pydantic 模型）。
     */
    public Map<String, Object> buildCreateBody(AgentDO agent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", agent.getName());
        body.put("description", agent.getDescription());
        body.put("system_prompt", agent.getSystemPrompt());

        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("provider", agent.getProvider());
        llm.put("model", agent.getModel());
        llm.put("base_url", agent.getApiUrl());
        llm.put("api_key", agent.getApiKey());
        llm.put("temperature", agent.getTemperature());
        llm.put("max_tokens", agent.getMaxTokens());
        llm.put("top_p", agent.getTopP());
        body.put("llm_config", llm);

        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("type", agent.getMemoryType());
        mem.put("max_tokens", agent.getMemoryMaxTokens());
        body.put("memory_config", mem);

        body.put("capabilities", agent.getCapabilities());
        body.put("knowledge_base_ids", agent.getKnowledgeBaseIds());
        body.put("tool_ids", agent.getToolIds());
        return body;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "AI 引擎响应解析失败");
        }
    }
}
```

> 注意：`bodyToMono` 需 import `org.springframework.web.reactive.function.client.bodyToMono`（WebClient 链式，编译器自动解析）。`block()` 用于同步 CUD（低频管理操作）。

- [ ] **Step 5：运行测试，确认通过**

```bash
mvn test -pl acg-chat -am -Dtest=PythonAiClientTest
```
Expected: BUILD SUCCESS，7 个测试通过。

- [ ] **Step 6：运行 `/code-review`，通过后 git add**

```bash
git add acg-chat/src/main/java/com/darkness/agent/client/PythonAiClient.java acg-chat/src/test/java/com/darkness/agent/client/PythonAiClientTest.java acg-common/src/main/java/com/darkness/common/result/ResultCode.java
```

---

## Task 7：AgentServiceImpl 同步逻辑（@Transactional，失败回滚）

**Files:**
- Modify: `acg-chat/src/main/java/com/darkness/agent/service/impl/AgentServiceImpl.java`
- Modify: `acg-chat/src/test/java/com/darkness/agent/service/impl/AgentServiceImplTest.java`

> 单测验证控制流（Python 失败时不进入"回写 pythonId"步骤并抛 BizException）。真正的 DB 回滚由 Spring `@Transactional` 保证——框架行为，不在 Mockito 单测覆盖范围内；上线前需用真实 MySQL + 失败的 Python 手动验证回滚（Task 11）。

- [ ] **Step 1：改测试** `AgentServiceImplTest.java`

在类顶部 `@Mock private AgentMapper agentMapper;` 之后加：

```java
    @Mock
    private com.darkness.agent.client.PythonAiClient pythonAiClient;
```

把 `createAgent_success` 改为（同步成功）：

```java
    @Test
    void createAgent_success_pythonSynced() {
        AgentVO vo = new AgentVO();
        vo.setName("NewAgent");
        vo.setApiKey("secret-key");
        when(agentMapper.insert(any(AgentDO.class))).thenAnswer(invocation -> {
            AgentDO entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
        when(pythonAiClient.createAgent(any(AgentDO.class))).thenReturn("py-xyz");
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);

        AgentVO result = agentService.createAgent(vo);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getPythonAgentId()).isEqualTo("py-xyz");
    }
```

把 `updateAgent_success` 改为（同步成功）：

```java
    @Test
    void updateAgent_success_pythonSynced() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real-secret-key");
        existing.setPythonAgentId("py-1");
        // updateAgent 内部会两次 selectById：第一次取 existing，第二次取回写后的对象
        when(agentMapper.selectById(1L)).thenReturn(existing, existing);
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);

        AgentVO vo = new AgentVO();
        vo.setName("Updated");
        vo.setApiKey("new-real-key");

        agentService.updateAgent(1L, vo);

        verify(pythonAiClient).updateAgent(eq("py-1"), any(AgentDO.class));
    }
```

> `updateAgent_preserveApiKey_whenMaskSent` / `updateAgent_preserveApiKey_whenNullSent` 需给 existing 设置 `pythonAgentId="py-1"` 并 `verify(pythonAiClient).updateAgent(...)` 不抛即可；保留对 apiKey 的断言不变。

新增 rollback 测试：

```java
    @Test
    void createAgent_pythonFails_throwsAndDoesNotWritePythonId() {
        AgentVO vo = new AgentVO();
        vo.setName("X");
        vo.setApiKey("k");
        when(agentMapper.insert(any(AgentDO.class))).thenAnswer(i -> {
            i.getArgument(0, AgentDO.class).setId(2L);
            return 1;
        });
        when(pythonAiClient.createAgent(any(AgentDO.class)))
                .thenThrow(new BizException(503, "down"));

        assertThatThrownBy(() -> agentService.createAgent(vo))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(503);
        // 控制流证明：失败后不应进入"回写 pythonId"步骤
        verify(agentMapper, never()).updateById(any());
    }

    @Test
    void updateAgent_notSynced_throws() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real");
        existing.setPythonAgentId(null); // 未同步
        when(agentMapper.selectById(1L)).thenReturn(existing);

        AgentVO vo = new AgentVO();
        vo.setName("U");
        assertThatThrownBy(() -> agentService.updateAgent(1L, vo))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.INTERNAL_ERROR.getCode());
        verify(agentMapper, never()).updateById(any());
        verifyNoInteractions(pythonAiClient);
    }

    @Test
    void updateAgent_pythonFails_throws() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setApiKey("real");
        existing.setPythonAgentId("py-1");
        when(agentMapper.selectById(1L)).thenReturn(existing);
        when(agentMapper.updateById(any(AgentDO.class))).thenReturn(1);
        doThrow(new BizException(503, "down"))
                .when(pythonAiClient).updateAgent(eq("py-1"), any(AgentDO.class));

        AgentVO vo = new AgentVO();
        vo.setName("U");
        vo.setApiKey("******");
        assertThatThrownBy(() -> agentService.updateAgent(1L, vo))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(503);
    }

    @Test
    void deleteAgent_pythonFails_throws() {
        AgentDO existing = new AgentDO();
        existing.setId(1L);
        existing.setPythonAgentId("py-1");
        when(agentMapper.selectById(1L)).thenReturn(existing);
        when(agentMapper.deleteById(1L)).thenReturn(1);
        doThrow(new BizException(503, "down"))
                .when(pythonAiClient).deleteAgent("py-1");

        assertThatThrownBy(() -> agentService.deleteAgent(1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(503);
    }
```

补 import：`import static org.mockito.ArgumentMatchers.eq;`、`import static org.mockito.Mockito.doThrow;`、`import static org.mockito.Mockito.never;`、`import static org.mockito.Mockito.verifyNoInteractions;`、`import com.darkness.agent.client.PythonAiClient;`（用全限定名也可）。

- [ ] **Step 2：运行测试，确认失败**

```bash
mvn test -pl acg-chat -am -Dtest=AgentServiceImplTest
```
Expected: 失败（AgentServiceImpl 尚未注入 PythonAiClient、未加同步逻辑）。

- [ ] **Step 3：改 AgentServiceImpl.java**

整体替换：

```java
package com.darkness.agent.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.constant.AgentConstants;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.mapper.AgentMapper;
import com.darkness.common.model.AgentVO;
import com.darkness.agent.service.AgentService;
import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
import com.darkness.common.util.ServiceHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent 业务服务实现类，处理 Agent 的增删改查、API Key 脱敏，以及与 Python AI 引擎的配置同步。
 * <p>
 * Java 是 Agent 配置的真相源：CUD 时在 @Transactional 内先落 MySQL，再同步推送 Python；
 * Python 同步失败则抛异常，由 @Transactional 回滚 MySQL，保证两边一致。
 * 更新时若前端脱敏回传掩码值或 apiKey 为 null，保留数据库原值，避免密钥被清空。
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final PythonAiClient pythonAiClient;

    /**
     * 根据 ID 查询 Agent 配置。不存在时抛出 BizException(404)，apiKey 脱敏。
     */
    @Override
    public AgentVO getAgentById(Long id) {
        return AgentVO.from(ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id));
    }

    /**
     * 查询所有 Agent 配置列表。
     */
    @Override
    public List<AgentVO> listAgents() {
        return agentMapper.selectList(null).stream().map(AgentVO::from).toList();
    }

    /**
     * 创建 Agent。@Transactional 内：插 MySQL（python_agent_id 暂空）→ 调 Python 创建 → 回写 python_agent_id。
     * Python 失败 → 抛异常 → 回滚（删除刚插入的行）。
     */
    @Override
    @Transactional
    public AgentVO createAgent(AgentVO vo) {
        AgentDO entity = vo.toEntity();
        entity.setPythonAgentId(null);
        agentMapper.insert(entity);
        String pythonId = pythonAiClient.createAgent(entity); // 失败抛异常 → 回滚
        entity.setPythonAgentId(pythonId);
        agentMapper.updateById(entity);
        return AgentVO.from(entity);
    }

    /**
     * 更新 Agent。@Transactional 内：校验存在 + 已同步 → 更新 MySQL（掩码/null 保留原 apiKey）→ 同步 Python。
     * Python 失败 → 抛异常 → 回滚到旧值。
     */
    @Override
    @Transactional
    public AgentVO updateAgent(Long id, AgentVO vo) {
        AgentDO existing = ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id);
        if (existing.getPythonAgentId() == null || existing.getPythonAgentId().isBlank()) {
            // 未同步到 Python，无法更新对端，提示重建
            throw new BizException(ResultCode.INTERNAL_ERROR, "Agent 未同步到 AI 引擎，请联系管理员重建");
        }
        AgentDO entity = vo.toEntity();
        entity.setId(id);
        entity.setPythonAgentId(existing.getPythonAgentId());
        if (entity.getApiKey() == null || AgentConstants.API_KEY_MASK.equals(entity.getApiKey())) {
            // 前端脱敏回传掩码值时保留原密钥不变
            entity.setApiKey(existing.getApiKey());
        }
        agentMapper.updateById(entity);
        pythonAiClient.updateAgent(existing.getPythonAgentId(), entity); // 失败抛异常 → 回滚
        return AgentVO.from(agentMapper.selectById(id));
    }

    /**
     * 删除 Agent。@Transactional 内：校验存在 → 删 MySQL → 删 Python（已同步时）。
     * Python 失败 → 抛异常 → 回滚（恢复行）。
     */
    @Override
    @Transactional
    public void deleteAgent(Long id) {
        AgentDO existing = ServiceHelper.findOrThrow(agentMapper.selectById(id), "Agent", id);
        agentMapper.deleteById(id);
        if (existing.getPythonAgentId() != null && !existing.getPythonAgentId().isBlank()) {
            pythonAiClient.deleteAgent(existing.getPythonAgentId()); // 失败抛异常 → 回滚
        }
    }
}
```

- [ ] **Step 4：运行测试，确认通过**

```bash
mvn test -pl acg-chat -am -Dtest=AgentServiceImplTest
```
Expected: BUILD SUCCESS，全部测试通过。

- [ ] **Step 5：运行 `/code-review`，通过后 git add**

```bash
git add acg-chat/src/main/java/com/darkness/agent/service/impl/AgentServiceImpl.java acg-chat/src/test/java/com/darkness/agent/service/impl/AgentServiceImplTest.java
```

---

## Task 8：ChatServiceImpl 对话流改造（改走 Python SSE）

**Files:**
- Modify: `acg-chat/src/main/java/com/darkness/agent/service/impl/ChatServiceImpl.java`
- Modify: `acg-chat/src/test/java/com/darkness/agent/service/impl/ChatServiceImplTest.java`

> sendMessage 的 SSE 异步流仍按项目惯例不做单测（参考既有注释）；新增"未同步"前置校验的单测。

- [ ] **Step 1：改测试** —— 把 `@Mock private com.darkness.agent.client.AgentClient agentClient;` 替换为：

```java
    @Mock
    private com.darkness.agent.client.PythonAiClient pythonAiClient;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
```

新增 sendMessage 前置校验测试：

```java
    // ==================== sendMessage ====================

    @Test
    void sendMessage_agentNotSynced_throws() {
        ConversationDO conv = new ConversationDO();
        conv.setId(1L);
        conv.setUserId(1L);
        conv.setAgentId(10L);
        when(conversationMapper.selectById(1L)).thenReturn(conv);

        AgentDO agent = new AgentDO();
        agent.setId(10L);
        agent.setPythonAgentId(null); // 未同步
        when(agentMapper.selectById(10L)).thenReturn(agent);

        assertThatThrownBy(() -> chatService.sendMessage(1L, 1L, "hi"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.INTERNAL_ERROR.getCode());
        // 未同步时不应持久化用户消息
        verify(messageMapper, never()).insert(any());
    }
```

补 import：`import static org.mockito.Mockito.never;`、`import com.darkness.common.entity.AgentDO;`（若未引入）。

- [ ] **Step 2：运行测试，确认失败**

```bash
mvn test -pl acg-chat -am -Dtest=ChatServiceImplTest
```
Expected: 失败（ChatServiceImpl 仍依赖 AgentClient，编译/注入错误）。

- [ ] **Step 3：改 ChatServiceImpl.java**

整体替换 `sendMessage` 方法，并调整依赖（`AgentClient` → `PythonAiClient` + `ObjectMapper`），删除"查询历史喂 LLM"逻辑。关键改动：

- 顶部依赖改为：
```java
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AgentMapper agentMapper;
    private final com.darkness.agent.client.PythonAiClient pythonAiClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
```
（删除 `import com.darkness.agent.client.AgentClient;` 与该字段）

- 新增 import：
```java
import com.darkness.common.model.ChatEvent;
import java.util.concurrent.atomic.AtomicBoolean;
```

- 整体替换 `sendMessage` 方法体为：

```java
    /**
     * 通过 SSE 流式发送用户消息给 AI Agent（经 Python 引擎）。
     * <p>
     * 流程：
     * 1. 校验会话归属（防越权）与 Agent 存在；
     * 2. 校验 Agent 已同步到 Python（python_agent_id 非空）；
     * 3. 持久化用户消息到 message 表（前端历史 UI 用）；
     * 4. 创建 SseEmitter，异步调用 PythonAiClient.streamChat 透传结构化 ChatEvent；
     * 5. 累计 content 事件，收到 done 事件时持久化助手回复；
     * 6. 收到 error 事件不持久化残缺回复，流结束后以 completeWithError 收尾。
     * <p>
     * 会话上下文由 Python 按 conversation_id 自管（M1 双写），Java 不再查历史喂 LLM。
     */
    @Override
    public SseEmitter sendMessage(Long userId, Long conversationId, String content) {
        // 校验会话归属
        ConversationDO conv = ServiceHelper.findOrThrow(conversationMapper.selectById(conversationId), "Conversation", conversationId);
        if (!conv.getUserId().equals(userId)) throw new BizException(ResultCode.FORBIDDEN, "Forbidden");

        AgentDO agent = ServiceHelper.findOrThrow(agentMapper.selectById(conv.getAgentId()), "Agent", conv.getAgentId());
        // 校验 Agent 已同步到 Python
        if (agent.getPythonAgentId() == null || agent.getPythonAgentId().isBlank()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "Agent 未同步到 AI 引擎，请联系管理员重建");
        }

        // 持久化用户消息
        MessageDO userMsg = new MessageDO();
        userMsg.setConversationId(conversationId);
        userMsg.setRole(MessageRole.USER);
        userMsg.setContent(content);
        messageMapper.insert(userMsg);

        SseEmitter emitter = new SseEmitter(sseTimeout);
        executor.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            AtomicBoolean hadError = new AtomicBoolean(false);
            pythonAiClient.streamChat(agent.getPythonAgentId(), conversationId, content, userId)
                    .doOnNext(event -> {
                        try {
                            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
                            switch (event.getType() == null ? "" : event.getType()) {
                                case "content" -> {
                                    if (event.getContent() != null) fullResponse.append(event.getContent());
                                }
                                case "done" -> persistAssistant(conversationId, fullResponse.toString());
                                case "error" -> hadError.set(true);
                                default -> { /* tool_call/tool_result/thinking 仅透传，不持久化 */ }
                            }
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(() -> {
                        if (hadError.get()) emitter.completeWithError(new RuntimeException("AI stream error"));
                        else emitter.complete();
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();
        });
        return emitter;
    }

    /** 持久化助手回复消息（仅在 done 事件时调用，避免持久化残缺回复）。 */
    private void persistAssistant(Long conversationId, String content) {
        MessageDO assistantMsg = new MessageDO();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole(MessageRole.ASSISTANT);
        assistantMsg.setContent(content);
        messageMapper.insert(assistantMsg);
    }
```

> 删除原 `sendMessage` 中"查询完整消息历史作为 LLM 上下文"的 `messageMapper.selectList(...)` 段（Python 自管 memory）。

- [ ] **Step 4：运行测试，确认通过**

```bash
mvn test -pl acg-chat -am -Dtest=ChatServiceImplTest
```
Expected: BUILD SUCCESS，全部测试通过（含新 sendMessage_agentNotSynced_throws）。

- [ ] **Step 5：运行 `/code-review`，通过后 git add**

```bash
git add acg-chat/src/main/java/com/darkness/agent/service/impl/ChatServiceImpl.java acg-chat/src/test/java/com/darkness/agent/service/impl/ChatServiceImplTest.java
```

---

## Task 9：删除 AgentClient（旧直连 LLM）

**Files:**
- Delete: `acg-chat/src/main/java/com/darkness/agent/client/AgentClient.java`

- [ ] **Step 1：确认无残留引用**

```bash
grep -rn "AgentClient" acg-chat/src acg-common/src acg-gateway/src acg-user/src || echo "NO REFERENCES"
```
Expected: 仅可能命中已删除/将删除点；正常应为 `NO REFERENCES`（ChatServiceImpl 已在 Task 8 改用 PythonAiClient）。若仍有命中，先修正引用再删除。

- [ ] **Step 2：删除文件**

```bash
rm acg-chat/src/main/java/com/darkness/agent/client/AgentClient.java
```

- [ ] **Step 3：编译验证**

```bash
mvn clean compile -pl acg-chat -am
```
Expected: BUILD SUCCESS。

- [ ] **Step 4：运行 `/code-review`，通过后 git add**

```bash
git add -A acg-chat/src/main/java/com/darkness/agent/client/
```

---

## Task 10：存量 Agent 迁移程序（gated CommandLineRunner）

**Files:**
- Create: `acg-chat/src/main/java/com/darkness/agent/migration/AgentSyncMigrationRunner.java`
- Test: `acg-chat/src/test/java/com/darkness/agent/migration/AgentSyncMigrationRunnerTest.java`

> 仅在 `python-agent.migrate-existing=true` 时执行；正常启动默认关闭，避免误跑。

- [ ] **Step 1：写失败测试** `acg-chat/src/test/java/com/darkness/agent/migration/AgentSyncMigrationRunnerTest.java`

```java
package com.darkness.agent.migration;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.mapper.AgentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 存量 Agent 迁移测试：验证对每条未同步 agent 调用 Python 创建并回写 python_agent_id；
 * 单条失败不影响其它（记录日志继续）。
 */
@ExtendWith(MockitoExtension.class)
class AgentSyncMigrationRunnerTest {

    @Mock private AgentMapper agentMapper;
    @Mock private PythonAiClient pythonAiClient;

    @InjectMocks
    private AgentSyncMigrationRunner runner;

    @Test
    void migrate_syncsUnsyncedAgents_andWritesPythonId() {
        AgentDO a1 = new AgentDO(); a1.setId(1L); a1.setName("A"); a1.setPythonAgentId(null);
        AgentDO a2 = new AgentDO(); a2.setId(2L); a2.setName("B"); a2.setPythonAgentId(null);
        AgentDO a3 = new AgentDO(); a3.setId(3L); a3.setName("C"); a3.setPythonAgentId("already"); // 已同步，跳过
        when(agentMapper.selectList(any())).thenReturn(List.of(a1, a2, a3));
        when(pythonAiClient.createAgent(any(AgentDO.class))).thenReturn("py-1").thenReturn("py-2");

        runner.run();

        verify(pythonAiClient, times(2)).createAgent(any(AgentDO.class)); // 只同步 a1/a2
        verify(agentMapper).updateById(argThat(a -> "py-1".equals(((AgentDO) a).getPythonAgentId())));
        verify(agentMapper).updateById(argThat(a -> "py-2".equals(((AgentDO) a).getPythonAgentId())));
    }

    @Test
    void migrate_oneFails_continuesOthers() {
        AgentDO a1 = new AgentDO(); a1.setId(1L); a1.setName("A"); a1.setPythonAgentId(null);
        AgentDO a2 = new AgentDO(); a2.setId(2L); a2.setName("B"); a2.setPythonAgentId(null);
        when(agentMapper.selectList(any())).thenReturn(List.of(a1, a2));
        when(pythonAiClient.createAgent(any(AgentDO.class)))
                .thenThrow(new com.darkness.common.exception.BizException(503, "down"))
                .thenReturn("py-2");

        runner.run();

        verify(pythonAiClient, times(2)).createAgent(any(AgentDO.class));
        verify(agentMapper).updateById(argThat(a -> "py-2".equals(((AgentDO) a).getPythonAgentId())));
    }
}
```

补 import：`import static org.mockito.ArgumentMatchers.argThat;`

- [ ] **Step 2：运行测试，确认失败**

```bash
mvn test -pl acg-chat -am -Dtest=AgentSyncMigrationRunnerTest
```
Expected: 编译失败（类不存在）。

- [ ] **Step 3：创建 AgentSyncMigrationRunner.java**

```java
package com.darkness.agent.migration;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.entity.AgentDO;
import com.darkness.common.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 存量 Agent 同步迁移程序。
 * <p>
 * 仅当 python-agent.migrate-existing=true 时启动执行（默认关闭）。
 * 对每条 python_agent_id 为空的 agent，调用 Python 创建并回写 python_agent_id；
 * 单条失败仅记日志，不中断其它（迁移尽力而为，可重跑）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSyncMigrationRunner implements CommandLineRunner {

    private final AgentMapper agentMapper;
    private final PythonAiClient pythonAiClient;

    /** 迁移开关，默认关闭。开启后下次启动执行一次。 */
    @Value("${python-agent.migrate-existing:false}")
    private boolean migrateExisting;

    @Override
    public void run(String... args) {
        if (!migrateExisting) {
            return;
        }
        log.info("Agent Python 同步迁移开始（python-agent.migrate-existing=true）");
        List<AgentDO> all = agentMapper.selectList(null);
        int ok = 0, fail = 0;
        for (AgentDO agent : all) {
            if (agent.getPythonAgentId() != null && !agent.getPythonAgentId().isBlank()) {
                continue; // 已同步，跳过
            }
            try {
                String pythonId = pythonAiClient.createAgent(agent);
                agent.setPythonAgentId(pythonId);
                agentMapper.updateById(agent);
                ok++;
            } catch (Exception e) {
                fail++;
                log.error("Agent 同步失败，id={}, name={}: {}", agent.getId(), agent.getName(), e.getMessage());
            }
        }
        log.info("Agent Python 同步迁移结束：成功 {}，失败 {}", ok, fail);
    }
}
```

- [ ] **Step 4：运行测试，确认通过**

```bash
mvn test -pl acg-chat -am -Dtest=AgentSyncMigrationRunnerTest
```
Expected: BUILD SUCCESS，2 个测试通过。

- [ ] **Step 5：运行 `/code-review`，通过后 git add**

```bash
git add acg-chat/src/main/java/com/darkness/agent/migration/AgentSyncMigrationRunner.java acg-chat/src/test/java/com/darkness/agent/migration/AgentSyncMigrationRunnerTest.java
```

---

## Task 11：Nacos 配置 + 全量构建/回归 + 手动验收

**Files:**
- 无代码改动；Nacos `acg-chat.yaml`（运维）+ 手动验证。

- [ ] **Step 1：在 Nacos `acg-agent` 命名空间、`DEFAULT_GROUP` 的 `acg-chat.yaml` 中确认/新增**

```yaml
python-agent:
  base-url: http://127.0.0.1:8100     # Python acgagent-ai 地址（按部署环境调整）
  api-key: 4l26****Waw                # 与 Python acgagent-ai/.env 的 ACG_AI_API_KEY 一致（真实值不提交）
  connect-timeout: 5000
  read-timeout: 10000
  stream-read-timeout: 300000
  migrate-existing: false             # 存量迁移时临时置 true，跑完改回 false
```

> api-key 真实值已在 Nacos 与 Python `.env` 中，不要写入代码仓库。

- [ ] **Step 2：全量编译 + 回归测试**

```bash
mvn clean test -pl acg-chat -am
```
Expected: BUILD SUCCESS，全部测试通过（含新增 5 个测试类）。

- [ ] **Step 3：启动 Python 引擎 + 存量迁移**

1. 启动 `acgagent-ai`（PyCharm 或 `python -m uvicorn app.main:app --port 8100`），确认 `GET http://127.0.0.1:8100/api/v1/health` 返回 healthy。
2. 临时把 Nacos `python-agent.migrate-existing` 置 `true`，启动 acg-chat 一次，查日志确认"成功 N，失败 0"。
3. 置回 `false`，重启 acg-chat。

- [ ] **Step 4：手动验收（验收标准 §10 Phase A）**

- [ ] `agent` 表所有记录 `python_agent_id` 非空（迁移成功）。
- [ ] 用 curl/前端发 `POST /api/chat/conversations/{id}/send`，确认前端能收到结构化 SSE（content/tool_call/done 都能渲染）。
- [ ] MySQL `message` 表新增 user + assistant 两条（assistant 为累计正文）。
- [ ] 故意停掉 Python，创建新 Agent → 应返回 503/报错，且 MySQL **无新增行**（验证 @Transactional 回滚）。
- [ ] `grep -rn AgentClient acg-chat/src` → 无结果。

- [ ] **Step 5：运行 `/code-review`（全量），通过后 git add 剩余变更（若有）**

```bash
git status   # 确认无遗漏
```

---

## Phase A 完成定义（Definition of Done）

- [ ] agent 表新增 11 列，存量数据迁移完成（所有 agent 有 `python_agent_id`）
- [ ] `POST /api/chat/conversations/{id}/send` 经 Python 返回结构化 SSE，前端能渲染 content/tool_call/done
- [ ] Agent CUD 同步 Python，Python 失败时 MySQL 回滚（单测验证控制流 + 手动验证真实回滚）
- [ ] `AgentClient` 已删除，`grep` 无残留
- [ ] `mvn test -pl acg-chat -am` 全绿，无跳过
- [ ] 变更日志已提交

---

## 备注：未在单测覆盖、需手动/集成验证的点（Rule 12）

1. **`@Transactional` 真实回滚**：Mockito 单测无法触发真实事务回滚，仅验证控制流；上线前用真实 MySQL + 失败的 Python 手动验证（Task 11 Step 4）。
2. **WebClient 实际 HTTP/SSE 流**：`PythonAiClient.streamChat` / `createAgent` 等的端到端 HTTP 行为按项目惯例归集成测试（参考既有 `ChatServiceImplTest` 注释），Phase A 以手动验收覆盖。
3. **超时行为**：`connect-timeout` / `read-timeout` / `stream-read-timeout` 的实际触发需在真实网络环境下手动观察。
