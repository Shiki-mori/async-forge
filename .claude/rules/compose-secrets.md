---
description: Compose 拓扑、环境变量占位、密钥不进 Git、改表同步
paths:
  - "deploy/**"
  - "database/**"
  - "**/.env.example"
  - "**/.gitignore"
  - "**/Dockerfile"
  - "README.md"
---

# Compose / 密钥 / 表结构

- 改表须同步 `database/sql/schema.sql` 与 `database/数据库设计.md`，并说明已有数据如何迁移（`task_type` 是 `VARCHAR(32)`，新增任务类型无需改表）。
- Compose 中 `agent` 服务：build context `../agent`，`container_name: async-forge-agent`，端口 `${AGENT_PORT:-8081}:8081`，网络 `async-forge`，healthcheck `GET /health`。
- backend 注入 `AGENT_BASE_URL=http://agent:8081`，`depends_on` agent healthy。仅本地只起 Java、Agent 在宿主机时才用 `localhost:8081`。
- `.env.example` 只放占位，不要把 `deploy/.env` 的真实 Key 写进示例。新增环境变量须同步 `.env.example`、`docker-compose.yml.example` 与 README。
- `.gitignore` 须包含 `deploy/.env`、`agent/.env`、`PRIVATE/`。镜像不烘焙 Key。Agent 镜像 Python 3.12-slim；uvicorn 听 `0.0.0.0:8081`。
- README 的启动和环境变量须与 Compose 配置一致，A2A method / 端口须与 [A2A 契约](../../docs/contracts/a2a-contract.md) 一致，三条演示须覆盖 [步骤 D 验证清单](../../devLog/验证/P2-步骤D.md)。
