# async-forge

基于 Spring Boot 3 的异步任务平台：任务提交后经 RabbitMQ 投递并由 Worker 消费执行，支持状态机流转、失败重试、死信队列与消费幂等。

## 技术栈

- Java 17 + Spring Boot 3.5
- Spring Security + JWT
- MyBatis-Plus + MySQL 8
- RabbitMQ
- Python 3.12 + FastAPI + a2a-sdk（Agent Runtime，P2 步骤 A 骨架）
- Docker Compose

## 快速启动

### Docker 一键启动（推荐）

```bash
cd deploy
cp .env.example .env
cp docker-compose.yml.example docker-compose.yml
docker compose up -d --build
```

会启动 MySQL、RabbitMQ、Python Agent、后端、前端。MySQL 首次启动时自动执行 `database/sql/schema.sql`。

| 服务 | 默认地址 |
|------|----------|
| 前端控制台 | http://localhost:5173 |
| 后端 API / Swagger | http://localhost:8090 、`/swagger-ui.html` |
| Python Agent | http://localhost:8081 （`/health`、`/.well-known/agent-card.json`） |
| RabbitMQ 管理台 | http://localhost:15672 （guest/guest） |

前端 Nginx 会把 `/api` 反代到后端容器，浏览器只需访问前端端口。

> **注意：** compose 使用独立项目名 `async-forge` 与独立网络，避免与同目录下其他项目冲突。宿主机端口可在 `.env` 中修改（如本机 3306 被占用可改 `MYSQL_PORT`）。

停止：

```bash
cd deploy && docker compose down
```

### 本地调试（可选）

仅起中间件，后端 / 前端在宿主机跑：

```bash
cd deploy && docker compose up -d mysql rabbitmq

# 后端（本地 Java 连 Agent 时用 AGENT_BASE_URL=http://localhost:8081）
cd backend
set -a && source ../deploy/.env && set +a
mvn spring-boot:run

# 前端（Vite 将 /api 代理到 localhost:8090）
cd frontend && npm run dev
```

Agent 在宿主机单独跑（步骤 A 骨架，无需 `AI_API_KEY`）：

```bash
cd agent
python3 -m venv .venv   # Python 3.12+
source .venv/bin/activate
pip install -e .
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

核验 Agent：

```bash
curl -s http://localhost:8081/health
# {"status":"ok"}

curl -s http://localhost:8081/.well-known/agent-card.json
```

A2A JSON-RPC 使用 **`POST /`**，method 为 **`SendMessage`**（a2a-sdk 1.x / A2A 1.0），请求头必须带 **`A2A-Version: 1.0`**。用户 Message 的文本 part 是 JSON 字符串。

固定成功（无 LLM，供后续 Java 联调）：

```bash
curl -s -X POST http://localhost:8081/ \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"1","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-ok","parts":[{"text":"{\"taskId\":1,\"instruction\":\"hello\",\"forceFail\":false}"}]}}}'
```

强制失败（A2A Task failed）：

```bash
curl -s -X POST http://localhost:8081/ \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"2","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-fail","parts":[{"text":"{\"taskId\":2,\"instruction\":\"任意内容\",\"forceFail\":true}"}]}}}'
```

> **本步尚未接入 Java。** 控制台还不能提交 `AGENT_TASK`；`DELAY_DEMO` / `HTTP_CALL` 演示语义不变。Agent 成功 / 死信三条演示等到 P2 后续步骤。

## 演示流程

### 注册并登录

```bash
curl -s -X POST http://localhost:8090/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}'
```

保存返回的 `token`。

### 创建 DELAY_DEMO 任务（成功）

```bash
curl -s -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"DELAY_DEMO","payload":{"seconds":1}}'
```

### 创建 DELAY_DEMO 任务（强制失败 → 重试 → DEAD）

```bash
curl -s -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"DELAY_DEMO","payload":{"seconds":1,"fail":true}}'
```

查询任务状态：

```bash
curl -s http://localhost:8090/api/tasks/<taskId> \
  -H "Authorization: Bearer <token>"
```

### 创建 HTTP_CALL 任务

```bash
curl -s -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"HTTP_CALL","payload":{"url":"https://httpbin.org/get"}}'
```

## 任务状态流转

```text
PENDING → RUNNING → SUCCESS
                 ↘ FAILED →（未超 max_retry）→ PENDING → ...
                 ↘ FAILED →（超过 max_retry）→ DEAD
```

- **FAILED**：单次执行失败，仍可能重试
- **DEAD**：重试耗尽后的终态；消息也会进入 RabbitMQ 死信队列 `task.execute.dlq`

## 幂等消费

消费者通过条件更新 `PENDING/FAILED → RUNNING` 抢占任务；同一 `taskId` 重复投递不会重复执行已成功或已死信的任务。

## 目录结构

```text
async-forge/
├── agent/            # Python Agent Runtime（FastAPI + a2a-sdk）
├── backend/          # Spring Boot 后端
├── database/sql/     # DDL
├── deploy/           # Docker Compose
├── devLog/           # 搭建记录（含 Agent 骨架说明）
└── frontend/         # Vue 控制台（P1，可选）
```

Python Agent 骨架如何搭建、A2A 如何挂载、Compose 如何接入，见 [devLog/环境配置/Python-Agent骨架搭建.md](devLog/环境配置/Python-Agent骨架搭建.md)。Docker 启停与本机端口，见 [devLog/环境配置/Python-Agent-Docker.md](devLog/环境配置/Python-Agent-Docker.md)。

## 环境变量

见 `deploy/.env.example` 与 `agent/.env.example`。密钥请勿提交 Git，不要把真实 `AI_API_KEY` 写进示例文件。

| 变量 | 说明 |
|------|------|
| `AGENT_PORT` | 宿主机映射的 Agent 端口，默认 `8081` |
| `AGENT_BASE_URL` | Java Worker 调用 Agent 的基址。Compose 内必须是 `http://agent:8081`；仅本地 Java + 宿主机 Agent 时才用 `http://localhost:8081` |
| `AGENT_TIMEOUT_SECONDS` | A2A 阻塞调用超时，默认 `60`（步骤 B 才会被 Java 读取） |
| `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` | OpenAI 兼容 Chat Completions。**步骤 C 之前可留空** |

不要把 Agent URL 交给 `HTTP_CALL`（SSRF 会拦 Docker 服务名 `agent`）。
