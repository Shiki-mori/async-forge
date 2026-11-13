# async-forge

async-forge 是一个基于 Spring Boot 和 RabbitMQ 的异步任务平台。API 接收任务并返回任务 ID，Worker 异步执行，控制台可以查看状态、结果和失败原因。平台支持延迟演示、HTTP GET 和 Agent 任务，三种任务共享抢占、状态回写与有限重试。

[快速启动](#快速启动) · [API 演示](#api-演示) · [任务状态与消息确认](#任务状态与消息确认)

## 架构与关键实现

```text
控制台 / curl ──HTTP──► Java API ──写入 PENDING──► MySQL
                           │
                           └──提交后投递 taskId──► RabbitMQ ──► Java Worker
                                                                     │
                                                     AGENT_TASK 时通过 A2A 调用
                                                                     ▼
                                                       Python Agent（LangGraph + MCP）
```

- **提交后投递。** API 将任务写为 `PENDING`，在事务提交后通过 `afterCommit` 把 `taskId` 投递到 RabbitMQ，避免 Worker 读到尚未提交的任务。可查看 [TaskServiceImpl](backend/src/main/java/com/phrolova/asyncforge/service/impl/TaskServiceImpl.java) 和 [TaskProducer](backend/src/main/java/com/phrolova/asyncforge/mq/TaskProducer.java)。
- **条件抢占与短事务。** Worker 用条件更新获取执行权，抢占成功后才调用执行器。HTTP 和 A2A 调用在数据库事务外执行，执行结果再用短事务回写。可查看 [TaskMapper](backend/src/main/java/com/phrolova/asyncforge/mapper/TaskMapper.java) 和 [TaskExecutionService](backend/src/main/java/com/phrolova/asyncforge/service/TaskExecutionService.java)。
- **通过执行器扩展任务类型。** Worker 按任务类型选择执行器。`AGENT_TASK` 通过 A2A 调用 Python Agent，由 LangGraph 调度 MCP 工具 `http_get` 和 `calculator`。Java 在写入 `SUCCESS` 前校验 `summary` 为非空字符串、`toolCalls` 为数组。调用超时、Agent 返回失败和结果校验失败均走平台的重试流程。可查看 [TaskExecutorRegistry](backend/src/main/java/com/phrolova/asyncforge/worker/TaskExecutorRegistry.java)、[AgentTaskExecutor](backend/src/main/java/com/phrolova/asyncforge/worker/AgentTaskExecutor.java) 和 [Agent 执行图](agent/app/graph.py)。

后端使用 Java 17、Spring Boot 3.5、Spring Security + JWT、MyBatis-Plus 和 MySQL 8。Agent Runtime 使用 Python 3.12、FastAPI 和 a2a-sdk，控制台使用 Vue 3。

## 快速启动

安装 Docker 和 Compose 插件后，在仓库根目录执行以下命令。**运行 `DELAY_DEMO` 和演示 Agent 强制失败均无需模型密钥。**

```bash
cd deploy
test -f .env || cp .env.example .env
test -f docker-compose.yml || cp docker-compose.yml.example docker-compose.yml
docker compose up -d --build
```

Compose 会启动 MySQL、RabbitMQ、Python Agent、后端与前端。MySQL 初始化数据卷时会执行 [schema.sql](database/sql/schema.sql)。

### 在控制台体验任务执行

1. 打开 [任务控制台](http://localhost:5173)，注册或登录。
2. 选择 `DELAY_DEMO`，延迟填 1 秒，直接提交。选择刚创建的任务，查看 `SUCCESS` 状态和 Result 中的执行结果。
3. 勾选「强制失败」再提交一次。默认配置下，任务在第三次执行失败后进入 `DEAD`，详情显示 `retryCount=3`，Error 面板显示失败原因。

本地服务还包括 [Swagger UI](http://localhost:8090/swagger-ui.html)、[Agent 健康检查](http://localhost:8081/health) 和 [RabbitMQ 管理台](http://localhost:15672)（默认 `guest` / `guest`）。宿主机端口冲突时，可以在 `deploy/.env` 中调整对应的端口变量。

停止服务时，在 `deploy/` 目录运行 `docker compose down`。

## API 演示

### 获取令牌

首次运行时注册账号。已有同名账号时，将下面的请求路径改成 `/api/auth/login` 并使用对应密码。

```bash
curl -sS -X POST http://localhost:8090/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}'
```

将响应中的 `data.token` 填入下方变量，后续命令在同一终端运行。

```bash
TOKEN='替换为 data.token 的值'
```

### 1. DELAY_DEMO 失败与有限重试

```bash
curl -sS -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"DELAY_DEMO","payload":{"seconds":1,"fail":true}}'
```

将创建响应中的 `data.id` 填入 `TASK_ID`，然后查询任务状态。下方查询命令适用于所有任务类型。

```bash
TASK_ID='替换为 data.id 的值'
curl -sS "http://localhost:8090/api/tasks/$TASK_ID" \
  -H "Authorization: Bearer $TOKEN"
```

默认配置下，最终 `data.status` 为 `DEAD`，`data.retryCount` 为 3。要验证成功路径，将 `fail` 改为 `false` 后提交新任务并查看 `data.result`。

### 2. AGENT_TASK 成功调用 MCP 工具

先在 `deploy/.env` 填写 `AI_BASE_URL`、`AI_API_KEY` 和 `AI_MODEL`，模型需支持工具调用。如果服务已启动，在 `deploy/` 目录运行 `docker compose up -d agent` 以应用配置。此示例还需要 Agent 容器能够访问 `https://httpbin.org/json`。

```bash
curl -sS -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"AGENT_TASK","payload":{"instruction":"GET https://httpbin.org/json ，告诉我 HTTP 状态码以及返回 JSON 的顶层字段名。","forceFail":false}}'
```

预期 `data.status` 为 `SUCCESS`，`data.result.summary` 非空，`data.result.toolCalls` 中包含 `http_get` 的调用记录。

### 3. AGENT_TASK 强制失败并进入 DEAD

`forceFail` 会让 Python Agent 返回 A2A 失败状态，无需配置模型。Java Worker 通过同一个 `handleFailure` 处理这次失败。

```bash
curl -sS -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"AGENT_TASK","payload":{"instruction":"任意内容","forceFail":true}}'
```

预期 `data.status` 为 `DEAD`，`data.errorMessage` 记录失败原因。

### HTTP_CALL

`HTTP_CALL` 对指定的公开 URL 发起 GET 请求，例如 `https://httpbin.org/get`。控制台选择 `HTTP_CALL` 后填写 URL 即可。调用 Python Agent 时选择 `AGENT_TASK`。

## 任务状态与消息确认

```text
PENDING ──抢占──► RUNNING ──成功────────────────► SUCCESS
                    ├──执行失败且未达阈值───────► FAILED ──延迟消息抢占──► RUNNING
                    └──执行失败且达到阈值───────► DEAD
```

- **抢占。** 无 `task-retry-count` 消息头的消息只能抢占 `PENDING`。带该消息头的消息只能抢占 `FAILED` 且 `retry_count` 与消息头一致的任务。状态和次数由同一条条件更新校验，抢占失败时跳过执行。
- **有限重试。** `retryCount` 记录累计执行失败次数。默认 `max_retry=3`，第一次失败后等待 2 秒，第二次失败后等待 8 秒，第三次失败写 `DEAD`。未耗尽时先提交 `FAILED` 和失败原因，再投递延迟消息。业务失败处理完成后，消费者 ACK 当前消息。重试成功时清空上次的失败原因。
- **延迟重试队列。** 后端声明两条没有消费者的持久化队列。`task.exchange` 使用 `task.retry.2s` 将消息路由到 `task.retry.2s.q`（TTL 2 秒），使用 `task.retry.8s` 路由到 `task.retry.8s.q`（TTL 8 秒）。消息到期后经 `task.exchange` 和 `task.execute` 回到 `task.execute.q`。
- **RabbitMQ 死信。** 异常传播到消费者时，消费者发出 `NACK(requeue=false)`，RabbitMQ 按死信配置将消息路由到 `task.execute.dlq`。死信监听器会将尚未标记为 `DEAD` 的对应任务收口并确认消息。管理台中可以查看死信队列和路由配置，强制失败演示则通过任务状态与错误信息验证。可查看 [TaskConsumer](backend/src/main/java/com/phrolova/asyncforge/mq/TaskConsumer.java)、[RabbitMqConfig](backend/src/main/java/com/phrolova/asyncforge/config/RabbitMqConfig.java) 和 [DlqListener](backend/src/main/java/com/phrolova/asyncforge/mq/DlqListener.java)。

## 本地调试

先按[快速启动](#快速启动)准备 `deploy/.env` 和 `deploy/docker-compose.yml`。仅用 Compose 启动中间件时，从仓库根目录运行以下命令。

```bash
cd deploy
docker compose up -d mysql rabbitmq
```

在新的终端从仓库根目录启动后端。

```bash
cd backend
set -a && source ../deploy/.env && set +a
mvn spring-boot:run
```

在另一个终端从仓库根目录启动前端。Vite 会将 `/api` 代理到 `localhost:8090`。

```bash
cd frontend
npm ci
npm run dev
```

如需本地执行 `AGENT_TASK`，在新的终端从仓库根目录准备 Python 3.12 环境。

```bash
cd agent
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
test -f .env || cp .env.example .env
```

演示 Agent 成功路径时，先在 `agent/.env` 填写模型配置，再从 `agent/` 目录启动服务。

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

### 直连 Agent 调试

```bash
curl -sS http://localhost:8081/health
curl -sS http://localhost:8081/.well-known/agent-card.json
```

A2A JSON-RPC 使用 `POST /` 和 `SendMessage`（a2a-sdk 1.x / A2A 1.0），请求头为 `A2A-Version: 1.0`。用户消息的文本 part 携带 JSON 字符串。

调用 LangGraph 和 MCP `http_get`，需配置模型。

```bash
curl -s -X POST http://localhost:8081/ \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"1","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-ok","parts":[{"text":"{\"taskId\":1,\"instruction\":\"GET https://httpbin.org/json ，告诉我 HTTP 状态码以及返回 JSON 的顶层字段名。\",\"forceFail\":false}"}]}}}'
```

强制失败会返回 A2A Task failed，无需配置模型。

```bash
curl -s -X POST http://localhost:8081/ \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"2","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-fail","parts":[{"text":"{\"taskId\":2,\"instruction\":\"任意内容\",\"forceFail\":true}"}]}}}'
```

若修改了 `AGENT_PORT`，请将示例中的 `8081` 换成对应的宿主机端口。

## 环境变量

配置示例见 [deploy/.env.example](deploy/.env.example) 和 [agent/.env.example](agent/.env.example)。模型密钥保存在本地 `.env` 中。

| 变量 | 用途 |
| --- | --- |
| `AGENT_PORT` | Compose 映射到宿主机的 Agent 端口，默认 `8081` |
| `AGENT_BASE_URL` | Java Worker 调用 Agent 的基址。Compose 内为 `http://agent:8081`，Java 和 Agent 均在宿主机运行时为 `http://localhost:8081` |
| `AGENT_TIMEOUT_SECONDS` | A2A 请求超时，默认 60 秒 |
| `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` | OpenAI 兼容 Chat Completions 配置，用于执行 Agent 成功路径 |

## 目录结构

```text
async-forge/
├── backend/       # Spring Boot API、Worker 与任务执行器
├── agent/         # Python Agent Runtime
├── frontend/      # Vue 任务控制台
├── database/sql/  # MySQL 初始化脚本
├── deploy/        # Docker Compose 与环境变量示例
└── devLog/        # 搭建与验证记录
```

进一步了解 Agent 的骨架与 Compose 接入，可查看 [Python Agent 骨架搭建](devLog/环境配置/Python-Agent骨架搭建.md) 和 [Python Agent Docker 部署](devLog/环境配置/Python-Agent-Docker.md)。
