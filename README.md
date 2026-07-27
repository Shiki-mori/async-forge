# async-forge

基于 Spring Boot 3 的异步任务平台：任务提交后经 RabbitMQ 投递并由 Worker 消费执行，支持状态机流转、失败重试、死信队列与消费幂等。

## 技术栈

- Java 17 + Spring Boot 3.5
- Spring Security + JWT
- MyBatis-Plus + MySQL 8
- RabbitMQ
- Docker Compose

## 快速启动

### 1. 启动中间件

```bash
cd deploy
cp .env.example .env
cp docker-compose.yml.example docker-compose.yml
docker compose up -d
```

MySQL 会自动执行 `database/sql/schema.sql` 初始化表结构。

### 2. 启动后端

```bash
cd backend
export MYSQL_HOST=localhost MYSQL_USER=root MYSQL_PASSWORD=root
export RABBITMQ_HOST=localhost JWT_SECRET=change-me-use-at-least-32-characters-long
mvn spring-boot:run
```

服务默认端口：`8090`  
Swagger UI：`http://localhost:8090/swagger-ui.html`

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
