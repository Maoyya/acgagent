# AI 视频生产平台设计文档

**版本**: 1.0
**日期**: 2026-05-20
**状态**: 初稿

---

## 1. 项目概述

### 1.1 背景

在现有 acgagent AI 对话助手基础上，扩展一个 AI 视频生产模块（类似小云雀），实现"故事梗概→剧情扩展→分镜生成→图片生成→视频生成"的完整生产管线。

### 1.2 目标用户

个人/小团队使用，不需要复杂的权限控制和并发处理。

### 1.3 核心流程

```
用户输入故事梗概
       │
       ▼
  ① 豆包扩展剧情 ──→ 用户确认/编辑剧情 ──→ 保存
       │
       ▼
  ② 豆包生成分镜 ──→ 用户确认/编辑分镜 ──→ 保存
       │
       ▼
  ③ 豆包生成人物描述 → 用户确认/编辑 → 保存
       │
       ▼
  ④ 即梦生成人物三视图/分镜图 ──→ 用户确认/重新生成 ──→ 保存
       │
       ▼
  ⑤ Seedance 2.0 图生视频 ──→ 用户查看/下载 ──→ 保存到素材库
```

分步确认模式：每个阶段用户都可以预览、编辑后再进入下一步。

### 1.4 技术选型

| 模型 | 用途 | 平台 |
|------|------|------|
| 豆包 (doubao-1.5-pro) | 剧情扩展、分镜生成、人物描述 | 火山引擎 |
| 即梦 (jimeng-2.1) | 人物三视图、分镜图生成 | 火山引擎 |
| Seedance 2.0 | 图生视频 | 火山引擎 |
| Vue 3 + Element Plus | 前端 | - |
| Spring Boot 3.3.5 | 后端（新增 service-video 模块） | - |

---

## 2. 整体架构

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────┐
│                    Vue 3 前端                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │ 登录/注册 │ │ 项目管理  │ │ 视频工坊  │ │ 素材库  │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────┘ │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP / SSE
┌──────────────────────▼──────────────────────────────┐
│               Gateway (8080)                         │
│  /api/user/**    → lb://acgagent-service-user        │
│  /api/video/**   → lb://acgagent-service-video       │
└───────┬─────────────────────────┬───────────────────┘
        │                         │
┌───────▼──────────┐  ┌──────────▼────────────────────┐
│  service-user     │  │  service-video (新增)          │
│  (8081, 现有)     │  │  (8082)                       │
│  用户注册/登录     │  │  项目管理                      │
│  用户信息 CRUD     │  │  故事扩展 (豆包 API)           │
│  RBAC 权限        │  │  分镜生成 (豆包 API)           │
│                   │  │  图片生成 (即梦 API)           │
│                   │  │  视频生成 (Seedance API)       │
│                   │  │  素材库管理                    │
│                   │  │  任务调度 (异步)               │
└───────┬──────────┘  └──────────┬───────────────────┘
        │                         │
   ┌────▼────┐         ┌─────────▼──────────┐
   │ MySQL   │         │ 火山引擎 API        │
   │ acgagent│         │ · 豆包 (文本生成)   │
   │         │         │ · 即梦 (AI 绘图)    │
   └─────────┘         │ · Seedance (视频)   │
                       └────────────────────┘
```

### 2.2 Maven 模块

```
acgagent (parent)
  ├── acgagent-common         (现有，不变)
  ├── acgagent-gateway        (现有，新增 /api/video/** 路由)
  ├── acgagent-service-user   (现有，不变)
  └── acgagent-service-video  (新增)
       pom.xml 依赖：
       · acgagent-common
       · spring-boot-starter-web
       · mybatis-plus
       · mysql-connector
       · nacos-discovery
       · okhttp (调用火山引擎 API)
```

---

## 3. 数据模型

### 3.1 新增数据库表

在 `acgagent` 数据库中新增以下表：

**video_project — 视频项目表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT, 自增 | 主键 |
| user_id | BIGINT | 关联用户 |
| title | VARCHAR(200) | 项目标题 |
| story_input | TEXT | 用户输入的故事梗概 |
| status | TINYINT | 0=草稿 1=剧情扩展中 2=分镜生成中 3=图片生成中 4=视频生成中 5=已完成 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| deleted | TINYINT | 逻辑删除 |

**story — 剧情表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT, 自增 | 主键 |
| project_id | BIGINT | 关联项目 |
| content | LONGTEXT | 扩展后的剧情文本 |
| version | INT | 版本号，支持多次重新生成 |
| is_confirmed | TINYINT | 用户是否已确认 |
| created_at / updated_at / deleted | - | 基础字段 |

**storyboard — 分镜表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT, 自增 | 主键 |
| project_id | BIGINT | 关联项目 |
| story_id | BIGINT | 关联剧情 |
| scene_number | INT | 场景序号 |
| shot_number | INT | 镜头序号 |
| shot_description | TEXT | 镜头描述文本 |
| camera_movement | VARCHAR(100) | 镜头运动: 推/拉/摇/移/固定 |
| duration_seconds | DECIMAL(4,1) | 预估时长(秒) |
| dialogue | TEXT | 对白 |
| narration | TEXT | 旁白 |
| sort_order | INT | 排序 |
| is_confirmed | TINYINT | 用户是否已确认 |
| created_at / updated_at / deleted | - | 基础字段 |

**character_profile — 人物表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT, 自增 | 主键 |
| project_id | BIGINT | 关联项目 |
| name | VARCHAR(50) | 角色名 |
| description | TEXT | 外貌/性格描述 |
| reference_prompt | TEXT | AI 绘图用 prompt |
| is_confirmed | TINYINT | 用户是否已确认 |
| created_at / updated_at / deleted | - | 基础字段 |

**asset — 素材表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT, 自增 | 主键 |
| project_id | BIGINT | 关联项目 |
| asset_type | TINYINT | 0=人物三视图 1=分镜图 2=视频片段 3=完整视频 |
| ref_id | BIGINT | 关联 character_profile.id 或 storyboard.id |
| file_url | VARCHAR(500) | 资源文件 URL |
| file_size | BIGINT | 文件大小(bytes) |
| prompt_used | TEXT | 生成时使用的 prompt |
| model_used | VARCHAR(50) | jimeng / seedance |
| task_id | VARCHAR(100) | 火山引擎异步任务 ID |
| status | TINYINT | 0=生成中 1=成功 2=失败 |
| is_confirmed | TINYINT | 用户是否已确认 |
| created_at / updated_at / deleted | - | 基础字段 |

**ai_call_log — AI 调用日志表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT, 自增 | 主键 |
| project_id | BIGINT | 关联项目 |
| user_id | BIGINT | 关联用户 |
| call_type | VARCHAR(20) | STORY / STORYBOARD / IMAGE / VIDEO |
| model_name | VARCHAR(50) | doubao / jimeng / seedance-2.0 |
| request_params | JSON | 请求参数快照 |
| response_summary | TEXT | 响应摘要 |
| tokens_used | INT | token 消耗(文本类) |
| duration_ms | INT | 调用耗时(ms) |
| status | TINYINT | 0=成功 1=失败 2=超时 |
| error_message | TEXT | 失败时的错误信息 |
| created_at | DATETIME | 创建时间 |

### 3.2 实体关系

```
user (1) ──→ (N) video_project
video_project (1) ──→ (1) story
video_project (1) ──→ (N) storyboard
video_project (1) ──→ (N) character_profile
video_project (1) ──→ (N) asset
storyboard (1) ──→ (0..1) asset (分镜图)
character_profile (1) ──→ (0..N) asset (三视图)
storyboard (1) ──→ (0..1) asset (视频片段)
video_project (1) ──→ (0..1) asset (完整视频)
```

### 3.3 项目状态机

```
0 草稿 ──→ 1 剧情扩展中 ──→ 2 分镜生成中 ──→ 3 图片生成中 ──→ 4 视频生成中 ──→ 5 已完成
  ↑              │                 │                 │                 │
  └──────────────┴─────────────────┴─────────────────┴─────────────────┘
                         用户可随时退回某一步重新生成
```

---

## 4. API 接口设计

### 4.1 接口总览

所有视频相关接口前缀 `/api/video`，需登录（JWT）。

**项目管理**

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/video/project` | POST | 创建项目（传入故事梗概） |
| `/api/video/project` | GET | 获取项目列表（分页） |
| `/api/video/project/{id}` | GET | 获取项目详情（含当前进度） |
| `/api/video/project/{id}` | PUT | 更新项目标题 |
| `/api/video/project/{id}` | DELETE | 删除项目（软删除） |

**剧情**

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/video/project/{id}/story/generate` | POST | 调用豆包扩展剧情（SSE 流式） |
| `/api/video/project/{id}/story` | GET | 获取当前剧情 |
| `/api/video/project/{id}/story` | PUT | 用户编辑剧情 |
| `/api/video/project/{id}/story/confirm` | POST | 确认剧情，进入下一步 |

**分镜**

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/video/project/{id}/storyboard/generate` | POST | 调用豆包生成分镜 |
| `/api/video/project/{id}/storyboard` | GET | 获取分镜列表 |
| `/api/video/project/{id}/storyboard/{shotId}` | PUT | 编辑单个分镜 |
| `/api/video/project/{id}/storyboard/confirm` | POST | 确认分镜，进入下一步 |

**人物**

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/video/project/{id}/character/generate` | POST | 调用豆包生成人物描述 |
| `/api/video/project/{id}/character` | GET | 获取人物列表 |
| `/api/video/project/{id}/character/{charId}` | PUT | 编辑人物描述 |
| `/api/video/project/{id}/character/{charId}/confirm` | POST | 确认人物 |

**图片生成**

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/video/project/{id}/character/{charId}/image/generate` | POST | 调用即梦生成人物三视图 |
| `/api/video/project/{id}/storyboard/{shotId}/image/generate` | POST | 调用即梦生成分镜图 |
| `/api/video/project/{id}/image/{assetId}/confirm` | POST | 确认图片 |
| `/api/video/project/{id}/image/{assetId}/regenerate` | POST | 重新生成图片 |

**视频生成**

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/video/project/{id}/video/generate` | POST | 调用 Seedance 批量生成视频 |
| `/api/video/project/{id}/video/{assetId}/status` | GET | 查询单个视频生成状态 |
| `/api/video/project/{id}/video` | GET | 获取项目所有视频片段 |

**素材库**

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/video/asset` | GET | 获取用户所有素材（分页、按类型筛选） |
| `/api/video/asset/{id}` | GET | 获取素材详情 |
| `/api/video/asset/{id}` | DELETE | 删除素材 |
| `/api/video/asset/{id}/download` | GET | 获取下载链接 |

### 4.2 核心接口详细设计

**创建项目** `POST /api/video/project`

```json
// Request
{
  "title": "猫和老鼠的冒险",
  "storyInput": "一只猫和一只老鼠成了朋友，一起踏上寻找奶酪王国的冒险旅程..."
}

// Response
{
  "code": 200,
  "data": {
    "id": 1,
    "title": "猫和老鼠的冒险",
    "status": 0,
    "storyInput": "..."
  }
}
```

**扩展剧情** `POST /api/video/project/{id}/story/generate`（SSE 流式）

```
event: message
data: {"type": "content", "text": "在一个阳光明媚的早晨..."}

event: message
data: {"type": "content", "text": "小猫橘子和老鼠小灰..."}

event: done
data: {"storyId": 1, "version": 1, "totalTokens": 2500}
```

**生成分镜** `POST /api/video/project/{id}/storyboard/generate`

```json
// Response
{
  "code": 200,
  "data": {
    "storyboardId": 10,
    "shots": [
      {
        "sceneNumber": 1,
        "shotNumber": 1,
        "shotDescription": "远景：一座小镇全景，阳光洒在屋顶上",
        "cameraMovement": "缓慢推进",
        "durationSeconds": 3.0,
        "dialogue": null,
        "narration": "在一个遥远的小镇上..."
      },
      {
        "sceneNumber": 1,
        "shotNumber": 2,
        "shotDescription": "中景：猫橘子在窗台上伸懒腰",
        "cameraMovement": "固定",
        "durationSeconds": 2.5,
        "dialogue": "又是美好的一天！",
        "narration": null
      }
    ]
  }
}
```

**生成视频** `POST /api/video/project/{id}/video/generate`

```json
// Request
{
  "shotIds": [1, 2, 3, 5, 8]
}

// Response
{
  "code": 200,
  "data": {
    "tasks": [
      {"shotId": 1, "assetId": 100, "taskId": "volc-task-xxx", "status": 0},
      {"shotId": 2, "assetId": 101, "taskId": "volc-task-yyy", "status": 0}
    ]
  }
}
```

### 4.3 Gateway 路由新增

```yaml
- id: video-service
  uri: lb://acgagent-service-video
  predicates:
    - Path=/api/video/**
```

---

## 5. service-video 内部架构

### 5.1 分层结构

```
controller/          REST 接口层，参数校验，SSE 流式响应
  ├── VideoProjectController
  ├── StoryController
  ├── StoryboardController
  ├── CharacterController
  ├── AssetController
  └── VideoGenerateController

service/             业务逻辑层
  ├── VideoProjectService
  ├── StoryService
  ├── StoryboardService
  ├── CharacterService
  ├── AssetService
  └── VideoGenerateService  (编排整个生成管线)

integration/         外部 API 集成层
  ├── volcengine/
  │   ├── VolcengineClient          (通用 HTTP 客户端，签名鉴权)
  │   ├── DoubaoClient              (豆包文本生成)
  │   ├── JimengClient              (即梦 AI 绘图)
  │   └── SeedanceClient            (Seedance 2.0 视频生成)
  │   └── dto/
  │       ├── DoubaoRequest/Response
  │       ├── JimengRequest/Response
  │       └── SeedanceRequest/Response
  │   └── VolcengineConfig          (API Key, Endpoint 配置)

mapper/              MyBatis-Plus Mapper 层
entity/              DO 实体类
converter/           DO <-> VO 转换
model/               VO / DTO
config/              配置类
```

### 5.2 integration 层接口定义

```
DoubaoClient:
  - generateStory(prompt, storyInput) → String (SSE)
  - generateStoryboard(story) → List<StoryboardDTO>
  - generateCharacterDesc(story) → List<CharacterDTO>
  API: /api/v3/text/generation (流式)

JimengClient:
  - generateImage(prompt, refImages) → taskId
  - queryImageResult(taskId) → ImageDTO
  API: /api/v2/image/generation (异步)

SeedanceClient:
  - generateVideo(imageUrl, prompt, audioUrl) → taskId
  - queryVideoResult(taskId) → VideoDTO
  API: /v1/videos (异步)
```

### 5.3 异步任务处理

图片和视频生成是异步的（提交任务 → 轮询结果）。使用 Spring `@Async` + 自定义 `ThreadPoolTaskExecutor`。

```
前端 POST /generate
       │
       ▼
  Controller 提交任务
       │
       ├── 记录 asset (status=0, taskId=xxx)
       │
       └── 异步线程池执行：
            ├── 调用火山引擎 API 提交任务
            ├── 轮询任务状态（间隔 5s，最大等待 10min）
            ├── 成功 → 更新 asset (status=1, fileUrl=xxx)
            └── 失败 → 更新 asset (status=2, errorMessage=xxx)
```

### 5.4 Prompt 模板

```yaml
volcengine:
  prompts:
    story-expand: |
      你是一位专业的编剧。请根据以下故事梗概，扩展为完整的剧情描述。
      要求：情节连贯、角色鲜明、场景描述具体。
      故事梗概：{storyInput}

    storyboard-generate: |
      你是一位专业导演。请根据以下剧情，生成详细的分镜脚本。
      每个分镜包含：场景描述、镜头运动、时长、对白、旁白。
      输出为 JSON 数组格式。
      剧情：{story}

    character-describe: |
      你是一位角色设计师。请为以下剧情中的角色生成详细的外貌描述，
      包含：面部特征、发型、服装、体型，适合用于 AI 绘图生成。
      输出为 JSON 数组格式。
      剧情：{story}
```

### 5.5 配置项

```yaml
volcengine:
  api-key: ${VOLC_API_KEY}
  base-url: https://ark.cn-beijing.volces.com
  models:
    doubao: doubao-1.5-pro-32k
    jimeng: jimeng-2.1
    seedance: seedance-2.0
  task:
    poll-interval: 5000
    max-wait: 600000
    max-retries: 3
```

### 5.6 错误处理

| 场景 | 处理方式 |
|------|---------|
| 火山引擎 API 调用失败 | 重试 3 次，仍失败则标记 asset status=2 |
| 视频生成超时 | 标记失败，提示用户重试 |
| API 配额不足 | 返回 429，前端提示"生成次数已达上限" |
| 图片内容审核不通过 | 返回审核失败原因，建议修改 prompt |

---

## 6. Vue 3 前端设计

### 6.1 技术栈

| 技术 | 用途 |
|------|------|
| Vue 3 + TypeScript | 框架 |
| Vite | 构建工具 |
| Pinia | 状态管理 |
| Vue Router | 路由 |
| Element Plus | UI 组件库 |
| Axios | HTTP 请求 |
| SSE (EventSource) | 流式接收剧情生成 |

独立前端项目，不和后端在同一个仓库。

### 6.2 页面结构

```
src/
  views/
    ├── auth/
    │   ├── Login.vue              登录页
    │   └── Register.vue           注册页
    ├── project/
    │   ├── ProjectList.vue        项目列表（首页）
    │   └── ProjectCreate.vue      创建项目（输入故事梗概）
    ├── workshop/
    │   ├── Workshop.vue           工坊主容器（左右分栏布局）
    │   ├── StepNavigator.vue      步骤导航条
    │   ├── StoryEditor.vue        剧情编辑/确认
    │   ├── StoryboardEditor.vue   分镜编辑/确认
    │   ├── CharacterEditor.vue    人物描述编辑/确认
    │   ├── ImageViewer.vue        图片预览/确认/重新生成
    │   └── VideoPlayer.vue        视频播放/下载
    ├── asset/
    │   └── AssetLibrary.vue       素材库
    └── layout/
        ├── MainLayout.vue         主布局
        └── Sidebar.vue            侧边导航
  components/
    ├── common/
    │   ├── SseStream.vue          SSE 流式文本展示组件
    │   ├── ImageCard.vue          图片卡片
    │   └── VideoCard.vue          视频卡片
    └── workshop/
        ├── StoryboardCard.vue     单个分镜卡片
        └── CharacterCard.vue      单个角色卡片
  api/
    ├── auth.ts, project.ts, story.ts, storyboard.ts,
    ├── character.ts, asset.ts, video.ts
  stores/
    ├── user.ts                    用户状态 + JWT
    ├── project.ts                 当前项目状态
    └── workshop.ts                工坊步骤状态管理
```

### 6.3 核心页面：视频工坊

左右分栏布局：

```
┌──────────────────────────────────────────────────────────────┐
│  猫和老鼠的冒险                              [步骤: ①②③④⑤⑥] │
├────────────────────────────┬─────────────────────────────────┤
│                            │                                 │
│     左侧：内容区            │      右侧：预览/AI 交互区        │
│                            │                                 │
│  当前步骤的内容列表          │  AI 生成的结果预览               │
│  (分镜列表/人物列表等)       │  (图片放大/视频播放/文本编辑)    │
│                            │                                 │
│                            │  [重新生成] [确认并下一步]        │
│                            │                                 │
└────────────────────────────┴─────────────────────────────────┘
```

### 6.4 路由设计

```
/login                    → Login.vue
/register                 → Register.vue
/                         → ProjectList.vue
/project/create           → ProjectCreate.vue
/project/:id/workshop     → Workshop.vue
/assets                   → AssetLibrary.vue
```

### 6.5 前端状态管理

```typescript
interface WorkshopState {
  project: VideoProject | null
  currentStep: 1 | 2 | 3 | 4 | 5
  story: Story | null
  storyboards: Storyboard[]
  characters: CharacterProfile[]
  assets: Asset[]
  generating: boolean
  generatingStep: number | null
}
```

### 6.6 前后端联调

```
开发阶段：
  前端 Vite dev server (localhost:5173)
      ↓ proxy /api → Gateway (localhost:8080)
                        ↓
                    service-video (localhost:8082)
```

---

## 7. 非功能需求

### 7.1 安全

| 项目 | 方案 |
|------|------|
| 认证 | 复用现有 JWT 体系，视频接口需登录 |
| API Key | 环境变量注入，不写入代码或配置文件 |
| 文件访问 | 火山引擎临时签名链接，设过期时间 |
| 输入校验 | 故事文本限 5000 字，标题限 200 字，防 XSS |

### 7.2 费用估算

| 模型 | 计费方式 | 预估单次调用成本 |
|------|---------|---------------|
| 豆包 | 按 token | 约 0.02-0.05 元/项目 |
| 即梦 | 按张 | 约 0.04-0.10 元/张，每项目 10-20 张 |
| Seedance 2.0 | 按时长 | 约 0.5-2 元/片段(5s)，每项目 5-10 个片段 |

单项目完整流程预估 5-20 元。

### 7.3 技术风险

| 风险 | 缓解措施 |
|------|---------|
| 火山引擎 API 变更/限流 | Client 层隔离，方便切换；重试机制 |
| Seedance 生图质量不稳定 | 支持重新生成，用户可调整 prompt |
| 长时间异步任务占用资源 | 任务超时(10min)，线程池大小限制 |

---

## 8. 开发分期

| 阶段 | 内容 | 预估 |
|------|------|------|
| P0：基础骨架 | service-video 模块搭建、数据库建表、实体/Mapper/CRUD、火山引擎 Client 封装(先 mock) | 3-4 天 |
| P1：核心管线 | 豆包集成(剧情+分镜+人物)、即梦集成(图片生成)、SSE 流式响应、前端工坊页面 | 7-10 天 |
| P2：视频生成 | Seedance 2.0 集成、异步任务轮询、视频播放/下载 | 3-5 天 |
| P3：周边功能 | 用户登录对接、项目管理、素材库、前端联调 | 3-5 天 |
| P4：打磨上线 | 云服务器部署、Nginx 反向代理、异常处理、UI 细节 | 3-4 天 |

总预估：约 3-4 周（1 人全职）。
