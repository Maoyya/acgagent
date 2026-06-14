# Java ↔ Python AI 集成 Phase B 实现计划（KB / Doc / Tool 代理）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Java acg-chat 新增知识库 / 文档 / 工具的管理接口，全部代理到 Python acgagent-ai，前端统一走 Gateway，admin 鉴权。

**Architecture:** 纯代理、无 MySQL（Python 是 KB/Doc/Tool 的存储真相源）。新增 acg-chat `kb/`、`tool/` 两个包的 Controller+Service，扩展 `PythonAiClient` 增加 KB/Doc/Tool 的 HTTP 调用（含 multipart 文档上传）与泛型 `extractData`，acg-common 新增 KB/Doc/Tool VO（camelCase + `@JsonAlias` 对齐 Python snake_case）。Gateway 路由追加 `/api/knowledge-bases/**,/api/tools/**` → acg-chat。详见 `docs/superpowers/specs/2026-06-14-java-python-ai-integration-design.md` §7。

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring WebFlux (WebClient + multipart), MyBatis-Plus（本期不落库）, jjwt/Jackson (`@JsonAlias`), JUnit5 + Mockito + AssertJ。

---

## 全局约定（每个 Task 都要遵守）

1. **分支**：`acgagent_dev`（规约九.1）。
2. **Code Review**：每个 Task 的 `git add` **之前**先 `/code-review`（规约八.1）；本计划执行时由控制器的规约+质量审查子代理替代。
3. **不自动 commit**：只 `git add`（规约九.2）。
4. **测试命令**：acg-chat 用 `mvn test -pl acg-chat -am -Dtest=<类名> -Dsurefire.failIfNoSpecifiedTests=false`；acg-common 用 `mvn test -pl acg-common -Dtest=<类名>`。
5. **前置条件**：Phase A（Task 1–11）已完成，`PythonAiClient` / `PythonAgentProperties` / `pythonWebClient` 就绪。

---

## File Structure

| 文件 | 责任 | 动作 |
|---|---|---|
| `acg-common/.../model/KnowledgeBaseVO.java` | 知识库 VO（camelCase + JsonAlias） | 新建 |
| `acg-common/.../model/KnowledgeBaseRequest.java` | 知识库 创建/更新 请求 | 新建 |
| `acg-common/.../model/DocumentVO.java` | 文档 VO | 新建 |
| `acg-common/.../model/ToolVO.java` | 工具 VO | 新建 |
| `acg-common/.../model/ToolCreateRequest.java` | 工具创建请求 | 新建 |
| `acg-chat/.../agent/client/PythonAiClient.java` | Python 出口，扩展 KB/Doc/Tool | 改 |
| `acg-chat/.../kb/service/KnowledgeBaseService.java`(+Impl) | 知识库代理 | 新建 |
| `acg-chat/.../kb/controller/KnowledgeBaseController.java` | 知识库 REST（admin） | 新建 |
| `acg-chat/.../kb/service/DocumentService.java`(+Impl) | 文档代理（含 multipart） | 新建 |
| `acg-chat/.../kb/controller/DocumentController.java` | 文档 REST（admin） | 新建 |
| `acg-chat/.../tool/service/ToolService.java`(+Impl) | 工具代理 | 新建 |
| `acg-chat/.../tool/controller/ToolController.java` | 工具 REST（admin） | 新建 |
| `acg-chat/src/test/...` | 各 Service 单测 + PythonAiClient 扩展单测 | 新建 |
| Nacos `acg-gateway.yaml` | 路由追加 | 手动 |
| `docs/changelogs/2026-06-15-phase-b-kb-doc-tool.md` | 变更日志 | 新建 |

---

## Task 1：acg-common 新增 KB / Document / Tool VO（TDD）

**Files:**
- Create: `acg-common/src/main/java/com/darkness/common/model/KnowledgeBaseVO.java`
- Create: `acg-common/src/main/java/com/darkness/common/model/KnowledgeBaseRequest.java`
- Create: `acg-common/src/main/java/com/darkness/common/model/DocumentVO.java`
- Create: `acg-common/src/main/java/com/darkness/common/model/ToolVO.java`
- Create: `acg-common/src/main/java/com/darkness/common/model/ToolCreateRequest.java`
- Test: `acg-common/src/test/java/com/darkness/common/model/PhaseBVoParsingTest.java`

> VOs 用 `@Data` + camelCase 字段，对 Python 返回的 snake_case 用 `@JsonAlias` 对齐；前端序列化仍为 camelCase。

- [ ] **Step 1：写失败测试** `acg-common/src/test/java/com/darkness/common/model/PhaseBVoParsingTest.java`

```java
package com.darkness.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase B VO 反序列化测试：验证 camelCase VO 能从 Python 的 snake_case JSON 解析（@JsonAlias）。
 */
class PhaseBVoParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void documentVO_fromPythonSnakeCase() throws Exception {
        String json = "{\"id\":\"d1\",\"knowledge_base_id\":\"kb1\",\"file_name\":\"a.pdf\","
                + "\"file_size\":1024,\"chunk_count\":8,\"status\":\"completed\","
                + "\"error_message\":null,\"created_at\":\"2026-06-15T10:00:00\"}";
        DocumentVO vo = mapper.readValue(json, DocumentVO.class);
        assertThat(vo.getId()).isEqualTo("d1");
        assertThat(vo.getKnowledgeBaseId()).isEqualTo("kb1");
        assertThat(vo.getFileName()).isEqualTo("a.pdf");
        assertThat(vo.getFileSize()).isEqualTo(1024L);
        assertThat(vo.getChunkCount()).isEqualTo(8);
        assertThat(vo.getStatus()).isEqualTo("completed");
    }

    @Test
    void knowledgeBaseVO_fromPythonSnakeCase() throws Exception {
        String json = "{\"id\":\"kb1\",\"name\":\"KB\",\"description\":\"d\","
                + "\"document_count\":3,\"chunk_count\":50}";
        KnowledgeBaseVO vo = mapper.readValue(json, KnowledgeBaseVO.class);
        assertThat(vo.getId()).isEqualTo("kb1");
        assertThat(vo.getName()).isEqualTo("KB");
        assertThat(vo.getDocumentCount()).isEqualTo(3);
        assertThat(vo.getChunkCount()).isEqualTo(50);
    }

    @Test
    void toolVO_fromPythonSnakeCase() throws Exception {
        String json = "{\"id\":\"t1\",\"name\":\"T\",\"description\":\"desc\",\"type\":\"api\",\"created_at\":\"2026-06-15T10:00:00\"}";
        ToolVO vo = mapper.readValue(json, ToolVO.class);
        assertThat(vo.getId()).isEqualTo("t1");
        assertThat(vo.getType()).isEqualTo("api");
    }
}
```

- [ ] **Step 2：跑测试，确认失败**

```bash
mvn test -pl acg-common -Dtest=PhaseBVoParsingTest
```
Expected: 编译失败（VO 不存在）。

- [ ] **Step 3：创建 KnowledgeBaseVO.java**

```java
package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 知识库视图对象，代理 Python acgagent-ai 的 KnowledgeBase schema。
 * <p>
 * camelCase 字段 + @JsonAlias 对齐 Python 的 snake_case；前端序列化仍为 camelCase。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeBaseVO {

    /** 知识库 id（Python 侧 string） */
    private String id;

    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;

    /** Embedding 配置：provider/model/baseUrl/apiKey */
    @JsonAlias("embedding_config")
    private Map<String, Object> embeddingConfig;

    /** 分块配置：chunkSize/chunkOverlap/separators */
    @JsonAlias("chunk_config")
    private Map<String, Object> chunkConfig;

    /** 文档数量 */
    @JsonAlias("document_count")
    private Integer documentCount;

    /** 分块数量 */
    @JsonAlias("chunk_count")
    private Integer chunkCount;
}
```

- [ ] **Step 4：创建 KnowledgeBaseRequest.java**

```java
package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 知识库创建/更新请求，透传给 Python。name 必填。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeBaseRequest {

    /** 知识库名称 */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称长度不能超过128个字符")
    private String name;

    /** 描述 */
    private String description;

    /** Embedding 配置（透传 Python embedding_config） */
    @JsonAlias("embedding_config")
    private Map<String, Object> embeddingConfig;

    /** 分块配置（透传 Python chunk_config） */
    @JsonAlias("chunk_config")
    private Map<String, Object> chunkConfig;
}
```

- [ ] **Step 5：创建 DocumentVO.java**

```java
package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 文档视图对象，代理 Python DocumentVO。文档上传后异步处理，status 流转 processing→completed/failed。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentVO {

    /** 文档 id（Python 侧 string） */
    private String id;

    /** 所属知识库 id */
    @JsonAlias("knowledge_base_id")
    private String knowledgeBaseId;

    /** 原始文件名 */
    @JsonAlias("file_name")
    private String fileName;

    /** 文件大小（字节） */
    @JsonAlias("file_size")
    private Long fileSize;

    /** 分块数（处理完成后填充） */
    @JsonAlias("chunk_count")
    private Integer chunkCount;

    /** 状态：processing / completed / failed */
    private String status;

    /** 失败时的错误信息 */
    @JsonAlias("error_message")
    private String errorMessage;
}
```

- [ ] **Step 6：创建 ToolVO.java**

```java
package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * 工具视图对象，代理 Python ToolVO。type: api(自定义) / builtin(内置 calculator/web_search/knowledge_search)。
 * 内置工具不可删除。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolVO {

    /** 工具 id（Python 侧 string；内置工具为 calculator/web_search/knowledge_search） */
    private String id;

    /** 工具显示名 */
    private String name;

    /** 工具描述（喂给 LLM） */
    private String description;

    /** 类型：api / builtin */
    private String type;

    /** 自定义 API 工具的 HTTP 调用配置（url/method/headers/query_params/body_template） */
    private Map<String, Object> config;

    /** 参数 schema（type/properties/required） */
    private Map<String, Object> parameters;
}
```

- [ ] **Step 7：创建 ToolCreateRequest.java**

```java
package com.darkness.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 工具创建请求，透传给 Python。name/description 必填。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCreateRequest {

    /** 工具显示名 */
    @NotBlank(message = "工具名称不能为空")
    @Size(max = 128, message = "工具名称长度不能超过128个字符")
    private String name;

    /** 工具描述 */
    @NotBlank(message = "工具描述不能为空")
    private String description;

    /** 类型，默认 api */
    private String type = "api";

    /** 自定义 API 工具的 HTTP 调用配置（透传 Python config） */
    private Map<String, Object> config;

    /** 参数 schema（透传 Python parameters） */
    private Map<String, Object> parameters;
}
```

- [ ] **Step 8：跑测试，确认通过**

```bash
mvn test -pl acg-common -Dtest=PhaseBVoParsingTest
```
Expected: BUILD SUCCESS，3 测试通过。

- [ ] **Step 9：CR + git add**

```bash
git add acg-common/src/main/java/com/darkness/common/model/KnowledgeBaseVO.java \
        acg-common/src/main/java/com/darkness/common/model/KnowledgeBaseRequest.java \
        acg-common/src/main/java/com/darkness/common/model/DocumentVO.java \
        acg-common/src/main/java/com/darkness/common/model/ToolVO.java \
        acg-common/src/main/java/com/darkness/common/model/ToolCreateRequest.java \
        acg-common/src/test/java/com/darkness/common/model/PhaseBVoParsingTest.java
```

---

## Task 2：PythonAiClient 扩展（KB / Doc / Tool + 泛型 extractData）

**Files:**
- Modify: `acg-chat/src/main/java/com/darkness/agent/client/PythonAiClient.java`
- Test: `acg-chat/src/test/java/com/darkness/agent/client/PythonAiClientTest.java`

> 新增泛型 `extractData(String json, Class<T>)` / `extractDataList(String json, Class<T>)`，以及 KB/Doc/Tool 的调用方法（含 multipart 文档上传）。HTTP/SSE 流不单测，纯解析方法单测。

- [ ] **Step 1：在 PythonAiClientTest 补失败测试**

在 `PythonAiClientTest` 类内追加：

```java
    @Test
    void extractData_success_returnsDataObject() {
        String json = "{\"code\":200,\"data\":{\"id\":\"kb1\",\"name\":\"KB\"}}";
        com.darkness.common.model.KnowledgeBaseVO vo =
                client.extractData(json, com.darkness.common.model.KnowledgeBaseVO.class);
        assertThat(vo).isNotNull();
        assertThat(vo.getId()).isEqualTo("kb1");
        assertThat(vo.getName()).isEqualTo("KB");
    }

    @Test
    void extractData_pythonError_throwsWithCode() {
        String json = "{\"code\":404,\"message\":\"Knowledge base not found\"}";
        assertThatThrownBy(() -> client.extractData(json, com.darkness.common.model.KnowledgeBaseVO.class))
                .isInstanceOf(com.darkness.common.exception.BizException.class)
                .extracting("code").isEqualTo(404);
    }

    @Test
    void extractDataList_success() {
        String json = "{\"code\":200,\"data\":[{\"id\":\"d1\",\"file_name\":\"a.pdf\"},{\"id\":\"d2\",\"file_name\":\"b.txt\"}]}";
        java.util.List<com.darkness.common.model.DocumentVO> list =
                client.extractDataList(json, com.darkness.common.model.DocumentVO.class);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getFileName()).isEqualTo("a.pdf");
        assertThat(list.get(1).getId()).isEqualTo("d2");
    }
```

- [ ] **Step 2：跑测试，确认失败**

```bash
mvn test -pl acg-chat -am -Dtest=PythonAiClientTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: 编译失败（extractData/extractDataList 不存在）。

- [ ] **Step 3：在 PythonAiClient.java 追加方法与 import**

新增 import：

```java
import com.darkness.common.model.DocumentVO;
import com.darkness.common.model.KnowledgeBaseRequest;
import com.darkness.common.model.KnowledgeBaseVO;
import com.darkness.common.model.ToolCreateRequest;
import com.darkness.common.model.ToolVO;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.http.MediaType;
```

在类内追加方法（KB / Doc / Tool + 泛型解析）：

```java
    /**
     * 从 Python 通用 Result 响应中提取 data 字段并反序列化为指定类型。
     * code != 200 时抛携带其 code 的 BizException。
     */
    public <T> T extractData(String json, Class<T> type) {
        JsonNode root = readTree(json);
        int code = root.path("code").asInt(200);
        if (code != 200) {
            throw new BizException(code, root.path("message").asText("Python request failed"));
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(data, type);
        } catch (Exception e) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "AI 引擎响应解析失败");
        }
    }

    /**
     * 从 Python 通用 Result 响应中提取 data 数组并反序列化为指定类型的列表。
     */
    public <T> java.util.List<T> extractDataList(String json, Class<T> type) {
        JsonNode root = readTree(json);
        int code = root.path("code").asInt(200);
        if (code != 200) {
            throw new BizException(code, root.path("message").asText("Python request failed"));
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull() || !data.isArray()) {
            return java.util.List.of();
        }
        try {
            java.util.List<T> result = new java.util.ArrayList<>();
            for (JsonNode node : data) {
                result.add(objectMapper.treeToValue(node, type));
            }
            return result;
        } catch (Exception e) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "AI 引擎响应解析失败");
        }
    }

    // ==================== 知识库 ====================

    public java.util.List<KnowledgeBaseVO> listKnowledgeBases() {
        return extractDataList(getJson("/api/v1/knowledge-bases"), KnowledgeBaseVO.class);
    }

    public KnowledgeBaseVO getKnowledgeBase(String kbId) {
        return extractData(getJson("/api/v1/knowledge-bases/" + kbId), KnowledgeBaseVO.class);
    }

    public KnowledgeBaseVO createKnowledgeBase(KnowledgeBaseRequest req) {
        String json = pythonWebClient.post()
                .uri("/api/v1/knowledge-bases")
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(req)
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
        return extractData(json, KnowledgeBaseVO.class);
    }

    public KnowledgeBaseVO updateKnowledgeBase(String kbId, KnowledgeBaseRequest req) {
        String json = pythonWebClient.put()
                .uri("/api/v1/knowledge-bases/{kbId}", kbId)
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(req)
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
        return extractData(json, KnowledgeBaseVO.class);
    }

    public void deleteKnowledgeBase(String kbId) {
        verifySuccess(getJsonDelete("/api/v1/knowledge-bases/" + kbId));
    }

    // ==================== 文档 ====================

    public java.util.List<DocumentVO> listDocuments(String kbId) {
        return extractDataList(getJson("/api/v1/knowledge-bases/" + kbId + "/documents"), DocumentVO.class);
    }

    public DocumentVO getDocument(String kbId, String docId) {
        return extractData(getJson("/api/v1/knowledge-bases/" + kbId + "/documents/" + docId), DocumentVO.class);
    }

    /**
     * 上传文档（multipart 转发）。Python 异步处理，返回 status=processing 的 DocumentVO。
     * file.getResource() 返回 MultipartFileResource，自带原始文件名，直接作为 multipart part。
     */
    public DocumentVO uploadDocument(String kbId, MultipartFile file) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getResource());
        String json = pythonWebClient.post()
                .uri("/api/v1/knowledge-bases/{kbId}/documents", kbId)
                .header("X-API-Key", props.getApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
        return extractData(json, DocumentVO.class);
    }

    public void deleteDocument(String kbId, String docId) {
        verifySuccess(getJsonDelete("/api/v1/knowledge-bases/" + kbId + "/documents/" + docId));
    }

    // ==================== 工具 ====================

    public java.util.List<ToolVO> listTools() {
        return extractDataList(getJson("/api/v1/tools"), ToolVO.class);
    }

    public ToolVO getTool(String toolId) {
        return extractData(getJson("/api/v1/tools/" + toolId), ToolVO.class);
    }

    public ToolVO createTool(ToolCreateRequest req) {
        String json = pythonWebClient.post()
                .uri("/api/v1/tools")
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(req)
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
        return extractData(json, ToolVO.class);
    }

    public void deleteTool(String toolId) {
        verifySuccess(getJsonDelete("/api/v1/tools/" + toolId));
    }

    // ==================== 内部 GET 辅助 ====================

    private String getJson(String path) {
        return pythonWebClient.get()
                .uri(path)
                .header("X-API-Key", props.getApiKey())
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
    }

    private String getJsonDelete(String path) {
        return pythonWebClient.delete()
                .uri(path)
                .header("X-API-Key", props.getApiKey())
                .retrieve().bodyToMono(String.class)
                .timeout(Duration.ofMillis(props.getReadTimeout())).block();
    }
```

> 说明：`getJson`/`getJsonDelete` 是私有辅助，统一带 X-API-Key + readTimeout + block。`extractDataList` 用手动 for 循环逐个 `treeToValue`，无需 TypeReference。

- [ ] **Step 4：跑测试，确认通过**

```bash
mvn test -pl acg-chat -am -Dtest=PythonAiClientTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: BUILD SUCCESS，原 7 + 新 3 = 10 测试通过。

- [ ] **Step 5：编译 acg-chat**

```bash
mvn clean compile -pl acg-chat -am
```
Expected: BUILD SUCCESS。

- [ ] **Step 6：CR + git add**

```bash
git add acg-chat/src/main/java/com/darkness/agent/client/PythonAiClient.java \
        acg-chat/src/test/java/com/darkness/agent/client/PythonAiClientTest.java
```

---

## Task 3：KnowledgeBaseService + Controller（admin）

**Files:**
- Create: `acg-chat/src/main/java/com/darkness/kb/service/KnowledgeBaseService.java`
- Create: `acg-chat/src/main/java/com/darkness/kb/service/impl/KnowledgeBaseServiceImpl.java`
- Create: `acg-chat/src/main/java/com/darkness/kb/controller/KnowledgeBaseController.java`
- Test: `acg-chat/src/test/java/com/darkness/kb/service/impl/KnowledgeBaseServiceImplTest.java`

> 纯代理 Service：校验由 Bean Validation 在 Controller 层完成，Service 只转发 + 返回。单测 mock PythonAiClient 验证"正确转发 + 错误透传"。

- [ ] **Step 1：写失败测试** `acg-chat/src/test/java/com/darkness/kb/service/impl/KnowledgeBaseServiceImplTest.java`

```java
package com.darkness.kb.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.exception.BizException;
import com.darkness.common.model.KnowledgeBaseRequest;
import com.darkness.common.model.KnowledgeBaseVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 知识库代理 Service 测试：验证正确转发到 PythonAiClient，并透传 Python 错误。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private PythonAiClient pythonAiClient;

    @InjectMocks
    private KnowledgeBaseServiceImpl knowledgeBaseService;

    @Test
    void list_delegatesToPython() {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId("kb1");
        when(pythonAiClient.listKnowledgeBases()).thenReturn(List.of(vo));

        List<KnowledgeBaseVO> result = knowledgeBaseService.list();

        assertThat(result).hasSize(1);
        verify(pythonAiClient).listKnowledgeBases();
    }

    @Test
    void get_delegatesToPython() {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId("kb1");
        when(pythonAiClient.getKnowledgeBase("kb1")).thenReturn(vo);

        KnowledgeBaseVO result = knowledgeBaseService.get("kb1");

        assertThat(result.getId()).isEqualTo("kb1");
    }

    @Test
    void create_delegatesToPython() {
        KnowledgeBaseRequest req = new KnowledgeBaseRequest();
        req.setName("KB");
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId("kb1");
        when(pythonAiClient.createKnowledgeBase(req)).thenReturn(vo);

        KnowledgeBaseVO result = knowledgeBaseService.create(req);

        assertThat(result.getId()).isEqualTo("kb1");
    }

    @Test
    void update_delegatesToPython() {
        KnowledgeBaseRequest req = new KnowledgeBaseRequest();
        req.setName("KB2");
        when(pythonAiClient.updateKnowledgeBase(eq("kb1"), any(KnowledgeBaseRequest.class)))
                .thenReturn(new KnowledgeBaseVO());

        knowledgeBaseService.update("kb1", req);

        verify(pythonAiClient).updateKnowledgeBase(eq("kb1"), eq(req));
    }

    @Test
    void delete_delegatesToPython() {
        knowledgeBaseService.delete("kb1");
        verify(pythonAiClient).deleteKnowledgeBase("kb1");
    }

    @Test
    void pythonError_propagatesAsBizException() {
        when(pythonAiClient.getKnowledgeBase("missing"))
                .thenThrow(new BizException(404, "Knowledge base not found"));

        assertThatThrownBy(() -> knowledgeBaseService.get("missing"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(404);
    }
}
```

- [ ] **Step 2：跑测试，确认失败**

```bash
mvn test -pl acg-chat -am -Dtest=KnowledgeBaseServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: 编译失败（Service 不存在）。

- [ ] **Step 3：创建 KnowledgeBaseService.java 接口**

```java
package com.darkness.kb.service;

import com.darkness.common.model.KnowledgeBaseRequest;
import com.darkness.common.model.KnowledgeBaseVO;

import java.util.List;

/**
 * 知识库代理服务：将知识库管理请求转发到 Python AI 引擎。
 */
public interface KnowledgeBaseService {

    /** 列出所有知识库。 */
    List<KnowledgeBaseVO> list();

    /** 获取知识库详情。 */
    KnowledgeBaseVO get(String kbId);

    /** 创建知识库。 */
    KnowledgeBaseVO create(KnowledgeBaseRequest request);

    /** 更新知识库。 */
    KnowledgeBaseVO update(String kbId, KnowledgeBaseRequest request);

    /** 删除知识库。 */
    void delete(String kbId);
}
```

- [ ] **Step 4：创建 KnowledgeBaseServiceImpl.java**

```java
package com.darkness.kb.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.model.KnowledgeBaseRequest;
import com.darkness.common.model.KnowledgeBaseVO;
import com.darkness.kb.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库代理服务实现，转发到 PythonAiClient。Python 是知识库存储的真相源，Java 不落库。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final PythonAiClient pythonAiClient;

    @Override
    public List<KnowledgeBaseVO> list() {
        return pythonAiClient.listKnowledgeBases();
    }

    @Override
    public KnowledgeBaseVO get(String kbId) {
        return pythonAiClient.getKnowledgeBase(kbId);
    }

    @Override
    public KnowledgeBaseVO create(KnowledgeBaseRequest request) {
        return pythonAiClient.createKnowledgeBase(request);
    }

    @Override
    public KnowledgeBaseVO update(String kbId, KnowledgeBaseRequest request) {
        return pythonAiClient.updateKnowledgeBase(kbId, request);
    }

    @Override
    public void delete(String kbId) {
        pythonAiClient.deleteKnowledgeBase(kbId);
    }
}
```

- [ ] **Step 5：创建 KnowledgeBaseController.java**

```java
package com.darkness.kb.controller;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.model.KnowledgeBaseRequest;
import com.darkness.common.model.KnowledgeBaseVO;
import com.darkness.common.result.Result;
import com.darkness.kb.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理控制器，全部代理到 Python AI 引擎，仅管理员可访问。
 */
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 列出所有知识库。
     * GET /api/knowledge-bases（需认证，仅管理员）
     */
    @GetMapping
    @RequireRole("admin")
    public Result<List<KnowledgeBaseVO>> list() {
        return Result.success(knowledgeBaseService.list());
    }

    /**
     * 获取知识库详情。
     * GET /api/knowledge-bases/{id}（需认证，仅管理员）
     *
     * @param id 知识库 id
     */
    @GetMapping("/{id}")
    @RequireRole("admin")
    public Result<KnowledgeBaseVO> get(@PathVariable String id) {
        return Result.success(knowledgeBaseService.get(id));
    }

    /**
     * 创建知识库，name 必填。
     * POST /api/knowledge-bases（需认证，仅管理员）
     *
     * @param request 知识库信息
     */
    @PostMapping
    @RequireRole("admin")
    public Result<KnowledgeBaseVO> create(@RequestBody @Valid KnowledgeBaseRequest request) {
        return Result.success(knowledgeBaseService.create(request));
    }

    /**
     * 更新知识库。
     * PUT /api/knowledge-bases/{id}（需认证，仅管理员）
     *
     * @param id      知识库 id
     * @param request 知识库信息
     */
    @PutMapping("/{id}")
    @RequireRole("admin")
    public Result<KnowledgeBaseVO> update(@PathVariable String id,
                                          @RequestBody @Valid KnowledgeBaseRequest request) {
        return Result.success(knowledgeBaseService.update(id, request));
    }

    /**
     * 删除知识库。
     * DELETE /api/knowledge-bases/{id}（需认证，仅管理员）
     *
     * @param id 知识库 id
     */
    @DeleteMapping("/{id}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable String id) {
        knowledgeBaseService.delete(id);
        return Result.success();
    }
}
```

- [ ] **Step 6：跑测试，确认通过**

```bash
mvn test -pl acg-chat -am -Dtest=KnowledgeBaseServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: BUILD SUCCESS，6 测试通过。

- [ ] **Step 7：CR + git add**

```bash
git add acg-chat/src/main/java/com/darkness/kb/ acg-chat/src/test/java/com/darkness/kb/
```

---

## Task 4：DocumentService + Controller（含 multipart 上传）

**Files:**
- Create: `acg-chat/src/main/java/com/darkness/kb/service/DocumentService.java`
- Create: `acg-chat/src/main/java/com/darkness/kb/service/impl/DocumentServiceImpl.java`
- Create: `acg-chat/src/main/java/com/darkness/kb/controller/DocumentController.java`
- Test: `acg-chat/src/test/java/com/darkness/kb/service/impl/DocumentServiceImplTest.java`

> multipart 上传：Controller 收 `@RequestParam("file") MultipartFile`，Service 透传给 `PythonAiClient.uploadDocument`。上传本身是集成级（不单测），list/get/delete 代理 + 错误透传单测。

- [ ] **Step 1：写失败测试** `acg-chat/src/test/java/com/darkness/kb/service/impl/DocumentServiceImplTest.java`

```java
package com.darkness.kb.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.exception.BizException;
import com.darkness.common.model.DocumentVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 文档代理 Service 测试。upload（multipart 转发）按惯例不单测，验证 list/get/delete + 错误透传。
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private PythonAiClient pythonAiClient;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Test
    void list_delegatesToPython() {
        DocumentVO vo = new DocumentVO();
        vo.setId("d1");
        when(pythonAiClient.listDocuments("kb1")).thenReturn(List.of(vo));

        List<DocumentVO> result = documentService.list("kb1");

        assertThat(result).hasSize(1);
    }

    @Test
    void get_delegatesToPython() {
        DocumentVO vo = new DocumentVO();
        vo.setId("d1");
        when(pythonAiClient.getDocument("kb1", "d1")).thenReturn(vo);

        assertThat(documentService.get("kb1", "d1").getId()).isEqualTo("d1");
    }

    @Test
    void delete_delegatesToPython() {
        documentService.delete("kb1", "d1");
        verify(pythonAiClient).deleteDocument("kb1", "d1");
    }

    @Test
    void upload_delegatesToPython() {
        MultipartFile file = mock(MultipartFile.class);
        DocumentVO vo = new DocumentVO();
        vo.setId("d1");
        vo.setStatus("processing");
        when(pythonAiClient.uploadDocument("kb1", file)).thenReturn(vo);

        DocumentVO result = documentService.upload("kb1", file);

        assertThat(result.getId()).isEqualTo("d1");
        assertThat(result.getStatus()).isEqualTo("processing");
    }

    @Test
    void pythonError_propagatesAsBizException() {
        when(pythonAiClient.listDocuments("kbMissing"))
                .thenThrow(new BizException(404, "Knowledge base not found"));

        assertThatThrownBy(() -> documentService.list("kbMissing"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(404);
    }
}
```

- [ ] **Step 2：跑测试，确认失败**

```bash
mvn test -pl acg-chat -am -Dtest=DocumentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: 编译失败。

- [ ] **Step 3：创建 DocumentService.java 接口**

```java
package com.darkness.kb.service;

import com.darkness.common.model.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档代理服务：转发到 Python AI 引擎，包含 multipart 文档上传。
 */
public interface DocumentService {

    /** 列出知识库下的文档。 */
    List<DocumentVO> list(String kbId);

    /** 获取文档详情。 */
    DocumentVO get(String kbId, String docId);

    /** 上传文档（multipart），Python 异步处理，返回 status=processing 的文档元数据。 */
    DocumentVO upload(String kbId, MultipartFile file);

    /** 删除文档。 */
    void delete(String kbId, String docId);
}
```

- [ ] **Step 4：创建 DocumentServiceImpl.java**

```java
package com.darkness.kb.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.model.DocumentVO;
import com.darkness.kb.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档代理服务实现，转发到 PythonAiClient。
 */
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final PythonAiClient pythonAiClient;

    @Override
    public List<DocumentVO> list(String kbId) {
        return pythonAiClient.listDocuments(kbId);
    }

    @Override
    public DocumentVO get(String kbId, String docId) {
        return pythonAiClient.getDocument(kbId, docId);
    }

    @Override
    public DocumentVO upload(String kbId, MultipartFile file) {
        return pythonAiClient.uploadDocument(kbId, file);
    }

    @Override
    public void delete(String kbId, String docId) {
        pythonAiClient.deleteDocument(kbId, docId);
    }
}
```

- [ ] **Step 5：创建 DocumentController.java**

```java
package com.darkness.kb.controller;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.model.DocumentVO;
import com.darkness.common.result.Result;
import com.darkness.kb.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理控制器（隶属知识库），全部代理到 Python，仅管理员可访问。
 */
@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文档（multipart）。Python 异步处理，返回 status=processing 的元数据，前端可轮询状态。
     * POST /api/knowledge-bases/{kbId}/documents（需认证，仅管理员）
     *
     * @param kbId 所属知识库 id
     * @param file 上传的文件
     */
    @PostMapping
    @RequireRole("admin")
    public Result<DocumentVO> upload(@PathVariable String kbId,
                                     @RequestParam("file") MultipartFile file) {
        return Result.success(documentService.upload(kbId, file));
    }

    /**
     * 列出知识库下的文档。
     * GET /api/knowledge-bases/{kbId}/documents（需认证，仅管理员）
     */
    @GetMapping
    @RequireRole("admin")
    public Result<List<DocumentVO>> list(@PathVariable String kbId) {
        return Result.success(documentService.list(kbId));
    }

    /**
     * 获取文档详情。
     * GET /api/knowledge-bases/{kbId}/documents/{docId}（需认证，仅管理员）
     */
    @GetMapping("/{docId}")
    @RequireRole("admin")
    public Result<DocumentVO> get(@PathVariable String kbId, @PathVariable String docId) {
        return Result.success(documentService.get(kbId, docId));
    }

    /**
     * 删除文档。
     * DELETE /api/knowledge-bases/{kbId}/documents/{docId}（需认证，仅管理员）
     */
    @DeleteMapping("/{docId}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable String kbId, @PathVariable String docId) {
        documentService.delete(kbId, docId);
        return Result.success();
    }
}
```

- [ ] **Step 6：跑测试，确认通过**

```bash
mvn test -pl acg-chat -am -Dtest=DocumentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: BUILD SUCCESS，5 测试通过。

- [ ] **Step 7：CR + git add**

```bash
git add acg-chat/src/main/java/com/darkness/kb/service/DocumentService.java \
        acg-chat/src/main/java/com/darkness/kb/service/impl/DocumentServiceImpl.java \
        acg-chat/src/main/java/com/darkness/kb/controller/DocumentController.java \
        acg-chat/src/test/java/com/darkness/kb/service/impl/DocumentServiceImplTest.java
```

---

## Task 5：ToolService + Controller（admin）

**Files:**
- Create: `acg-chat/src/main/java/com/darkness/tool/service/ToolService.java`
- Create: `acg-chat/src/main/java/com/darkness/tool/service/impl/ToolServiceImpl.java`
- Create: `acg-chat/src/main/java/com/darkness/tool/controller/ToolController.java`
- Test: `acg-chat/src/test/java/com/darkness/tool/service/impl/ToolServiceImplTest.java`

- [ ] **Step 1：写失败测试** `acg-chat/src/test/java/com/darkness/tool/service/impl/ToolServiceImplTest.java`

```java
package com.darkness.tool.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.exception.BizException;
import com.darkness.common.model.ToolCreateRequest;
import com.darkness.common.model.ToolVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 工具代理 Service 测试：验证转发 + 错误透传（含内置工具不可删的 400 错误透传）。
 */
@ExtendWith(MockitoExtension.class)
class ToolServiceImplTest {

    @Mock
    private PythonAiClient pythonAiClient;

    @InjectMocks
    private ToolServiceImpl toolService;

    @Test
    void list_delegatesToPython() {
        when(pythonAiClient.listTools()).thenReturn(List.of(new ToolVO()));
        assertThat(toolService.list()).hasSize(1);
    }

    @Test
    void get_delegatesToPython() {
        ToolVO vo = new ToolVO();
        vo.setId("t1");
        when(pythonAiClient.getTool("t1")).thenReturn(vo);
        assertThat(toolService.get("t1").getId()).isEqualTo("t1");
    }

    @Test
    void create_delegatesToPython() {
        ToolCreateRequest req = new ToolCreateRequest();
        req.setName("T");
        req.setDescription("d");
        when(pythonAiClient.createTool(req)).thenReturn(new ToolVO());
        toolService.create(req);
        verify(pythonAiClient).createTool(req);
    }

    @Test
    void delete_builtinTool_propagatesError() {
        // 内置工具不可删，Python 返回 code!=200，PythonAiClient 抛 BizException
        doThrow(new BizException(400, "builtin tool cannot be deleted"))
                .when(pythonAiClient).deleteTool("calculator");
        assertThatThrownBy(() -> toolService.delete("calculator"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(400);
    }
}
```

- [ ] **Step 2：跑测试，确认失败**

```bash
mvn test -pl acg-chat -am -Dtest=ToolServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: 编译失败。

- [ ] **Step 3：创建 ToolService.java**

```java
package com.darkness.tool.service;

import com.darkness.common.model.ToolCreateRequest;
import com.darkness.common.model.ToolVO;

import java.util.List;

/**
 * 工具代理服务：转发到 Python AI 引擎。
 */
public interface ToolService {

    /** 列出所有工具（内置 + 自定义）。 */
    List<ToolVO> list();

    /** 获取工具详情。 */
    ToolVO get(String toolId);

    /** 创建自定义 API 工具。 */
    ToolVO create(ToolCreateRequest request);

    /** 删除工具（内置工具不可删，错误透传）。 */
    void delete(String toolId);
}
```

- [ ] **Step 4：创建 ToolServiceImpl.java**

```java
package com.darkness.tool.service.impl;

import com.darkness.agent.client.PythonAiClient;
import com.darkness.common.model.ToolCreateRequest;
import com.darkness.common.model.ToolVO;
import com.darkness.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工具代理服务实现，转发到 PythonAiClient。
 */
@Service
@RequiredArgsConstructor
public class ToolServiceImpl implements ToolService {

    private final PythonAiClient pythonAiClient;

    @Override
    public List<ToolVO> list() {
        return pythonAiClient.listTools();
    }

    @Override
    public ToolVO get(String toolId) {
        return pythonAiClient.getTool(toolId);
    }

    @Override
    public ToolVO create(ToolCreateRequest request) {
        return pythonAiClient.createTool(request);
    }

    @Override
    public void delete(String toolId) {
        pythonAiClient.deleteTool(toolId);
    }
}
```

- [ ] **Step 5：创建 ToolController.java**

```java
package com.darkness.tool.controller;

import com.darkness.common.annotation.RequireRole;
import com.darkness.common.model.ToolCreateRequest;
import com.darkness.common.model.ToolVO;
import com.darkness.common.result.Result;
import com.darkness.tool.service.ToolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具管理控制器，全部代理到 Python，仅管理员可访问。
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    /**
     * 列出所有工具（内置 + 自定义）。
     * GET /api/tools（需认证，仅管理员）
     */
    @GetMapping
    @RequireRole("admin")
    public Result<List<ToolVO>> list() {
        return Result.success(toolService.list());
    }

    /**
     * 获取工具详情。
     * GET /api/tools/{id}（需认证，仅管理员）
     */
    @GetMapping("/{id}")
    @RequireRole("admin")
    public Result<ToolVO> get(@PathVariable String id) {
        return Result.success(toolService.get(id));
    }

    /**
     * 创建自定义 API 工具，name/description 必填。
     * POST /api/tools（需认证，仅管理员）
     */
    @PostMapping
    @RequireRole("admin")
    public Result<ToolVO> create(@RequestBody @Valid ToolCreateRequest request) {
        return Result.success(toolService.create(request));
    }

    /**
     * 删除工具（内置工具 calculator/web_search/knowledge_search 不可删，错误透传）。
     * DELETE /api/tools/{id}（需认证，仅管理员）
     */
    @DeleteMapping("/{id}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable String id) {
        toolService.delete(id);
        return Result.success();
    }
}
```

- [ ] **Step 6：跑测试，确认通过**

```bash
mvn test -pl acg-chat -am -Dtest=ToolServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: BUILD SUCCESS，4 测试通过。

- [ ] **Step 7：CR + git add**

```bash
git add acg-chat/src/main/java/com/darkness/tool/ acg-chat/src/test/java/com/darkness/tool/
```

---

## Task 6：Gateway 路由 + 变更日志 + 全量回归 + 手动验收

**Files:**
- Create: `docs/changelogs/2026-06-15-phase-b-kb-doc-tool.md`
- Nacos `acg-gateway.yaml`（手动）
- 全量回归（我跑）+ 手动验收（用户本地）

- [ ] **Step 1：变更日志** `docs/changelogs/2026-06-15-phase-b-kb-doc-tool.md`

```markdown
# 2026-06-15 Phase B：知识库 / 文档 / 工具代理接口

## 变更原因
Java acg-chat 新增知识库、文档、工具的管理接口，全部代理到 Python acgagent-ai，
前端统一走 Gateway。spec：docs/superpowers/specs/2026-06-14-java-python-ai-integration-design.md §7。

## 影响范围
- API 新增（均需认证 + admin）：
  - GET/POST/PUT/DELETE `/api/knowledge-bases[/{id}]`
  - POST(multipart)/GET/DELETE `/api/knowledge-bases/{kbId}/documents[/{docId}]`
  - GET/POST/DELETE `/api/tools[/{id}]`
- 架构：纯代理，无 MySQL；Python 是 KB/Doc/Tool 存储真相源。
- Gateway：`agent-service` 路由 Path 追加 `/api/knowledge-bases/**,/api/tools/**`。

## 变更前后对比
新增 acg-chat `kb/`、`tool/` 两个包（Controller + Service），扩展 PythonAiClient，
acg-common 新增 KB/Doc/Tool VO（camelCase + @JsonAlias）。

## 注意
- 文档上传为 multipart 转发，Python 异步处理，返回 status=processing，前端需轮询状态。
- 内置工具（calculator/web_search/knowledge_search）不可删，Python 返回 400 透传。
```

- [ ] **Step 2：Nacos `acg-gateway.yaml` 路由追加（手动）**

把 `agent-service` 路由的 predicates 改为：

```yaml
        - id: agent-service
          uri: lb://acg-chat
          predicates:
            - Path=/api/agents/**,/api/chat/**,/api/knowledge-bases/**,/api/tools/**
```

> 这两个新前缀不在 Gateway 白名单（`JwtAuthFilter` 白名单只有 `/api/auth/**`、`/druid/**`），所以默认走 JWT 鉴权。Java Controller 用 `@RequireRole("admin")` 限制管理员。

- [ ] **Step 3：全量回归**

```bash
mvn test -pl acg-common,acg-chat -am -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: BUILD SUCCESS，acg-common（80+3）+ acg-chat（33+3+6+5+4）全绿。

- [ ] **Step 4：手动验收（用户本地，我无法代劳）**

1. Nacos 改 `acg-gateway.yaml` 路由（Step 2），重启 acg-gateway。
2. 启动 Python acgagent-ai（:8100）。
3. 登录 admin，带 JWT 调：
   - `POST /api/knowledge-bases`（body: `{name}`）→ 返回知识库 VO。
   - `POST /api/knowledge-bases/{id}/documents`（multipart file=a.txt）→ 返回 status=processing 的 DocumentVO；稍后 `GET /api/knowledge-bases/{id}/documents/{docId}` 看 status 转 completed。
   - `GET /api/tools` → 含 3 个内置工具。
   - `DELETE /api/tools/calculator` → 返回 400（内置不可删，透传 Python 错误）。
4. 用非 admin 用户调上述任一接口 → 403（`@RequireRole("admin")` 生效）。

- [ ] **Step 5：CR + git add 剩余**

```bash
git add docs/changelogs/2026-06-15-phase-b-kb-doc-tool.md
git status  # 确认无遗漏
```

---

## Phase B 完成定义（Definition of Done）

- [ ] `/api/knowledge-bases/**`、`/api/tools/**` 经 Java 代理 Python，admin 鉴权生效
- [ ] 文档 multipart 上传正确转发，返回 status=processing
- [ ] 内置工具删除返回 400 透传
- [ ] Python 返回错误时按其 code 透传 BizException
- [ ] `mvn test -pl acg-common,acg-chat -am` 全绿，无跳过
- [ ] 变更日志已提交
- [ ] Nacos `acg-gateway.yaml` 路由已更新

---

## 备注：未在单测覆盖、需手动/集成验证的点（Rule 12）

1. **multipart 文档上传端到端**：`PythonAiClient.uploadDocument` 的 WebClient multipart 转发按惯例归集成测试，Phase B 以手动验收覆盖。
2. **`@RequireRole("admin")` 生效**：依赖现有鉴权切面，单测不覆盖，手动验收（非 admin 调用返 403）。
3. **Gateway 路由**：Nacos 配置，需重启 gateway 生效，手动验收。
4. **Python 返回 data 为复杂嵌套（config/parameters Map）**：Java VO 用 `Map<String,Object>` 透传，不严格类型化，靠手动验收确认前端可用。
