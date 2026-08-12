# async-forge

基于 Spring Boot 3 的异步任务平台：任务提交后经 RabbitMQ 投递并由 Worker 消费执行，支持状态机流转、失败重试、死信队列与消费幂等。

## 技术栈

- Java 17 + Spring Boot 3.5
- Spring Security + JWT
- MyBatis-Plus + MySQL 8
- RabbitMQ
- Docker Compose

## 快速启动

### Docker 一键启动（推荐）

```bash
cd deploy
cp .env.example .env
cp docker-compose.yml.example docker-compose.yml
docker compose up -d --build
```

会启动 MySQL、RabbitMQ、后端、前端。MySQL 首次启动时自动执行 `database/sql/schema.sql`。

| 服务 | 默认地址 |
|------|----------|
| 前端控制台 | http://localhost:5173 |
| 后端 API / Swagger | http://localhost:8090 、`/swagger-ui.html` |
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

# 后端
cd backend
set -a && source ../deploy/.env && set +a
mvn spring-boot:run

# 前端（Vite 将 /api 代理到 localhost:8090）
cd frontend && npm run dev
```
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
├── backend/          # Spring Boot 后端
├── database/sql/     # DDL
├── deploy/           # Docker Compose
└── frontend/         # Vue 控制台（P1，可选）
```

## 环境变量

见 `deploy/.env.example`。密钥请勿提交 Git。
