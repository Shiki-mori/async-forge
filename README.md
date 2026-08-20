# async-forge

基于 Spring Boot 3 的异步任务平台：任务提交后经 RabbitMQ 投递并由 Worker 消费执行，支持状态机流转、失败重试、死信队列与消费幂等。

`AGENT_TASK` 是同一套状态机上的一种任务类型，不是新平台。Java 负责可靠性，Python 负责智能。

## 架构

```text
控制台 / curl  ──HTTP──►  Java API（JWT、入库、投递）
                                  │
                                  ▼
                             RabbitMQ
                                  │
                    Java Worker（抢占、执行、重试、死信）
                                  │
                    AGENT_TASK 时阻塞一次 A2A SendMessage
                                  ▼
                    Python Agent（LangGraph + MCP 两工具）
```

| 层 | 职责 | 明确不做什么 |
|----|------|----------------|
| 前端控制台 | 提交任务、展示 payload / result / error | 不直连 Agent、不做聊天窗或流式打字 |
| Java | 鉴权、入库、MQ、抢占、重试、死信 | 不加 LLM SDK；不用 `HTTP_CALL` 打 Agent |
| Python Agent | 推理与受限工具（`http_get`、`calculator`） | 不连 MySQL / RabbitMQ，不写 `task` 表 |

Worker 远程调用（A2A / HTTP）在 DB 事务外进行。未通过 schema 校验的模型原文不会写成 `SUCCESS`。

## 技术栈

- Java 17 + Spring Boot 3.5
- Spring Security + JWT
- MyBatis-Plus + MySQL 8
- RabbitMQ
- Python 3.12 + FastAPI + a2a-sdk + LangGraph + MCP（Agent Runtime）
- Vue 3 控制台
- Docker Compose

## 快速启动

### Docker 一键启动（推荐）

```bash
cd deploy
cp .env.example .env
cp docker-compose.yml.example docker-compose.yml
# 在 .env 填写 AI_BASE_URL / AI_API_KEY / AI_MODEL（Agent 成功路径需要；forceFail 不需要）
docker compose up -d --build
```

会启动 MySQL、RabbitMQ、Python Agent、后端、前端。MySQL 首次启动时自动执行 `database/sql/schema.sql`。

| 服务 | 默认地址 |
|------|----------|
| 前端控制台 | http://localhost:5173 |
| 后端 API / Swagger | http://localhost:8090 、`/swagger-ui.html` |
| Python Agent | http://localhost:8081 （`/health`、`/.well-known/agent-card.json`） |
| RabbitMQ 管理台 | http://localhost:15672 （guest/guest） |

前端 Nginx 会把 `/api` 反代到后端容器，浏览器只需访问前端端口。不要把 Agent URL 填进 `HTTP_CALL`。

> **注意：** compose 使用独立项目名 `async-forge` 与独立网络，避免与同目录下其他项目冲突。宿主机端口可在 `.env` 中修改（如本机 3306 被占用可改 `MYSQL_PORT`；本机 8081 被占用可改 `AGENT_PORT`，Compose 内 Java 仍用 `http://agent:8081`）。

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

Agent 在宿主机单独跑（成功路径需要 `AI_API_KEY`；`forceFail` 不需要）：

```bash
cd agent
python3 -m venv .venv   # Python 3.12+
source .venv/bin/activate
pip install -e .
cp .env.example .env    # 填写 AI_BASE_URL / AI_API_KEY / AI_MODEL
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

核验 Agent：

```bash
curl -s http://localhost:8081/health
# {"status":"ok"}

curl -s http://localhost:8081/.well-known/agent-card.json
```

A2A JSON-RPC 使用 **`POST /`**，method 为 **`SendMessage`**（a2a-sdk 1.x / A2A 1.0），请求头必须带 **`A2A-Version: 1.0`**。用户 Message 的文本 part 是 JSON 字符串。直连 Agent 的调试 curl 见下方「可选：直连 Agent」。

## 演示流程

控制台（http://localhost:5173）登录后可提交同一套 payload，详情面板展示 Payload / Result / Error。下面三条是 README 必须可抄的演示（默认 `max_retry=3`）。不要用 `HTTP_CALL` 打 Agent。

### 注册并登录

```bash
curl -s -X POST http://localhost:8090/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}'
```

保存返回的 `token`。

### 1. 平台可靠性：DELAY_DEMO → 死信

```bash
curl -s -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"DELAY_DEMO","payload":{"seconds":1,"fail":true}}'
```

查询任务：

```bash
curl -s http://localhost:8090/api/tasks/<taskId> \
  -H "Authorization: Bearer <token>"
```

期望：`retryCount` 增加到耗尽 → 状态 `DEAD`；RabbitMQ 管理台能看到队列 `task.execute.dlq`。

### 2. Agent 成功（httpbin）

需要已填写 `AI_API_KEY`。控制台选 `AGENT_TASK`，默认指令即可。

```bash
curl -s -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"AGENT_TASK","payload":{"instruction":"GET https://httpbin.org/json ，告诉我 HTTP 状态码以及返回 JSON 的顶层字段名。","forceFail":false}}'
```

期望：`SUCCESS`；`result.summary` 非空；`result.toolCalls` 含 `http_get`。空 `instruction` 创建返回业务码 `40000`。

### 3. Agent 失败进死信

不调用成功路径（无需模型密钥）。证明 Agent 没有旁路现有重试策略。

```bash
curl -s -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"AGENT_TASK","payload":{"instruction":"任意内容","forceFail":true}}'
```

期望：任务最终 `DEAD`；`errorMessage` 可在控制台 Error 面板看到。

### 可选：DELAY_DEMO 成功 / HTTP_CALL

```bash
curl -s -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"DELAY_DEMO","payload":{"seconds":1}}'
```

```bash
curl -s -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"HTTP_CALL","payload":{"url":"https://httpbin.org/get"}}'
```

`DELAY_DEMO` / `HTTP_CALL` 的成功演示语义不变，不要改它们来接 Agent。

### 可选：直连 Agent

成功（LangGraph + MCP `http_get`，需要模型密钥）：

```bash
curl -s -X POST http://localhost:8081/ \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"1","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-ok","parts":[{"text":"{\"taskId\":1,\"instruction\":\"GET https://httpbin.org/json ，告诉我 HTTP 状态码以及返回 JSON 的顶层字段名。\",\"forceFail\":false}"}]}}}'
```

强制失败（A2A Task failed，无需模型）：

```bash
curl -s -X POST http://localhost:8081/ \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"2","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-fail","parts":[{"text":"{\"taskId\":2,\"instruction\":\"任意内容\",\"forceFail\":true}"}]}}}'
```

若改了 `AGENT_PORT`，把上面的 `8081` 换成该端口。Compose 内 Java 仍是 `http://agent:8081`。

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
├── agent/            # Python Agent Runtime（FastAPI + LangGraph + MCP）
├── backend/          # Spring Boot 后端
├── database/sql/     # DDL（P2 不改表）
├── deploy/           # Docker Compose
├── devLog/           # 搭建与验证记录
└── frontend/         # Vue 控制台（含 AGENT_TASK 表单）
```

Python Agent 骨架如何搭建、A2A 如何挂载、Compose 如何接入，见 [devLog/环境配置/Python-Agent骨架搭建.md](devLog/环境配置/Python-Agent骨架搭建.md)。Docker 启停与本机端口，见 [devLog/环境配置/Python-Agent-Docker.md](devLog/环境配置/Python-Agent-Docker.md)。

## 环境变量

见 `deploy/.env.example` 与 `agent/.env.example`。密钥请勿提交 Git，不要把真实 `AI_API_KEY` 写进示例文件。

| 变量 | 说明 |
|------|------|
| `AGENT_PORT` | 宿主机映射的 Agent 端口，默认 `8081` |
| `AGENT_BASE_URL` | Java Worker 调用 Agent 的基址。Compose 内必须是 `http://agent:8081`；仅本地 Java + 宿主机 Agent 时才用 `http://localhost:8081` |
| `AGENT_TIMEOUT_SECONDS` | A2A 阻塞调用超时，默认 `60` |
| `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` | OpenAI 兼容 Chat Completions。成功路径必填；`forceFail` 除外 |

不要把 Agent URL 交给 `HTTP_CALL`（SSRF 会拦 Docker 服务名 `agent`）。
