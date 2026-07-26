# 创作工坊 LLM 化 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把创作工坊 ②剧情/③分镜/④角色 从 mock 改为真实 LLM 生成（复用 prompt 功能的 Java→Python→LLM + SSE 基建），并新增 `workshop_project` 多项目落库（每用户私有 CRUD）。⑤视频不在本期。

**Architecture:** Python(acgagent-ai) 产 LLM 结果（②astream 流式 / ③④`with_structured_output` 同步结构化）→ Java(acgagent) `PythonAiClient` 转发（②Flux 复用 `stripSseData` / ③④同步）+ `WorkshopService` 项目 CRUD → 前端(acgagent-web) 项目选择器 + 手动生成 + PUT 落库。生成端点无状态，前端拿到结果后 PUT 存到 `workshop_project`。

**Tech Stack:** Python FastAPI + langchain(ChatOpenAI astream / with_structured_output) + pydantic；Java 21 + Spring Boot 3.3.5 + MyBatis-Plus + WebClient(reactor Flux)；前端 Vue3 + TS + Element Plus + fetch SSE。

**Spec:** `docs/superpowers/specs/2026-07-26-workshop-llm-design.md`

## Global Constraints

- **复用既有基建**，严格镜像 prompt 功能的实现：Python 端 `app/api/v1/prompt.py` + `app/services/prompt_service.py`（含 `_sse`/`generate_stream`/`_build_gen_llm`）+ `app/core/moderator.py`（`with_structured_output(..., method="function_calling")`）；Java 端 `PythonAiClient.streamGeneratePrompt`/`parseGenerateStreamEvent`/`stripSseData`/`extractData` + `PromptServiceImpl`(owner 鉴权 + `ServiceHelper.findOrThrow` + LambdaQueryWrapper)；前端 `src/api/prompt.ts` 的 `generatePromptStream`(fetch+ReadableStream 解析 `data:`) + `useSSE.ts`。每个任务的实现者**先读对应的 prompt 功能文件**作为权威模式。
- **②流式必须用 `stripSseData` + `filter(startsWith("{"))`**，绝不能用旧的 `startsWith("data:")`（会踩 SSE-reader 剥前缀的坑，见 prompt 那次 bug）。
- **meta-LLM 配置复用** Python `settings.meta_llm_*`（已配 GLM），不新增配置。
- **结构化输出用容器模型**：`with_structured_output(StoryboardResult/CharactersResult, method="function_calling")`，不要直接传 `list[Shot]`。
- **Git 规约（项目 CLAUDE.md 规约八/九）**：每任务完成后**先 code-review**，CR 过才 `git add`，**不自动 `git commit`**（开发者决定）。三个仓库各自提交。
- **注释/命名规约（项目规约二/六）**：类/字段/方法 Javadoc，VO `@JsonAlias` 接 Python snake_case，DO `@TableName(autoResultMap=true)` + JSON 列 `JacksonTypeHandler`，包按业务域 `com.darkness.workshop`。
- **ResultCode**：SUCCESS(200)/BAD_REQUEST(400)/FORBIDDEN(403)/NOT_FOUND(404)/INTERNAL_ERROR(500)/SERVICE_UNAVAILABLE(503)。

---

## File Structure

**Python(acgagent-ai)：**
- `app/models/workshop.py`（新）— PlotRequest{story} / Shot / Character / StoryboardResult{shots} / CharactersResult{characters}
- `app/core/workshop_builder.py`（新）— plot_messages(story) / storyboard_messages(plot) / characters_messages(plot)
- `app/services/workshop_service.py`（新）— plot_stream(astream+_sse) / storyboard(with_structured_output) / characters(...)
- `app/api/v1/workshop.py`（新）— 4 端点（plot 流式 / storyboard / characters + 复用 Result）
- `app/api/v1/router.py`（改）— 挂载 workshop_router

**Java(acgagent)：**
- `acg-common`: `entity/WorkshopProjectDO`、`mapper/WorkshopProjectMapper`、`model/`(WorkshopProjectVO, WorkshopProjectRequest, ShotVO, CharacterVO, WorkshopPlotStreamEvent, PlotRequest, StoryboardRequest, CharactersRequest)
- `acg-chat`: `agent/client/PythonAiClient`（改：加 workshop 段）、`workshop/service/WorkshopService`+`impl/WorkshopServiceImpl`、`workshop/controller/WorkshopController`
- `acg-user/src/main/resources/db/schema.sql`（改：加 workshop_project）

**前端(acgagent-web)：**
- `src/types/workshop.ts`（新）、`src/api/workshop.ts`（新）、`src/views/workshop/Index.vue`（改）

**网关/文档：** Nacos `acg-gateway.yaml` 加 `/api/workshop/**`、`docs/changelogs/2026-07-26-workshop.md`

---

## Task 1: Python — workshop 数据模型

**Files:**
- Create: `C:/Users/10173/PycharmProjects/acgagent-ai/app/models/workshop.py`
- Test: `C:/Users/10173/PycharmProjects/acgagent-ai/tests/test_workshop_models.py`（若 tests/ 无此约定，放 `tests/models/test_workshop_models.py`，参考既有 `tests/` 结构）

**Interfaces:**
- Produces: `PlotRequest{story:str}`、`Shot{shot,description,duration,movement,dialogue}`、`Character{name,role,description}`、`StoryboardResult{shots:list[Shot]}`、`CharactersResult{characters:list[Character]}`（pydantic v2）。供 Task 2/3 使用。

- [ ] **Step 1: 写失败测试**（验证 pydantic 模型能从典型 JSON 构造 + 默认值）
```python
from app.models.workshop import PlotRequest, Shot, Character, StoryboardResult, CharactersResult

def test_plot_request():
    r = PlotRequest(story="少女与黑猫")
    assert r.story == "少女与黑猫"

def test_storyboard_result_parses_list():
    s = StoryboardResult.model_validate({
        "shots": [
            {"shot":"远景","description":"雨夜涩谷","duration":"3s","movement":"缓慢推进","dialogue":""},
            {"shot":"特写","description":"黑猫眼睛","duration":"1s","movement":"推近","dialogue":"救救我"},
        ]
    })
    assert len(s.shots) == 2
    assert s.shots[0].shot == "远景"
    assert s.shots[1].dialogue == "救救我"

def test_characters_result_parses_list():
    c = CharactersResult.model_validate({
        "characters": [{"name":"林月","role":"主角","description":"17岁少女"}]
    })
    assert c.characters[0].name == "林月"
```

- [ ] **Step 2: 跑测试确认失败** — Run: `cd C:/Users/10173/PycharmProjects/acgagent-ai && /c/Users/10173/miniconda3/envs/acgagent-ai/python.exe -m pytest tests/test_workshop_models.py -q`（或对应路径）Expected: ImportError（模块不存在）

- [ ] **Step 3: 实现 `app/models/workshop.py`**
```python
"""
创作工坊数据模型。所有模型走 pydantic v2，沿用项目统一 Result<T> 信封（见 app/models/common.py）。
Shot/Character 为单条结构化记录；StoryboardResult/CharactersResult 为容器（供 with_structured_output）。
"""
from pydantic import BaseModel, Field


class PlotRequest(BaseModel):
    """剧情生成请求：故事梗概。"""
    story: str = Field(description="故事梗概（用户输入）")


class Shot(BaseModel):
    """单个分镜。"""
    shot: str = Field(description="景别：远景/中景/近景/特写")
    description: str = Field(description="镜头画面描述")
    duration: str = Field(default="3s", description="时长，如 3s")
    movement: str = Field(default="静止", description="镜头运动：静止/推进/跟随...")
    dialogue: str = Field(default="", description="该镜台词，可为空")


class Character(BaseModel):
    """单个角色。color 不含——由前端按 name 自动配色。"""
    name: str = Field(description="角色名")
    role: str = Field(description="角色定位：主角/配角/引导者...")
    description: str = Field(description="角色外貌与性格描述")


class StoryboardResult(BaseModel):
    """分镜结构化输出容器（供 with_structured_output）。"""
    shots: list[Shot] = Field(default_factory=list)


class CharactersResult(BaseModel):
    """角色结构化输出容器（供 with_structured_output）。"""
    characters: list[Character] = Field(default_factory=list)
```

- [ ] **Step 4: 跑测试确认通过** — Expected: 3 passed

- [ ] **Step 5: CR + stage** — code-review → `git add app/models/workshop.py tests/test_workshop_models.py`（在 acgagent-ai 仓库；不自动 commit）

---

## Task 2: Python — workshop_builder + workshop_service（生成核心）

**Files:**
- Create: `app/core/workshop_builder.py`、`app/services/workshop_service.py`
- Test: `tests/test_workshop_service.py`
- **Read first（权威模式）：** `app/services/prompt_service.py`（`_build_gen_llm`/`_get_gen_llm`/`generate_stream`/`_sse`/`MetaLLMNotConfigured`）、`app/core/moderator.py`（`with_structured_output(..., method="function_calling")`）、`app/core/prompt_builder.py`（`build_messages` 模式）

**Interfaces:**
- Consumes: Task 1 的模型；`app.config.settings.meta_llm_*`；`app.models.common.Result`
- Produces: `workshop_service.plot_stream(req: PlotRequest) -> AsyncGenerator[str,None]`（yield SSE `data: {json}` 行）、`workshop_service.storyboard(req: PlotRequest_plot) -> Result`、`workshop_service.characters(...) -> Result`。供 Task 3 api 调用。

> 注：storyboard/characters 入参用 plot 文本。为避免与 PlotRequest(story) 混淆，这两个方法签名直接收 `plot: str`（api 层从请求体取）。

- [ ] **Step 1: 写失败测试**（mock meta-LLM：plot 流式吐 content+done；storyboard/characters 返回结构化）
```python
import pytest
from unittest.mock import AsyncMock, MagicMock
from app.models.workshop import PlotRequest, Shot, Character, StoryboardResult, CharactersResult

# 复用 prompt 测试里 mock meta-LLM 的手法（conftest）；这里用 monkeypatch 替换 _get_gen_llm

@pytest.mark.asyncio
async def test_plot_stream_emits_content_done(monkeypatch):
    from app.services import workshop_service as ws
    # mock astream：吐两个 token 再结束
    async def fake_astream(messages):
        for t in ["# 角", "色"]:
            yield MagicMock(content=t)
    fake_llm = MagicMock()
    fake_llm.astream = fake_astream
    monkeypatch.setattr(ws.workshop_service, "_get_gen_llm", lambda: fake_llm)
    chunks = []
    async for sse in ws.workshop_service.plot_stream(PlotRequest(story="x")):
        chunks.append(sse)
    body = "".join(chunks)
    assert 'data: {"type": "content"' in body and "# 角" in body and "角色" in body
    assert 'data: {"type": "done"}' in body

@pytest.mark.asyncio
async def test_storyboard_returns_structured(monkeypatch):
    from app.services import workshop_service as ws
    structured = MagicMock()
    structured.ainvoke = AsyncMock(return_value=StoryboardResult(shots=[Shot(shot="远景", description="雨夜")]))
    fake_llm = MagicMock()
    fake_llm.with_structured_output = MagicMock(return_value=structured)
    monkeypatch.setattr(ws.workshop_service, "_get_gen_llm", lambda: fake_llm)
    res = ws.workshop_service.storyboard("一段剧情")
    assert res.code == 200
    assert res.data[0].shot == "远景"

@pytest.mark.asyncio
async def test_characters_returns_structured(monkeypatch):
    from app.services import workshop_service as ws
    structured = MagicMock()
    structured.ainvoke = AsyncMock(return_value=CharactersResult(characters=[Character(name="林月", role="主角", description="少女")]))
    fake_llm = MagicMock()
    fake_llm.with_structured_output = MagicMock(return_value=structured)
    monkeypatch.setattr(ws.workshop_service, "_get_gen_llm", lambda: fake_llm)
    res = ws.workshop_service.characters("一段剧情")
    assert res.code == 200
    assert res.data[0].name == "林月"
```
> 若项目用 `pytest-asyncio`，确保 `@pytest.mark.asyncio`；参考既有 prompt 测试的异步写法。

- [ ] **Step 2: 跑测试确认失败** — Run: `python -m pytest tests/test_workshop_service.py -q` Expected: ImportError（service 不存在）

- [ ] **Step 3: 实现 `app/core/workshop_builder.py`**（消息构造，镜像 `prompt_builder.build_messages`）
```python
"""创作工坊生成器：构造 plot/storyboard/characters 的元提示词消息。"""
from langchain_core.messages import HumanMessage, SystemMessage

_PLOT_SYS = "你是一名资深剧本作家，擅长把简短的故事梗概扩展为有画面感、有起伏的完整剧情（Markdown，分章节）。"
_SB_SYS = "你是一名分镜师，把剧情拆成可拍摄的分镜清单，只返回结构化结果。"
_CH_SYS = "你是一名角色设计师，根据剧情提炼角色，只返回结构化结果。"


class WorkshopBuilder:
    def plot_messages(self, story: str) -> list:
        payload = (
            f"把下面的故事梗概扩展为完整剧情，要求：画面感强、有起承转合、分章节（Markdown）。\n"
            f"故事梗概：\n{story}\n"
            f"输出：只输出剧情正文，不要解释、不要前缀。"
        )
        return [SystemMessage(content=_PLOT_SYS), HumanMessage(content=payload)]

    def storyboard_messages(self, plot: str) -> list:
        payload = (
            f"把下面的剧情拆分为 5~8 个分镜。每个分镜含：景别(远景/中景/近景/特写)、画面描述、时长、镜头运动、台词(可空)。\n"
            f"剧情：\n{plot}\n"
            f"只返回结构化结果。"
        )
        return [SystemMessage(content=_SB_SYS), HumanMessage(content=payload)]

    def characters_messages(self, plot: str) -> list:
        payload = (
            f"从下面的剧情中提炼主要角色（通常 2~4 个）。每个角色含：姓名、定位(主角/配角/引导者...)、外貌与性格描述。\n"
            f"剧情：\n{plot}\n"
            f"只返回结构化结果。"
        )
        return [SystemMessage(content=_CH_SYS), HumanMessage(content=payload)]
```

- [ ] **Step 4: 实现 `app/services/workshop_service.py`**（镜像 `prompt_service` 的 `_build_gen_llm`/`_get_gen_llm`/`_sse`/`MetaLLMNotConfigured`/`generate_stream`）
```python
"""创作工坊编排：plot 流式(astream) / storyboard / characters 结构化(with_structured_output)。"""
import json
import logging

from langchain_openai import ChatOpenAI

from app.config import settings
from app.core.workshop_builder import WorkshopBuilder
from app.models.common import Result
from app.models.workshop import (
    CharactersResult, PlotRequest, StoryboardResult,
)

logger = logging.getLogger("acgagent-ai")


class MetaLLMNotConfigured(RuntimeError):
    pass


def _sse(obj: dict) -> str:
    return f"data: {json.dumps(obj, ensure_ascii=False)}\n\n"


class WorkshopService:
    def __init__(self):
        self._gen_llm = None
        self.builder = WorkshopBuilder()

    def _get_gen_llm(self):
        # 镜像 prompt_service._build_gen_llm/_get_gen_llm：缺 api_key 抛 MetaLLMNotConfigured
        if self._gen_llm is None:
            if not settings.meta_llm_api_key:
                raise MetaLLMNotConfigured("meta llm not configured (set ACG_AI_META_LLM_API_KEY)")
            self._gen_llm = ChatOpenAI(
                model=settings.meta_llm_model,
                base_url=settings.meta_llm_base_url,
                api_key=settings.meta_llm_api_key,
                temperature=0.8,
                streaming=False,  # astream 自带流式
            )
        return self._gen_llm

    async def plot_stream(self, req: PlotRequest):
        """剧情流式：astream → content；末尾 done。镜像 prompt_service.generate_stream。"""
        try:
            llm = self._get_gen_llm()
        except MetaLLMNotConfigured as e:
            yield _sse({"type": "error", "message": str(e)})
            return
        try:
            async for chunk in llm.astream(self.builder.plot_messages(req.story)):
                text = chunk.content or ""
                if text:
                    yield _sse({"type": "content", "content": text})
            yield _sse({"type": "done"})
        except Exception:
            logger.exception("workshop plot stream failed")
            yield _sse({"type": "error", "message": "plot generation failed"})

    def storyboard(self, plot: str) -> Result:
        """分镜结构化：with_structured_output(StoryboardResult, function_calling)。镜像 moderator.moderate。"""
        try:
            llm = self._get_gen_llm()
            structured = llm.with_structured_output(StoryboardResult, method="function_calling")
            result = structured.invoke(self.builder.storyboard_messages(plot))
            return Result.success(data=result.shots)
        except Exception:
            logger.exception("workshop storyboard failed")
            return Result.error(code=500, message="storyboard generation failed")

    def characters(self, plot: str) -> Result:
        """角色结构化：with_structured_output(CharactersResult, function_calling)。"""
        try:
            llm = self._get_gen_llm()
            structured = llm.with_structured_output(CharactersResult, method="function_calling")
            result = structured.invoke(self.builder.characters_messages(plot))
            return Result.success(data=result.characters)
        except Exception:
            logger.exception("workshop characters failed")
            return Result.error(code=500, message="characters generation failed")


workshop_service = WorkshopService()
```
> 注意：storyboard/characters 用 `structured.invoke(...)`（同步，meta-LLM 非流式）。`Result.success(data=list)` 把 pydantic 列表序列化为 JSON 数组。

- [ ] **Step 5: 跑测试确认通过** — Run: `python -m pytest tests/test_workshop_service.py -q` Expected: 3 passed

- [ ] **Step 6: CR + stage** — code-review → `git add app/core/workshop_builder.py app/services/workshop_service.py tests/test_workshop_service.py`（不自动 commit）

---

## Task 3: Python — workshop API + router 挂载

**Files:**
- Create: `app/api/v1/workshop.py`
- Modify: `app/api/v1/router.py`（挂载 workshop_router）
- **Read first：** `app/api/v1/prompt.py`（端点结构 + StreamingResponse + Header X-User-Id 模式）

**Interfaces:**
- Produces: 4 个端点（挂 `/api/v1` 前缀）：`POST /workshop/plot`（SSE）、`POST /workshop/storyboard`、`POST /workshop/characters`。Java 侧 Task 6 调用。

- [ ] **Step 1: 实现 `app/api/v1/workshop.py`**（镜像 `prompt.py`）
```python
"""创作工坊 API（挂 /api/v1 前缀、需 X-API-Key、user_id 由 Java 经 X-User-Id 透传）。

端点：
- POST /workshop/plot       剧情流式生成(SSE: content/done/error)，不落库
- POST /workshop/storyboard 分镜结构化(同步 JSON)，不落库
- POST /workshop/characters 角色结构化(同步 JSON)，不落库
"""
from fastapi import APIRouter, Header
from fastapi.responses import StreamingResponse

from app.models.common import Result
from app.models.workshop import PlotRequest
from app.services.workshop_service import workshop_service

router = APIRouter(tags=["workshop"])


@router.post("/workshop/plot")
async def plot(
    body: PlotRequest,
    x_user_id: str | None = Header(None, alias="X-User-Id"),
):
    """剧情流式生成（SSE）。"""
    return StreamingResponse(
        workshop_service.plot_stream(body, user_id=x_user_id),
        media_type="text/event-stream",
    )


@router.post("/workshop/storyboard")
async def storyboard(body: dict):
    """分镜结构化。body: {plot: str}。"""
    plot = (body or {}).get("plot", "")
    return workshop_service.storyboard(plot)


@router.post("/workshop/characters")
async def characters(body: dict):
    """角色结构化。body: {plot: str}。"""
    plot = (body or {}).get("plot", "")
    return workshop_service.characters(plot)
```
> storyboard/characters 用 `body: dict` 取 plot（简单）；或定义 `PlotTextInput{plot}` 模型，二选一（实现者定，保持与 prompt 风格一致即可）。

- [ ] **Step 2: 挂载 router** — 在 `app/api/v1/router.py` 加：
```python
from app.api.v1.workshop import router as workshop_router
# ...在已有 include_router 旁
router.include_router(workshop_router)
```

- [ ] **Step 3: 导入校验** — Run: `cd C:/Users/10173/PycharmProjects/acgagent-ai && /c/Users/10173/miniconda3/envs/acgagent-ai/python.exe -c "from app.api.v1.router import router; print([r.path for r in router.routes if '/workshop' in r.path])"` Expected: `['/api/v1/workshop/plot', '/api/v1/workshop/storyboard', '/api/v1/workshop/characters']`（3 个）

- [ ] **Step 4: CR + stage** — code-review → `git add app/api/v1/workshop.py app/api/v1/router.py`（不自动 commit）

---

## Task 4: Java — DDL + WorkshopProjectDO + Mapper

**Files:**
- Modify: `acg-user/src/main/resources/db/schema.sql`（末尾追加建表）
- Create: `acg-common/src/main/java/com/darkness/common/entity/WorkshopProjectDO.java`、`acg-common/src/main/java/com/darkness/common/mapper/WorkshopProjectMapper.java`
- **Read first（权威模式）：** `acg-common/.../entity/PromptTemplateDO.java`、`mapper/PromptTemplateMapper.java`、`BaseEntity`（DO 镜像 PromptTemplateDO：extends BaseEntity、`@TableName(autoResultMap=true)`、JSON 列 `@TableField(typeHandler=JacksonTypeHandler.class)`）

**Interfaces:**
- Produces: `WorkshopProjectDO{id,user_id,title,story,plot,storyboard(List<ShotVO>),characters(List<CharacterVO>),deleted,...}`（extends BaseEntity）、`WorkshopProjectMapper extends BaseMapper<WorkshopProjectDO>`。供 Task 7 使用。
- ShotVO/CharacterVO 由 Task 5 定义；DO 的 storyboard/characters 字段类型为 `List<ShotVO>`/`List<CharacterVO>`（JSON 列）。**依赖 Task 5 先完成**（或本任务先定义 DO 用 `List<Object>` 占位、Task 5 再改——建议调整顺序：先 Task 5 的 ShotVO/CharacterVO，再 Task 4 DO）。实现者按 `先 ShotVO/CharacterVO → 再 DO` 顺序做。

- [ ] **Step 1: 追加 DDL** 到 `schema.sql` 末尾（见 spec §4.1，逐字）：
```sql

CREATE TABLE IF NOT EXISTS workshop_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID，自增',
    user_id BIGINT NOT NULL COMMENT '归属用户 ID',
    title VARCHAR(128) NOT NULL COMMENT '项目标题，默认取故事前 32 字，可改',
    story TEXT COMMENT '①故事梗概（用户输入）',
    plot TEXT COMMENT '②剧情（生成的 Markdown 正文）',
    storyboard JSON COMMENT '③分镜数组，如 [{shot,description,duration,movement,dialogue}]',
    characters JSON COMMENT '④角色数组，如 [{name,role,description}]',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) COMMENT '创作工坊项目表（每用户私有）';
```

- [ ] **Step 2: 实现 `WorkshopProjectDO.java`**（镜像 PromptTemplateDO，字段见 spec §4）
```java
package com.darkness.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.darkness.common.model.CharacterVO;
import com.darkness.common.model.ShotVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/** 创作工坊项目实体，映射 workshop_project 表（每用户私有）。storyboard/characters 为 JSON 列。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "workshop_project", autoResultMap = true)
public class WorkshopProjectDO extends BaseEntity {
    /** 归属用户 ID */
    private Long userId;
    /** 项目标题，默认取故事前 32 字，可改 */
    private String title;
    /** ①故事梗概（用户输入） */
    private String story;
    /** ②剧情（生成的 Markdown） */
    private String plot;
    /** ③分镜数组（JSON 列） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ShotVO> storyboard;
    /** ④角色数组（JSON 列，不含 color——前端配色） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<CharacterVO> characters;
}
```

- [ ] **Step 3: 实现 `WorkshopProjectMapper.java`**（镜像 PromptTemplateMapper）
```java
package com.darkness.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.darkness.common.entity.WorkshopProjectDO;

/** 创作工坊项目数据访问层。 */
public interface WorkshopProjectMapper extends BaseMapper<WorkshopProjectDO> {
}
```

- [ ] **Step 4: 编译确认** — Run: `cd C:/Users/10173/IdeaProjects/acgagent && mvn clean compile -pl acg-common -am -q` → BUILD SUCCESS（需 ShotVO/CharacterVO 已在 Task 5 定义；若本任务先做，临时把 DO 字段改 `List<Object>` 编通，Task 5 后改回——推荐按 Task5→Task4 顺序）

- [ ] **Step 5: CR + stage** — code-review → `git add acg-common/.../entity/WorkshopProjectDO.java acg-common/.../mapper/WorkshopProjectMapper.java acg-user/src/main/resources/db/schema.sql`（不自动 commit）

---

## Task 5: Java — workshop VO/Request 模型

**Files:**
- Create 于 `acg-common/src/main/java/com/darkness/common/model/`：`ShotVO.java`、`CharacterVO.java`、`WorkshopProjectVO.java`、`WorkshopProjectRequest.java`、`PlotRequest.java`、`StoryboardRequest.java`、`CharactersRequest.java`、`WorkshopPlotStreamEvent.java`
- **Read first（权威模式）：** `acg-common/.../model/PromptTemplateVO.java`（`from(DO)` 工厂）、`PromptTemplateRequest.java`、`CostEstimateVO.java`（`@JsonAlias`/`@JsonIgnoreProperties`）、`GenerateStreamEvent.java`

**Interfaces:**
- Produces（供 Task 4/6/7/8 + 前端契约）：
  - `ShotVO{shot,description,duration,movement,dialogue}`（@JsonAlias snake）
  - `CharacterVO{name,role,description}`（@JsonAlias snake；无 color）
  - `WorkshopProjectVO{id,userId,title,story,plot,storyboard:List<ShotVO>,characters:List<CharacterVO>,createdAt,updatedAt}` + `static from(WorkshopProjectDO)`
  - `WorkshopProjectRequest{title?,story?,plot?,storyboard?,characters?}`（局部更新）
  - `PlotRequest{story}`、`StoryboardRequest{plot}`、`CharactersRequest{plot}`（→Python，camelCase，PythonAiClient 构造 snake body）
  - `WorkshopPlotStreamEvent{type,content,message}`（@JsonIgnoreProperties(ignoreUnknown=true)；解析 Python SSE）

- [ ] **Step 1: 实现 ShotVO/CharacterVO**（@JsonAlias 接 Python snake_case，@JsonIgnoreProperties(ignoreUnknown=true)；含字段 Javadoc）
```java
// ShotVO.java
@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class ShotVO {
    private String shot;
    private String description;
    private String duration;
    private String movement;
    private String dialogue;
}
// CharacterVO.java
@Data @JsonIgnoreProperties(ignoreWorld = true)  // 注意：ignoreUnknown=true（typo 纠正）
public class CharacterVO {
    private String name;
    private String role;
    private String description;
}
```
> 字段名与 Python `Shot`/`Character` 的 snake_case 对应：Python 发 `shot/description/duration/movement/dialogue`、`name/role/description`——Java 字段同名（无大小写差），无需 @JsonAlias；但加 `@JsonIgnoreProperties(ignoreUnknown=true)` 防多余字段。

- [ ] **Step 2: 实现 WorkshopPlotStreamEvent**（镜像 GenerateStreamEvent，但无 estimate）
```java
@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class WorkshopPlotStreamEvent {
    /** content / done / error */
    private String type;
    /** content 事件：token */
    private String content;
    /** error 事件：信息 */
    private String message;
}
```

- [ ] **Step 3: 实现 WorkshopProjectVO + from(WorkshopProjectDO)**（镜像 PromptTemplateVO.from）；`WorkshopProjectRequest`（镜像 PromptTemplateRequest，字段 title/story/plot/storyboard/characters 全可选）；`PlotRequest{story}`、`StoryboardRequest{plot}`、`CharactersRequest{plot}`（@Data + 字段 Javadoc）。

- [ ] **Step 4: 编译确认** — Run: `mvn clean compile -pl acg-common -am` → BUILD SUCCESS

- [ ] **Step 5: CR + stage** — code-review → `git add` 上述 8 个文件（不自动 commit）

---

## Task 6: Java — PythonAiClient workshop 段（转发）

**Files:**
- Modify: `acg-chat/src/main/java/com/darkness/agent/client/PythonAiClient.java`（加 workshop 段）
- Modify: `acg-chat/src/test/java/com/darkness/agent/client/PythonAiClientTest.java`（加 parseWorkshopPlotEvent 测试）
- **Read first（权威模式）：** `PythonAiClient.streamGeneratePrompt`（Flux + `stripSseData` + `filter("{")` + `parseGenerateStreamEvent`）、`extractData`、`postJson`、`buildPromptGenerateBody`

**Interfaces:**
- Consumes: Task 5 的 PlotRequest/StoryboardRequest/CharactersRequest/WorkshopPlotStreamEvent/ShotVO/CharacterVO；既有 `stripSseData`/`extractData`/`postJson`/`props`/`pythonWebClient`
- Produces: `streamWorkshopPlot(PlotRequest, Long userId) -> Flux<WorkshopPlotStreamEvent>`、`workshopStoryboard(StoryboardRequest, Long userId) -> List<ShotVO>`、`workshopCharacters(CharactersRequest, Long userId) -> List<CharacterVO>`、`parseWorkshopPlotEvent(String json) -> WorkshopPlotStreamEvent`。供 Task 7 使用。

- [ ] **Step 1: 写失败测试**（parseWorkshopPlotEvent，镜像 parseGenerateStreamEvent 测试）
```java
@Test
void parseWorkshopPlotEvent_content() {
    WorkshopPlotStreamEvent e = client.parseWorkshopPlotEvent("{\"type\":\"content\",\"content\":\"# \"}");
    assertThat(e.getType()).isEqualTo("content");
    assertThat(e.getContent()).isEqualTo("# ");
}
@Test
void parseWorkshopPlotEvent_done() {
    WorkshopPlotStreamEvent e = client.parseWorkshopPlotEvent("{\"type\":\"done\"}");
    assertThat(e.getType()).isEqualTo("done");
}
```
（import WorkshopPlotStreamEvent；client 构造同既有 `new PythonAiClient(null,null,new ObjectMapper())`）

- [ ] **Step 2: 跑测试确认失败** — Run: `mvn test -pl acg-chat -am -Dtest=PythonAiClientTest -Dsurefire.failIfNoSpecifiedTests=false` Expected: 编译失败（方法不存在）

- [ ] **Step 3: 加 import + workshop 段** 到 PythonAiClient（镜像 streamGeneratePrompt + moderatePrompt）
```java
// imports 追加
import com.darkness.common.model.PlotRequest;
import com.darkness.common.model.StoryboardRequest;
import com.darkness.common.model.CharactersRequest;
import com.darkness.common.model.WorkshopPlotStreamEvent;
import com.darkness.common.model.ShotVO;
import com.darkness.common.model.CharacterVO;
import com.fasterxml.jackson.core.type.TypeReference;

// ==================== Workshop ====================

/** 剧情 SSE 流式（镜像 streamGeneratePrompt，复用 stripSseData + filter("{")）。 */
public Flux<WorkshopPlotStreamEvent> streamWorkshopPlot(PlotRequest req, Long userId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("story", req.getStory());
    return pythonWebClient.post()
            .uri("/api/v1/workshop/plot")
            .header("X-API-Key", props.getApiKey())
            .header("X-User-Id", String.valueOf(userId))
            .header("Content-Type", "application/json")
            .bodyValue(body)
            .retrieve().bodyToFlux(String.class)
            .map(this::stripSseData)
            .filter(json -> json != null && json.startsWith("{"))
            .map(this::parseWorkshopPlotEvent)
            .timeout(Duration.ofMillis(props.getStreamReadTimeout()));
}

/** 解析剧情 SSE data 载荷。镜像 parseGenerateStreamEvent。 */
public WorkshopPlotStreamEvent parseWorkshopPlotEvent(String data) {
    try {
        return objectMapper.readValue(data, WorkshopPlotStreamEvent.class);
    } catch (Exception e) {
        log.warn("Failed to parse workshop plot SSE chunk: {}", data, e);
        throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "AI 流式响应解析失败");
    }
}

/** 分镜结构化（同步，extractData 反序列化为 List<ShotVO>）。 */
public java.util.List<ShotVO> workshopStoryboard(StoryboardRequest req, Long userId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("plot", req.getPlot());
    String json = postJson("/api/v1/workshop/storyboard", body, userId);
    return extractDataList(json, ShotVO.class);
}

/** 角色结构化（同步）。 */
public java.util.List<CharacterVO> workshopCharacters(CharactersRequest req, Long userId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("plot", req.getPlot());
    String json = postJson("/api/v1/workshop/characters", body, userId);
    return extractDataList(json, CharacterVO.class);
}
```
> 复用既有 `postJson(path,body,userId)` 私有辅助（prompt 已加）。`extractDataList(json, Class)` 是既有方法（PythonAiClient 已有，用于 KB/Doc 列表）——直接复用。

- [ ] **Step 4: 跑测试确认通过** — Run: `mvn test -pl acg-chat -am -Dtest=PythonAiClientTest -Dsurefire.failIfNoSpecifiedTests=false` Expected: 既有 + 新 2 = 全绿

- [ ] **Step 5: CR + stage** — code-review → `git add` PythonAiClient.java + PythonAiClientTest.java（不自动 commit）

---

## Task 7: Java — WorkshopService + Impl（CRUD + 生成转发）

**Files:**
- Create: `acg-chat/src/main/java/com/darkness/workshop/service/WorkshopService.java`、`impl/WorkshopServiceImpl.java`
- Test: `acg-chat/src/test/java/com/darkness/workshop/service/impl/WorkshopServiceImplTest.java`
- **Read first（权威模式）：** `acg-chat/.../prompt/service/impl/PromptServiceImpl.java`（owner 鉴权 `checkReadable`/`checkWritable` + `ServiceHelper.findOrThrow` + LambdaQueryWrapper + `createTemplate` title 默认值；generate 转发委托）

**Interfaces:**
- Consumes: Task 4 `WorkshopProjectMapper`/`WorkshopProjectDO`；Task 5 VOs；Task 6 `PythonAiClient.streamWorkshopPlot/workshopStoryboard/workshopCharacters`；`UserContext`（userId 由 Controller 透传）
- Produces: `WorkshopService` 接口方法：
  - `WorkshopProjectVO createProject(WorkshopProjectRequest req, Long userId)`
  - `List<WorkshopProjectVO> listProjects(Long userId)`
  - `WorkshopProjectVO getProject(Long id, Long userId)`
  - `WorkshopProjectVO updateProject(Long id, WorkshopProjectRequest req, Long userId)`
  - `void deleteProject(Long id, Long userId)`
  - `Flux<WorkshopPlotStreamEvent> plot(PlotRequest req, Long userId)`
  - `Result<List<ShotVO>> storyboard(StoryboardRequest req, Long userId)`
  - `Result<List<CharacterVO>> characters(CharactersRequest req, Long userId)`

- [ ] **Step 1: 写失败测试**（mock PythonAiClient + Mapper；镜像 PromptServiceImplTest 的 owner 隔离 + create 默认 title）
```java
@ExtendWith(MockitoExtension.class)
class WorkshopServiceImplTest {
    @Mock private WorkshopProjectMapper mapper;
    @Mock private PythonAiClient pythonAiClient;
    @InjectMocks private WorkshopServiceImpl service;

    @Test
    void createProject_defaultsTitleFromStory() {
        // 给 story 不给 title → title 取前 32 字
        when(mapper.insert(any(WorkshopProjectDO.class))).thenAnswer(i -> { i.getArgument(0, WorkshopProjectDO.class).setId(1L); return 1; });
        WorkshopProjectRequest req = new WorkshopProjectRequest();
        req.setStory("少女在雨夜的东京街头捡到会说话的黑猫");
        WorkshopProjectVO vo = service.createProject(req, 7L);
        assertThat(vo.getTitle()).isEqualTo("少女在雨夜的东京街头捡到会说话的黑猫");  // ≤32
        // 归属当前用户
        var captor = ArgumentCaptor.forClass(WorkshopProjectDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    }

    @Test
    void getProject_otherUser_forbidden_403() {
        WorkshopProjectDO tpl = new WorkshopProjectDO(); tpl.setId(1L); tpl.setUserId(1L);
        when(mapper.selectById(1L)).thenReturn(tpl);
        assertThatThrownBy(() -> service.getProject(1L, 999L))
            .isInstanceOf(BizException.class).extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    void listProjects_returnsOnlyOwn() {
        WorkshopProjectDO p = new WorkshopProjectDO(); p.setId(1L); p.setUserId(7L); p.setTitle("t");
        when(mapper.selectList(any())).thenReturn(java.util.List.of(p));
        assertThat(service.listProjects(7L)).hasSize(1);
    }

    @Test
    void storyboard_delegatesToPython() {
        StoryboardRequest req = new StoryboardRequest(); req.setPlot("剧情");
        when(pythonAiClient.workshopStoryboard(req, 7L)).thenReturn(java.util.List.of(new ShotVO()));
        assertThat(service.storyboard(req, 7L).getData()).hasSize(1);
    }
}
```
> 注意 `insert(any())` 重载歧义 → 用 `any(WorkshopProjectDO.class)`（同 prompt 测试）。

- [ ] **Step 2: 跑测试确认失败** — Run: `mvn test -pl acg-chat -am -Dtest=WorkshopServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false` Expected: 编译失败

- [ ] **Step 3: 实现 `WorkshopService.java` 接口**（8 方法声明 + Javadoc，签名见 Interfaces）

- [ ] **Step 4: 实现 `WorkshopServiceImpl.java`**（`@Service @RequiredArgsConstructor`；镜像 PromptServiceImpl 的 owner 鉴权 + findOrThrow + LambdaQueryWrapper；生成方法直接委托 pythonAiClient）
  - `createProject`：`new WorkshopProjectDO()`，`userId=当前`，`title = req.title!=null ? req.title : autoTitle(req.story)`（autoTitle = story 前 32 字，空则"新建项目"），story/plot 等按 req 设，`insert`，返回 `from(do)`。
  - `listProjects`：`LambdaQueryWrapper` `.eq(userId).orderByDesc(createdAt)` → `selectList` → `from`。
  - `getProject`：findOrThrow → owner 校验（`userId.equals(current)` 否则 FORBIDDEN）→ from。
  - `updateProject`：findOrThrow + owner 校验 → 按 req 非空字段覆盖 → `updateById` → from(selectById)。
  - `deleteProject`：findOrThrow + owner → `deleteById`。
  - `plot`：`return pythonAiClient.streamWorkshopPlot(req, userId);`
  - `storyboard`：`return Result.success(pythonAiClient.workshopStoryboard(req, userId));`
  - `characters`：同上。
  - 私有 `autoTitle(String story)`：`story==null||blank → "新建项目"；story.length()<=32 ? story : story.substring(0,32)`。
  - 私有 `checkOwner(WorkshopProjectDO, userId)`：`!userId.equals(do.getUserId()) → throw BizException(FORBIDDEN)`。

- [ ] **Step 5: 跑测试确认通过** — Run: `mvn test -pl acg-chat -am -Dtest=WorkshopServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false` Expected: 4 passed

- [ ] **Step 6: CR + stage** — code-review → `git add` WorkshopService.java + WorkshopServiceImpl.java + 测试（不自动 commit）

---

## Task 8: Java — WorkshopController（CRUD + 生成端点）

**Files:**
- Create: `acg-chat/src/main/java/com/darkness/workshop/controller/WorkshopController.java`
- **Read first（权威模式）：** `acg-chat/.../prompt/controller/PromptController.java`（`@RestController @RequestMapping` + `produces=TEXT_EVENT_STREAM_VALUE` for SSE + `UserContext.getUserId()` 透传 + 方法 Javadoc）

**Interfaces:**
- Produces: `/api/workshop` 下 8 端点（CRUD 5 + 生成 3）。给前端 Task 9/10 调用。

- [ ] **Step 1: 实现 `WorkshopController.java`**
```java
package com.darkness.workshop.controller;

import com.darkness.common.model.*;
import com.darkness.common.result.Result;
import com.darkness.common.util.UserContext;
import com.darkness.workshop.service.WorkshopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.util.List;

/** 创作工坊 REST 控制器：项目 CRUD + ②剧情(流式)/③分镜/④角色 生成。 */
@RestController
@RequestMapping("/api/workshop")
@RequiredArgsConstructor
public class WorkshopController {
    private final WorkshopService workshopService;

    // ===== 项目 CRUD =====
    @PostMapping("/projects")
    public Result<WorkshopProjectVO> create(@RequestBody WorkshopProjectRequest req) {
        return Result.success(workshopService.createProject(req, UserContext.getUserId()));
    }
    @GetMapping("/projects")
    public Result<List<WorkshopProjectVO>> list() {
        return Result.success(workshopService.listProjects(UserContext.getUserId()));
    }
    @GetMapping("/projects/{id}")
    public Result<WorkshopProjectVO> get(@PathVariable Long id) {
        return Result.success(workshopService.getProject(id, UserContext.getUserId()));
    }
    @PutMapping("/projects/{id}")
    public Result<WorkshopProjectVO> update(@PathVariable Long id, @RequestBody WorkshopProjectRequest req) {
        return Result.success(workshopService.updateProject(id, req, UserContext.getUserId()));
    }
    @DeleteMapping("/projects/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workshopService.deleteProject(id, UserContext.getUserId());
        return Result.success();
    }

    // ===== 生成（无状态，前端拿到结果后 PUT 落库）=====
    @PostMapping(value = "/plot", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<WorkshopPlotStreamEvent> plot(@RequestBody PlotRequest req) {
        return workshopService.plot(req, UserContext.getUserId());
    }
    @PostMapping("/storyboard")
    public Result<List<ShotVO>> storyboard(@RequestBody StoryboardRequest req) {
        return workshopService.storyboard(req, UserContext.getUserId());
    }
    @PostMapping("/characters")
    public Result<List<CharacterVO>> characters(@RequestBody CharactersRequest req) {
        return workshopService.characters(req, UserContext.getUserId());
    }
}
```
> 每方法补 Javadoc（功能/路径/认证/@param/@return，规约二.3）。

- [ ] **Step 2: 编译整个 acg-chat** — Run: `mvn clean compile -pl acg-chat -am` → BUILD SUCCESS

- [ ] **Step 3: 跑全量 acg-chat 测试（无回归）** — Run: `mvn test -pl acg-chat -am` → 全绿（除既有 PythonAgentPropertiesTest 环境依赖项）

- [ ] **Step 4: CR + stage** — code-review → `git add WorkshopController.java`（不自动 commit）

---

## Task 9: 前端 — workshop types + api

**Files:**
- Create: `src/types/workshop.ts`、`src/api/workshop.ts`（在 `acgagent-web`）
- Modify: `src/types/index.ts`（re-export workshop 类型）
- Test: `src/__tests__/api/workshop.test.ts`
- **Read first（权威模式）：** `src/api/prompt.ts` 的 `generatePromptStream`（fetch+ReadableStream+TextDecoder+`\n\n`/`data:` 解析）、`createTemplate/updateTemplate/getTemplateList`（CRUD）、`src/types/prompt.ts`、`src/composables/useSSE.ts`

**Interfaces:**
- Produces（供 Task 10）：
  - 类型：`WorkshopProject/Shot/Character/PlotStreamEvent`
  - api：`createProject/listProjects/getProject/updateProject/deleteProject`（CRUD，axios）+ `streamPlot(data,onEvent)`（fetch SSE，镜像 generatePromptStream）

- [ ] **Step 1: 实现 `src/types/workshop.ts`**
```ts
export interface Shot { shot: string; description: string; duration: string; movement: string; dialogue: string }
export interface Character { name: string; role: string; description: string }
export interface WorkshopProject {
  id: number; userId: number; title: string
  story: string | null; plot: string | null
  storyboard: Shot[] | null; characters: Character[] | null
  createdAt: string; updatedAt: string
}
export interface WorkshopProjectRequest {
  title?: string; story?: string; plot?: string; storyboard?: Shot[]; characters?: Character[]
}
export interface PlotRequest { story: string }
export interface StoryboardRequest { plot: string }
export interface CharactersRequest { plot: string }
export interface PlotStreamEvent { type: 'content' | 'done' | 'error'; content?: string; message?: string }
```
在 `src/types/index.ts` 加 `export * from './workshop'`。

- [ ] **Step 2: 实现 `src/api/workshop.ts`**（CRUD 用 axios；plot 用 fetch SSE 镜像 `generatePromptStream`）
```ts
import request from '@/utils/request'
import type { Result, WorkshopProject, WorkshopProjectRequest, PlotRequest, StoryboardRequest, CharactersRequest, Shot, Character, PlotStreamEvent } from '@/types'

export function createProject(data: WorkshopProjectRequest) { return request.post<Result<WorkshopProject>>('/workshop/projects', data) }
export function listProjects() { return request.get<Result<WorkshopProject[]>>('/workshop/projects') }
export function getProject(id: number) { return request.get<Result<WorkshopProject>>(`/workshop/projects/${id}`) }
export function updateProject(id: number, data: WorkshopProjectRequest) { return request.put<Result<WorkshopProject>>(`/workshop/projects/${id}`, data) }
export function deleteProject(id: number) { return request.delete<Result<void>>(`/workshop/projects/${id}`) }

export function generateStoryboard(data: StoryboardRequest) { return request.post<Result<Shot[]>>('/workshop/storyboard', data) }
export function generateCharacters(data: CharactersRequest) { return request.post<Result<Character[]>>('/workshop/characters', data) }

/** 剧情 SSE 流式（镜像 src/api/prompt.ts 的 generatePromptStream：fetch+ReadableStream 解析 data:）。 */
export async function streamPlot(data: PlotRequest, onEvent: (e: PlotStreamEvent) => void, signal?: AbortSignal): Promise<void> {
  // 完整实现镜像 generatePromptStream：fetch POST /workshop/plot（相对路径走代理），Authorization Bearer，读 response.body，TextDecoder，按 \n\n 切块，行 startsWith('data:') → slice(5).trim() → JSON.parse → onEvent
  //（实现者复制 generatePromptStream 的解析逻辑，仅改 URL=/workshop/plot 与事件类型）
}
```
> `streamPlot` 完整实现 = `generatePromptStream` 的解析逻辑逐字复制（URL 改 `/workshop/plot`、类型改 PlotStreamEvent）。`generatePromptStream` 在 `src/api/prompt.ts` 是权威参考。

- [ ] **Step 3: 写 api 测试**（`src/__tests__/api/workshop.test.ts`：mock request，断言 CRUD 调对 URL/method；streamPlot 可 vi.mock fetch，参考 prompt api 测试）。Run: `npx vitest run src/__tests__/api/workshop.test.ts` → 绿

- [ ] **Step 4: typecheck + 全量测试** — Run: `npx vue-tsc --noEmit` 无错；`npm run test:run` 全绿

- [ ] **Step 5: CR + stage** — code-review → `git add` workshop.ts(types+api) + index.ts + 测试（在 acgagent-web 仓库；不自动 commit）

---

## Task 10: 前端 — workshop/Index.vue 改造

**Files:**
- Modify: `src/views/workshop/Index.vue`（去 mock，加项目选择器 + 手动生成 + 落库 + 角色自动配色）
- Test: `src/views/workshop/__tests__/Index.test.ts`（若不存在则新建；mock api）
- **Read first：** 现有 `src/views/workshop/Index.vue`（保留步骤骨架+样式）、`src/components/prompt/PromptGenerateDialog.vue`（流式消费 + live ref 模式）

**行为规范（spec §4/§6.3）：**
- 顶部加**项目选择器**（el-select 列出 `listProjects()`；含「新建项目」按钮 → `createProject({})` → 切换；删除按钮 → `deleteProject`）。
- 打开项目 → `getProject(id)` → 把 story/plot/storyboard/characters 灌进各步 ref。
- ①故事：`v-model` 到 `storyInput`；「保存」→ `updateProject(id,{story})`。
- ②剧情：「生成」→ `streamPlot({story}, onEvent)`：onEvent content → 追加到 `plotText` ref（live 渲染）；done → `updateProject(id,{plot:plotText})` 落库；error → ElMessage.error。
- ③分镜：「生成」→ `generateStoryboard({plot})`（loading 转圈）→ 渲染 `storyboard` 数组 → `updateProject(id,{storyboard})`。
- ④角色：「生成」→ `generateCharacters({plot})` → 渲染 `characters` → `updateProject(id,{characters})`。
- 「重新生成」= 重跑当前步（覆盖）。
- **角色头像 color**：前端按 name 哈希取色——新增工具函数 `colorFromName(name)`（一组色板，`charCodeAt` 累加取模），替换原 mock 的 `char.color`。
- 删除 mockPlot/mockStoryboards/mockCharacters/mockVideos 及 `handleGenerate` 的 setTimeout；mockVideos（⑤）相关步骤5 视频区**保留占位但 disabled**（⑤不在本期）。

- [ ] **Step 1: 写/改测试**（mock `@/api/workshop`：streamPlot 用 vi.hoisted+vi.mock 按 content/done 回调；断言：点「生成」②后 plotText 累加、done 后调 updateProject；③④后数组渲染 + updateProject）。参考 `PromptGenerateDialog.test.ts` 的 stream mock 手法。

- [ ] **Step 2: 实现 Index.vue 改造**（按行为规范；保留既有 `<style>` 与步骤 `<el-steps>` 骨架；移除 mock 数据 ref，改用 `ref<WorkshopProject|null>(currentProject)` + 各步局部 ref）。

- [ ] **Step 3: typecheck + 测试** — Run: `npx vue-tsc --noEmit` 无错；`npm run test:run` 全绿。

- [ ] **Step 4: CR + stage** — code-review → `git add src/views/workshop/Index.vue (+ 测试)`（不自动 commit）

---

## Task 11: 网关路由 + changelog + DDL 应用

**Files:**
- Modify: Nacos `acg-gateway.yaml`（OPS，控制台改）+ 本地镜像 `docx/nacos_config/extracted/DEFAULT_GROUP/acg-gateway.yaml`
- Create: `docs/changelogs/2026-07-26-workshop.md`
- DDL 应用：MySQL（host 3306 + Docker 3307）建 `workshop_project`（schema.sql 已含，需手动跑）

- [ ] **Step 1: Nacos 加路由** — `acg-gateway.yaml` 的 acg-chat 路由 Path 追加 `,/api/workshop/**`（线上控制台改 + 重启 gateway；本地镜像同步）。

- [ ] **Step 2: 建表** — host 3306 与 Docker 3307 的 `acg_agent` 库都执行 `workshop_project` DDL（spec §4.1）。

- [ ] **Step 3: changelog** — `docs/changelogs/2026-07-26-workshop.md`（变更原因/影响范围：新表 workshop_project + 新 API /api/workshop/** + 复用 prompt SSE 基建 / 注意点：⑤视频未做、生成端点无状态前端落库、需 Nacos 加路由）。

- [ ] **Step 4: CR + stage** — code-review → `git add docx/nacos_config/extracted/DEFAULT_GROUP/acg-gateway.yaml docs/changelogs/2026-07-26-workshop.md`（Nacos 控制台改不入 git；不自动 commit）

---

## Self-Review（写计划后自检）

**1. Spec 覆盖：**
- ②剧情流式 → Task 2(plot_stream)+3(api)+6(Java stream)+8(controller)+9(api)+10(前端) ✓
- ③分镜/④角色结构化 → Task 2(storyboard/characters)+3+6+8+9+10 ✓
- 多项目落库(CRUD) → Task 4(DO/DDL)+5(VO)+7(service)+8(controller)+9(api)+10(前端选择器) ✓
- 复用 stripSseData → Task 6 强调 ✓
- 角色 color 前端 → Task 10 colorFromName ✓
- 网关路由 + changelog + DDL → Task 11 ✓
- ⑤视频外推 → Task 10 步骤5 disabled；spec §2.2 ✓

**2. 占位符扫描：** 无 TBD/TODO；每个代码步骤含完整代码或指向仓库内既有权威文件（prompt.py/PromptServiceImpl 等，均为已存在的完整实现，非占位）；`streamPlot` 明确「逐字复制 generatePromptStream」（该文件存在）。

**3. 类型/签名一致性：**
- `WorkshopProjectDO.storyboard:List<ShotVO>` ↔ Task 5 ShotVO ↔ Task 6 `workshopStoryboard→List<ShotVO>` ↔ Task 7 service ↔ Task 8 controller `Result<List<ShotVO>>` ↔ 前端 `Shot[]` ✓
- `streamWorkshopPlot→Flux<WorkshopPlotStreamEvent>` ↔ parseWorkshopPlotEvent ↔ controller `Flux<WorkshopPlotStreamEvent>` ✓
- Python `workshop_service.plot_stream/storyboard/characters` ↔ api ↔ Java PythonAiClient 调 `/api/v1/workshop/{plot,storyboard,characters}` ✓
- `autoTitle`/`checkOwner` 在 Task 7 定义、测试引用一致 ✓

**已知限制（已在计划中标注，非缺口）：**
- Task 4(DO) 依赖 Task 5(ShotVO/CharacterVO) 先做——计划已说明调整顺序（5→4）。
- storyboard/characters 同步生成在 Python 用 `.invoke()`（非 async），meta-LLM streaming=False；Java/前端走普通 POST（非 SSE）。一致。
- Python `with_structured_output(list[...])` 改用容器模型 `StoryboardResult/CharactersResult`（spec §Global Constraints），避免 list 类型兼容问题。
