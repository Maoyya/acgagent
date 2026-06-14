# 2026-06-15 Phase B：知识库 / 文档 / 工具代理接口

## 变更原因
Java acg-chat 新增知识库、文档、工具的管理接口，全部代理到 Python acgagent-ai，前端统一走 Gateway。
spec：docs/superpowers/specs/2026-06-14-java-python-ai-integration-design.md §7；计划：docs/superpowers/plans/2026-06-15-java-python-ai-phase-b.md。

## 影响范围
- **API 新增**（均需认证 + admin）：
  - `GET/POST/PUT/DELETE /api/knowledge-bases[/{id}]`
  - `POST(multipart)/GET/DELETE /api/knowledge-bases/{kbId}/documents[/{docId}]`
  - `GET/POST/DELETE /api/tools[/{id}]`
- **架构**：纯代理，无 MySQL；Python 是 KB/Doc/Tool 存储真相源。
- **Gateway**：Nacos `acg-gateway.yaml` 的 `acg-chat` 路由 Path 追加 `/api/knowledge-bases/**,/api/tools/**`（已通过 Nacos Open API 更新）。
- **PythonAiClient**：新增泛型 `extractData`/`extractDataList` + KB/Doc/Tool 的 13 个调用方法（含 multipart 文档上传）。

## 变更前后对比
- 新增 acg-chat `kb/`、`tool/` 两个包（Controller + Service 接口 + Impl + 单测）。
- acg-common 新增 KnowledgeBaseVO/Request、DocumentVO、ToolVO/ToolCreateRequest（camelCase + `@JsonAlias` 对齐 Python snake_case）。

## 注意
- 文档上传为 multipart 转发，Python 异步处理，返回 `status=processing`，前端需轮询状态。
- 内置工具（calculator/web_search/knowledge_search）不可删，Python 返回 400，Java 透传 `BizException`。
- 路由需重启 acg-gateway（或触发 /actuator/refresh）后对前端生效；Nacos 配置已就位。
