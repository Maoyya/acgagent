# 2026-06-27 Nacos 配置持久化目标库由独立 nacos 库改为 acg_agent 业务库

## 变更原因
Nacos 容器此前一直启动失败（`Exited (1)`），定位到两个根因：
1. 连接 MySQL 8.0 报 `Public Key Retrieval is not allowed`（MySQL 8 默认 `caching_sha2_password` 认证，nacos JDBC 默认不允许取公钥）。
2. 期望持久化的 `nacos` 库不存在——数据卷 `docker_mysql-data` 已非空（旧容器遗留），MySQL 的 `docker-entrypoint-initdb.d/` 脚本只在卷首次为空时执行，因此 `02-nacos.sql` 从未运行。

同时按需求，让 Nacos 配置表与业务表共用 `acg_agent` 库，减少多库维护成本。已核对无表名冲突（nacos 的 `users`/`roles`/`permissions` 与业务的 `sys_user`/`sys_role`/`sys_permission` 不同名；其余 `config_*`/`tenant_*` 表业务侧不存在）。

## 影响范围
- **docker/docker-compose.yml**（nacos 服务）：
  - `MYSQL_SERVICE_DB_NAME`：`nacos` → `acg_agent`
  - 新增 `MYSQL_SERVICE_DB_PARAM`：放开 `allowPublicKeyRetrieval=true&useSSL=false` 等参数，修复 MySQL 8 连接
- **docker/init/nacos-schema.sql**：`CREATE DATABASE nacos; USE nacos;` → `USE acg_agent;`，12 张 nacos 表 + 默认 `nacos` 管理员账号建到业务库
- **数据库 acg_agent**：新增 12 张 nacos 表（`config_info` / `his_config_info` / `tenant_info` / `users` / `roles` / `permissions` / `config_info_aggr`/`beta`/`tag` / `config_tags_relation` / `group_capacity` / `tenant_capacity`）

## 变更前后对比
| 项 | 变更前 | 变更后 |
|---|---|---|
| Nacos 持久化库 | nacos（实际不存在，回退 Derby 内存） | acg_agent（业务库，已实测） |
| Nacos 容器状态 | Exited (1)，无法启动 | Up，`Nacos started successfully ... use external storage` |
| MySQL 8 连接 | 失败（Public Key Retrieval） | 成功（allowPublicKeyRetrieval=true） |

端到端实测：API 写入配置 → `acg_agent.config_info` 表可见 → 删除后归零。

## 注意
- ⚠️ **安全**：当前 nacos 鉴权为 `AUTH_DISABLED`（登录返回 `accessToken=AUTH_DISABLED`），任何人可访问控制台与 Open API。生产环境须开启 `nacos.core.auth.enabled=true` 并配置 identity/serverIdentity。本次未改鉴权，仅修持久化与连接。
- **数据卷已非空**：本次 nacos 表是手动 `docker exec ... < nacos-schema.sql` 灌入；未来若 `docker-compose down -v` 清空数据卷重建，改后的 schema 会被 initdb 自动执行（依赖 `01-init.sql` 先建好 acg_agent 库，文件名前缀顺序保证）。
- **命名空间不受影响**：微服务的 Nacos namespace 仍是 `acg_agent`（逻辑命名空间，对应 `tenant_info` 表），与「持久化用哪个物理库」是两回事，无需改微服务配置。
- **历史遗留**（✅ 已于 2026-06-27 清理）：原先存在一个 4 周前的旧 `mysql` 容器（Exited，绑定挂载 `D:\docker\mysql\data`、独立 `bridge` 网络、无 compose 标签）与一个 210MB 匿名孤儿卷（LINKS=0，无任何容器挂载），二者均与 compose 服务完全解耦，确认不影响 `docker-mysql-1` / `docker-nacos-1` 后已删除；同期另清理了 stray 的 acg-gateway 测试容器 `lucid_torvalds`（Exited 1，独立 `bridge` 网络）。

## 附：启动范围调整（compose profiles）

为支持「IDEA 点 docker-compose.yml 运行按钮一键只起中间件」，给 3 个 Java 应用服务（`acg-user`/`acg-chat`/`acg-gateway`）加 `profiles: ["app"]`。无 profile 的 4 个中间件（`mysql`/`nacos`/`zipkin`/`sentinel-dashboard`）在默认 `docker-compose up` 时启动。

- **IDEA 一键运行**（点 `services:` 行运行按钮）= 默认无 profile → 只起中间件，符合本地开发：Java 服务在 IDEA Run/Debug 调试。
- **需 Docker 化启动全部（含应用）**：`COMPOSE_PROFILES=app docker-compose up -d`。本机 Docker Desktop 自带的 compose（v5.1.x）不支持 `--profile` flag，必须用 `COMPOSE_PROFILES` 环境变量启用 profile。
- **动机**：避免 Docker 化应用与 IDEA 本地运行的实例端口冲突（8080/8081/8082），保留调试便利。
- **一键脚本**：新增 `docker/start-middleware.bat`，双击即启动 4 个中间件（等价于 `docker-compose up -d mysql nacos zipkin sentinel-dashboard`），无需进命令行或依赖 IDEA Docker 集成。脚本显式指定 4 个服务名，不依赖 profiles，稳定可靠。
