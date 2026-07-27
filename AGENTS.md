# AGENTS.md

## 项目名称

async-forge

## 项目概览

本项目是一个面向校招作品集的 **异步任务平台 / 轻量工作流**。

目标：  
用 Spring Boot 实现「提交任务 → 消息队列可靠执行 → 状态可查 → 失败重试 / 死信」的完整链路。优先体现 Java 后端工程能力。  
若时间允许，再增加一类 `AGENT_TOOL` 任务，演示 Agent 工具调用如何接入同一套异步与失败处理机制。

本项目是 **另一个AI简历分析业务系统的互补项目**，差异点必须落在：消息队列、消费语义、重试、死信、幂等，而不是再做一个「调大模型的业务 App」。

## 技术栈

后端：

- Java 17
- Spring Boot 3.5.15
- Spring Security + JWT
- MyBatis-Plus
- MySQL 8
- RabbitMQ（必须；不要用 `@Async` 代替 MQ）
- Redis（可选：限流、幂等 Key）
- springDoc OpenAPI
- Docker Compose

前端（P1，可选）：

- Vue 3 + TypeScript + Vite
- 极简任务控制台即可

Agent 扩展（P2，可选）：

- OpenAI 兼容 Chat Completions API
- 环境变量注入 API Key

## 设计原则

1. **先可靠异步，后智能**：P0 未完成前不做 Agent。
2. **窄而硬**：功能少，但 MQ、重试、死信、幂等必须可演示、可讲清。
3. **调用外部 AI / HTTP 时不长时间持有 DB 事务**。
4. **密钥不入库、不进 Git**：AI Key、JWT、DB 密码走环境变量 / `.env`。

## 推荐仓库结构

```text
async-forge/  
├── AGENTS.md
├── README.md
├── deploy/
│   ├── docker-compose.yml.example
│   └── .env.example
├── database/sql/schema.sql
├── backend/                   # Spring Boot
└── frontend/                  # 可选
```

后端包结构建议（按技术分层）：

```text
com.phrolova.asyncforge/
├── AsyncFlowApplication.java
├── config/          -- Security、RabbitMQ、Redis、Async 无关的线程池仅作辅助
├── common/          -- Result、ErrorCode、RateLimit
├── exception/
├── auth/            -- JwtUtil、Filter、UserContext
├── entity/
├── mapper/
├── dto/request|response/
├── controller/
├── service/|impl/
├── mq/              -- Producer、Consumer、DLQ 监听、消息模型
├── worker/          -- 各 TaskType 执行器（策略模式）
├── workflow/        -- P1：顺序步骤编排（可选）
└── agent/           -- P2：工具注册与 Agent 任务执行（可选）
```

## 核心领域模型

### 任务状态

```text
PENDING   -- 已入库，等待投递或等待消费
RUNNING   -- Worker 执行中
SUCCESS   -- 成功
FAILED    -- 失败且仍可能被重试策略处理中 / 已记失败
DEAD      -- 重试耗尽，进入死信后的终态
```

建议流转：

```text
创建 → PENDING
  →（投递 MQ）→ 消费中 RUNNING
  → SUCCESS
  → 失败 →（未超最大重试）重新入队 → PENDING/RUNNING
  → 失败 →（超过最大重试）DEAD
```

`FAILED` 与 `DEAD` 的语义在实现中必须写清，并在 README / 面试口述一致。

### 任务类型（P0）

| taskType     | 行为                               |
|--------------|----------------------------------|
| `HTTP_CALL`  | 按 payload 请求外部 HTTP，记录状态码与响应摘要   |
| `DELAY_DEMO` | 睡眠 N 秒；可由 payload 强制失败，用于演示重试与死信 |

### 任务类型（P2）

| taskType     | 行为                                         |
|--------------|--------------------------------------------|
| `AGENT_TOOL` | LLM + 少量工具调用，结果写入 task result；失败走同一套重试/DLQ |

## 功能范围

### P0（必须）

- 注册 / 登录（JWT）
- 创建任务、查询任务详情、任务列表（按当前用户）
- RabbitMQ 生产与消费
- 状态机更新
- 有限重试 + 死信队列
- 消费幂等（基于 taskId）
- `HTTP_CALL`、`DELAY_DEMO` 两种执行器
- Docker Compose 一键启动中间件与服务
- README

### P1（建议）

- 手动重试（DEAD/FAILED → 重新入队）
- 顺序工作流 2～3 步（轻量，自研状态表即可，不上重型引擎）
- 极简前端控制台
- 创建任务限流
- Swagger

### P2（可选）

- `AGENT_TOOL` 任务
- 工具注册表（≥2 个工具）
- 工具超时与错误纳入重试/死信
- AI 调用耗时日志

### 非目标（禁止第一版做）

- Kafka 替代 RabbitMQ（除非已完成 P0 且有明确理由）
- 复杂 DAG / 可视化流程编辑器
- Multi-Agent、向量库、长期记忆
- 大而全权限体系、分库分表、完整可观测平台

## 功能清单（按优先级）

### P0 — 骨架必须有

| ID  | 功能             | 说明                                                 |
|-----|----------------|----------------------------------------------------|
| F01 | 用户注册 / 登录      | JWT + BCrypt；任务按用户隔离                               |
| F02 | 创建任务           | 指定 `taskType` + JSON payload，立即返回 `taskId`         |
| F03 | 任务状态机          | `PENDING → RUNNING → SUCCESS / FAILED`；失败可进 `DEAD` |
| F04 | 查询任务           | 按 ID 查详情；列表查当前用户任务                                 |
| F05 | MQ 投递          | 创建后发到 RabbitMQ，**禁止**仅用 `@Async` 充当异步              |
| F06 | Worker 消费      | 独立消费者拉取、执行、更新状态                                    |
| F07 | ACK / 失败重试     | 业务失败或异常：有限次重试；耗尽进死信                                |
| F08 | 死信队列           | DLQ 可观察；任务标记 `DEAD`，保留错误信息                         |
| F09 | 幂等消费           | 同一 `taskId` 重复投递不重复产生副作用                           |
| F10 | 至少 2 种内置任务     | 如：`HTTP_CALL`（调外部 URL）、`DELAY_DEMO`（可失败模拟）         |
| F11 | 统一响应 / 全局异常    | 与工程规范一致                                            |
| F12 | Docker Compose | MySQL + Redis（可选）+ RabbitMQ + 后端（+ 可选前端）           |
| F13 | README         | 启动方式、架构图、与简历表述一致                                   |

### P1 — 强烈建议（有时间就做）

| ID  | 功能      | 说明                                       |
|-----|---------|------------------------------------------|
| F14 | 手动重试    | `DEAD` / `FAILED` 任务重新入队                 |
| F15 | 轻量工作流   | 2～3 步顺序节点（step1 → step2 → step3），每步可失败重试 |
| F16 | 管理控制台   | 极简 Vue：提交任务、看状态、触发重试                     |
| F17 | 限流      | 创建任务用户维度限流（Redis）                        |
| F18 | 可观测     | 关键日志：投递、消费、重试次数、进入 DLQ；可选简单 metrics      |
| F19 | OpenAPI | springdoc Swagger                        |

### P2 — 有余力再做（Agent 扩展）

| ID  | 功能                | 说明                                         |
|-----|-------------------|--------------------------------------------|
| F20 | `AGENT_TOOL` 任务类型 | LLM 决定是否调工具，执行后写回结果                        |
| F21 | 工具注册表             | 2 个工具即可：如 `http_get`、`echo` / `calculator` |
| F22 | 工具超时与失败           | 超时/失败走同一套重试与死信，不另起炉灶                       |
| F23 | AI 调用日志           | 耗时、成功失败；Key 环境变量注入                         |

### 明确不做（第一版砍掉）

- Kafka 集群 / 多租户 / 权限 RBAC 大而全
- 复杂 DAG、可视化流程编辑器、Camunda 级引擎
- Multi-Agent、长期记忆、向量库
- K8s、分库分表、链路追踪全家桶

## 主要接口（建议）

### 认证

| 方法   | 路径                   | 说明 |
|------|----------------------|----|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login`    | 登录 |

### 任务

| 方法   | 路径                      | 说明                     |
|------|-------------------------|------------------------|
| POST | `/api/tasks`            | 创建任务，返回 taskId         |
| GET  | `/api/tasks`            | 当前用户任务列表               |
| GET  | `/api/tasks/{id}`       | 任务详情（含状态、重试次数、错误信息、结果） |
| POST | `/api/tasks/{id}/retry` | 手动重试（P1）               |

### 工作流（P1，可选）

| 方法   | 路径                    | 说明           |
|------|-----------------------|--------------|
| POST | `/api/workflows`      | 创建顺序工作流定义或实例 |
| GET  | `/api/workflows/{id}` | 查询工作流实例与步骤状态 |

所有需登录接口从安全上下文取 userId，禁止信任前端传入的 userId。

## 数据库（最小表）

| 表名            | 说明                                                                   |
|---------------|----------------------------------------------------------------------|
| `user`        | 用户                                                                   |
| `task`        | 任务：type、payload、status、retry_count、error_message、result_json、user_id |
| `task_step`   | P1：工作流步骤（可选）                                                         |
| `ai_call_log` | P2：可选                                                                |

具体 DDL 放在 `database/sql/schema.sql`。

## RabbitMQ 设计

推荐：

```text
exchange: task.exchange (topic 或 direct)
queue: task.execute.q
routingKey: task.execute
queue: task.execute.dlq          -- 死信队列
```

要求：

- 消息体至少包含 `taskId`
- 消费者手动 ACK（或等价可靠语义）
- 业务失败与系统异常的重试策略明确（本地重试 / 重新入队 / 进 DLQ）
- DLQ 消费或管理接口能让演示者看到「死信中的任务」

## Redis（可选）

```text
idempotent:task:{taskId}     -- 消费幂等
rate:task:create:{userId}    -- 创建限流
```

若第一版不用 Redis，幂等必须用 DB 唯一约束或状态条件更新实现，并在文档中写明。

## Agent 扩展约束（P2）

- API Key 仅环境变量：`AI_BASE_URL`、`AI_API_KEY`、`AI_MODEL`
- 工具数量控制在 2～3 个，实现要真能调用
- Agent 失败必须走与普通任务相同的重试/死信路径
- 禁止在日志中打印完整密钥与大段用户隐私数据
- 不要把未校验的模型原始输出直接当作成功业务结果

## 统一响应

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

Controller 不直接返回 Entity，使用 DTO/VO。

## 安全与隐私

- 任务查询必须带当前用户条件
- 不在日志输出 JWT 全文、密码、API Key
- `HTTP_CALL` 需限制可访问地址（至少禁止明显的内网探测滥用；文档说明演示用途）
- `.env` / 含密钥的 compose 不提交 Git

## 开发节奏

### 第 1 阶段（骨架）

完成全部 P0，能用 curl/Swagger 演示：创建 → 队列执行 → 成功；强制失败 → 重试 → 死信。

### 第 2 阶段（加分）

P1 中选做：手动重试 + 简单前端，或轻量顺序工作流（二选一优先做透，优于两个都做一半）。

### 第 3 阶段（可选）

P2 Agent 工具任务接入同一套 Worker。

## 简历表述方向（完成后按实际裁剪）

```text
基于 Spring Boot 3 实现异步任务平台：
任务提交后经 RabbitMQ 投递并由 Worker 消费执行，支持状态机流转、失败重试、死信队列与消费幂等；
使用 MySQL 持久化任务元数据，Docker Compose 编排中间件与服务。
可扩展顺序工作流步骤，以及基于 LLM 的工具调用任务类型，使 Agent 执行纳入统一的异步可靠性机制。
```

未完成的能力不要写入简历。

## AI 编码约束

1. 不要用线程池/`@Async` 冒充本项目的主异步方案。
2. 不要跳过鉴权与用户数据隔离。
3. 不要在 P0 未完成时优先堆 Agent / 前端美化。
4. 不要引入与本文件冲突的重型工作流引擎（除非用户明确要求）。
5. 不要把业务逻辑写在 Controller。
6. 不要提交密钥与真实 `.env`。
7. 新增任务类型必须通过策略/执行器扩展，避免巨型 switch 无法维护。
8. 修改 MQ 语义时必须同步更新 README 与本文件中的状态流转说明。