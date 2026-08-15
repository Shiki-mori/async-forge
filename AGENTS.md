# AGENTS.md

## 项目名称

async-forge

## 项目概览

本项目是一个面向校招作品集的 **异步任务平台**。

目标：用 Spring Boot 实现「提交任务 → RabbitMQ 可靠执行 → 状态可查 → 失败重试 / 死信」的完整链路，优先体现 Java 后端工程能力。

当前工作（P2）：在**不破坏现有 P0 链路**的前提下，嵌入一个独立的小型 Python Agent 服务。Agent 只作为一类 Worker 任务（`AGENT_TASK`）被调度；智能失败必须走与 `DELAY_DEMO` / `HTTP_CALL` 相同的重试与死信，而不是另起一套 AI 流程。

本项目是 **另一个 AI 简历分析业务系统（VitaeLens）的互补项目**。差异点必须落在：消息队列、消费语义、重试、死信、幂等，以及「把 Agent 执行纳入同一套可靠性机制」。禁止做成第二个简历 / 面试 App，禁止在 Agent 里分析简历或生成面试题。

## 当前实现状态（以代码为准）

已完成（不要回退、不要用 `@Async` 替换 MQ）：

- 注册 / 登录（JWT + BCrypt）
- 创建 / 查询任务（按当前用户隔离）
- RabbitMQ 投递与手动 ACK 消费
- 状态机：`PENDING → RUNNING → SUCCESS`；失败未耗尽重试则 `PENDING` 再入队，耗尽则 `DEAD`
- 消费幂等（`claimForExecution`）
- `HTTP_CALL`、`DELAY_DEMO` 执行器（策略注册表）
- DLQ 监听
- Vue 极简控制台、Swagger、Docker Compose

未实现（P2 要做）：

- `AGENT_TASK` 任务类型与 Java 执行器
- `agent/` Python 服务（FastAPI + A2A + LangGraph + MCP）
- Compose 增加 agent 容器
- 控制台提交 Agent 任务

明确仍不做（除非用户另提）：

- 手动重试 HTTP 接口、顺序工作流引擎、Redis 限流
- Kafka、K8s、分库分表、向量库、长期记忆、Multi-Agent 集群
- 用现有 `HTTP_CALL` 去打 Python Agent（该执行器会拦截内网 / localhost）

## 技术栈

Java 后端（已有，保持）：

- Java 17、Spring Boot 3.5.15
- Spring Security + JWT
- MyBatis-Plus、MySQL 8、RabbitMQ
- springDoc OpenAPI、Docker Compose

前端（已有，P2 只加表单）：

- Vue 3 + TypeScript + Vite

Python Agent（P2 新增）：

- Python 3.12
- FastAPI + Uvicorn
- 官方 A2A Python SDK（`a2a-sdk`）作服务端
- LangGraph 编排循环
- LangChain 仅作 OpenAI 兼容 Chat 模型与工具绑定（不要再包一层业务 Chain）
- MCP：真实 Tool Server（`mcp` 或 FastMCP），至少 2 个工具
- httpx、Pydantic Settings
- 模型：OpenAI 兼容 Chat Completions（`AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL`）

## 设计原则

1. **先可靠异步，后智能**：禁止改写 P0 主链路来迁就 Agent。
2. **Java 管可靠性，Python 管智能**：Spring 负责鉴权、入库、MQ、抢占、重试、死信；Python 负责规划与工具调用。
3. **Agent 是任务类型，不是新平台**：不新增第二套任务表、不新增第二套状态机。
4. **调用外部 AI / HTTP / A2A 时不得长时间持有 DB 事务**。现有 `TaskExecutionService.execute` 若带 `@Transactional` 覆盖远程调用，实现 `AGENT_TASK` 时必须把「抢占 / 写结果」与「A2A 调用」拆开。
5. **密钥不入库、不进 Git**。
6. **窄而硬**：工具 2 个、图 3～5 个节点、一种 A2A 同步调用。禁止为凑技术栈再拆多个 Agent 进程。
7. **不要把未校验的模型原文当作 SUCCESS 业务结果**。

## 仓库结构（P2 目标）

```text
async-forge/
├── AGENTS.md
├── README.md
├── deploy/
│   ├── docker-compose.yml
│   ├── docker-compose.yml.example
│   └── .env.example
├── database/sql/schema.sql          -- P2 不改表结构
├── backend/                         -- Spring Boot（已有）
├── frontend/                        -- Vue 控制台（已有）
└── agent/                           -- P2 新增 Python Agent Runtime
    ├── Dockerfile
    ├── pyproject.toml               -- 或 requirements.txt，二选一
    ├── .env.example
    └── app/
        ├── __init__.py
        ├── main.py                  -- FastAPI：/health + 挂载 A2A
        ├── config.py                -- 环境变量
        ├── schemas.py               -- 指令 / 结果 Pydantic 模型
        ├── a2a_server.py            -- Agent Card + message/send 处理器
        ├── graph.py                 -- LangGraph
        ├── llm.py                   -- ChatOpenAI 兼容客户端
        └── mcp_server/
            ├── __init__.py
            └── tools.py             -- http_get、calculator
```

Java 包（在现有分层上增量，不要新起模块工程）：

```text
com.phrolova.asyncforge/
├── worker/
│   ├── TaskExecutor.java            -- 已有，勿改接口语义
│   ├── TaskExecutorRegistry.java    -- 已有
│   ├── HttpCallExecutor.java        -- 已有，勿用来调 Agent
│   ├── DelayDemoExecutor.java       -- 已有
│   └── AgentTaskExecutor.java       -- P2：读 payload，调 A2A Client
├── agent/                           -- P2：仅 Java 侧 A2A 客户端与配置
│   ├── AgentProperties.java
│   └── AgentA2aClient.java
└── entity/TaskType.java             -- 增加 AGENT_TASK
```

禁止：把 LLM SDK 加进 Java；在 Python 里直连 MySQL / RabbitMQ；让前端直连 Agent。

## 核心领域模型

### 任务状态（不变）

```text
PENDING   -- 已入库，等待投递或等待再次消费
RUNNING   -- Worker 已抢占，执行中
SUCCESS   -- 成功（终态）
FAILED    -- 文档语义：失败但仍可能重试；当前实现失败回写多为 PENDING 或 DEAD
DEAD      -- 重试耗尽后的终态
```

流转（与现实现一致，Agent 必须复用）：

```text
创建 → PENDING
  →（投递 MQ）→ claim RUNNING
  → SUCCESS
  → 失败且 retry_count < max_retry → PENDING（重新入队）
  → 失败且 retry_count ≥ max_retry → DEAD
```

### 任务类型

| taskType     | 行为 |
|--------------|------|
| `HTTP_CALL`  | 按 payload 对外 GET；SSRF 限制；记录状态码与响应摘要 |
| `DELAY_DEMO` | 睡眠 N 秒；`fail=true` 时抛错，用于演示重试与死信 |
| `AGENT_TASK` | 把自然语言指令交给 Python Agent；Agent 经 Function Calling / MCP 调工具；结构化结果写入 `result_json`；失败走同一套重试 / DLQ |

旧文档中的 `AGENT_TOOL` 名称废弃，统一用 `AGENT_TASK`。

## Agent 是什么、做什么（给后续实现者）

### 一句话

用户提交一句**自然语言指令**（例如「GET 某个公网 URL，摘要状态码和关键字段」）。Java 只负责排队与可靠性；Python Agent 理解指令、按需调用 MCP 工具、返回结构化摘要。

### 用户可见行为

1. 登录控制台，选择 `AGENT_TASK`。
2. 填写 `instruction`，可选勾选 `forceFail`（不调模型、直接失败，用于演示死信）。
3. 立即得到 `taskId`，任务进入队列。
4. 成功：详情里 `result` 含 `summary` 与 `toolCalls`。
5. `forceFail=true` 或 Agent / 工具超时：与 `DELAY_DEMO` 一样重试，耗尽后 `DEAD`。

### 非目标（Agent 禁止做）

- 简历解析、JD 匹配、模拟面试、聊天机器人 UI
- 任意代码执行、访问本机文件、扫描内网
- 多 Agent 互调、向量检索、记忆、用户登录

### 端到端时序

```text
浏览器 / curl
  → POST /api/tasks  { taskType: AGENT_TASK, payload: { instruction, forceFail } }
  → Spring：校验类型与 payload → 入库 PENDING → 事务提交后投递 RabbitMQ → 立即返回 taskId
  → TaskConsumer 拉取
  → TaskExecutionService：claim RUNNING（短事务）
  → AgentTaskExecutor：A2A Client 调用 Python（无 DB 事务）
  → Python：
        forceFail → 以失败结束 A2A Task
        否则 LangGraph：reason(Function Calling) ⇄ MCP tools → finalize
  → Java：校验结果 JSON → 短事务写 SUCCESS + result_json
  → 任何抛错 / 超时 / 校验失败 → 现有 handleFailure（再入队或 DEAD）
```

主叙事（简历可讲）：**A2A 是跨语言契约；LangGraph 是工具循环；MCP 是工具协议；失败进同一套 DLQ。** LangChain 只是模型适配，不要写成主线。

## 功能清单

### P0 / P1（已完成，仅作约束）

F01–F13、F16、F19 已存在。实现 P2 时不要重写这些行为。

### P2 — 本次必须交付

| ID  | 功能 | 说明 |
|-----|------|------|
| F20 | `AGENT_TASK` 类型 | 加入 `TaskType`；`TaskServiceImpl.validateTaskType` 自动放开；表结构不改 |
| F21 | `AgentTaskExecutor` | 新 `@Component` 实现 `TaskExecutor`；禁止改成巨型 switch |
| F22 | Python Agent Runtime | FastAPI 进程；`GET /health`；A2A Agent Card + `message/send` |
| F23 | LangGraph | 3～5 节点；Function Calling 循环；最多 5 轮工具；超时可配置 |
| F24 | MCP 工具 ≥ 2 | `http_get`、`calculator`；必须走 MCP 协议，禁止只写同名 Python 函数冒充 |
| F25 | 结构化结果 | 见下方 schema；缺字段视为失败 |
| F26 | 超时与失败 | Java 侧 A2A 超时；Python 侧工具超时；`forceFail`；均进入现有重试 / DLQ |
| F27 | Compose | 增加 `agent` 服务；backend 注入 `AGENT_BASE_URL=http://agent:8081` |
| F28 | 控制台 | 可提交 `AGENT_TASK` 并展示 result / error |
| F29 | README | 补充 Agent 启动、环境变量、三条演示（见下） |

### 明确不做

- 用 `HTTP_CALL` 调用 Agent
- Java 实现工具循环 / Function Calling
- Python 消费 RabbitMQ 或写 `task` 表
- 第三个工具、Web UI 对话窗、流式打字效果
- 为 Agent 新建 `ai_call_log` 表（耗时写入 `result_json.durationMs` 与日志即可）

## 主要接口

认证与任务 HTTP 接口不变：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/tasks` | 创建任务，立即返回 taskId |
| GET | `/api/tasks` | 当前用户任务列表 |
| GET | `/api/tasks/{id}` | 详情（含 status、retryCount、errorMessage、result） |

所有需登录接口从 `UserContext` 取 userId，禁止信任前端传入的 userId。

### `AGENT_TASK` payload（创建任务）

```json
{
  "instruction": "GET https://httpbin.org/json，摘要 HTTP 状态码和 JSON 的顶层字段名。失败则说明原因。",
  "forceFail": false
}
```

校验：

- `instruction` 必填，字符串，trim 后非空，建议最长 2000
- `forceFail` 可选，默认 `false`
- 不要在 Java 里解析用户 URL；是否调 `http_get` 由 Agent 决定

### `AGENT_TASK` 成功时 `result_json`

```json
{
  "summary": "请求成功，状态码 200，顶层字段包括 slideshow。",
  "finalAnswer": "状态码 200；顶层字段：slideshow。",
  "toolCalls": [
    {
      "name": "http_get",
      "input": { "url": "https://httpbin.org/json" },
      "ok": true,
      "outputSnippet": "{\"slideshow\": ...}"
    }
  ],
  "durationMs": 1840
}
```

Java 校验：必须存在非空 `summary`，且 `toolCalls` 为数组（允许空数组，例如指令无需工具）。缺一不可写 SUCCESS。

### Python 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 返回 `{"status":"ok"}`，供 Compose healthcheck |

## A2A 契约（Java Client ↔ Python Server）

Python **必须**用官方 `a2a-sdk` 作为 A2A Server（JSON-RPC over HTTP），不要发明 `/api/agent/run` 作为主协议。

最低要求：

1. `GET /.well-known/agent-card.json`  
   - name：`async-forge-agent`  
   - 描述：执行自然语言指令，可通过 MCP 工具访问受限 HTTP GET 与四则运算  
   - skill：`run-instruction`  
   - 标明 JSON-RPC 接口 URL（容器内 `http://agent:8081`）
2. JSON-RPC `message/send`（若已安装 SDK 使用 `SendMessage` 且要求 `A2A-Version` 头，Java 客户端必须与 SDK 示例一致；以能对通 SDK 为准，并在 README 写明实际 method 名与头）
3. **阻塞一次调用**：Java Worker 同步等待最终 Task（不要在 P2 做 Java 侧轮询 + 流式）。超时默认 60 秒。
4. 用户 Message 的文本 part 必须是 JSON 字符串（便于 Java/Python 对齐）：

```json
{
  "taskId": 123,
  "instruction": "……",
  "forceFail": false
}
```

5. 成功：A2A Task completed；artifact 或最终 message 的文本为上面的 `result_json`
6. 失败：A2A Task failed（含 `forceFail`、工具致命错误、模型不可用、结果校验失败）。Java 将其视为 `execute()` 抛错
7. Java **不要**把 Agent 的 URL 交给 `HttpCallExecutor`（SSRF 黑名单会拦 Docker DNS 名 `agent`）

`AgentA2aClient` 职责：拉 Card → 发 message → 设超时 → 抽出 JSON。不要在 Client 里写业务摘要逻辑。

## LangGraph（Python）

状态至少包含：`instruction`、`force_fail`、`messages`、`tool_calls`、`summary`、`error`。

节点（3～5 个，不要更多）：

```text
start
  → fail_fast        -- force_fail=true 则置 error 并结束
  → reason           -- LLM + 已绑定 MCP 工具（Function Calling）
  → tools            -- 执行工具，写 tool_calls
  → reason           -- 循环，最多 5 轮
  → finalize         -- 产出 schema；再校验，失败则 error
```

约束：

- 使用 LangGraph 的条件边实现循环，不要手写 `while True` 调模型。
- Function Calling 必须真实发生：模型通过 tool call 选 `http_get` / `calculator`，而不是把 URL 写进纯文本让 Python 正则捞。
- `finalize` 不得把模型原始长文本直接当 `summary`；需整理成短摘要。
- 日志可记耗时与工具名；禁止打印 API Key、完整 Authorization 头、超长响应体。

## MCP 工具

同一 `agent` 进程内提供 MCP Server（stdio 子进程或 in-process 均可）。LangGraph 必须经 MCP Client 调工具（可用 `langchain-mcp-adapters`）。禁止「MCP 目录里只放普通函数、图却直接调这些函数」。

### `http_get`

参数：`url`（string）

行为：仅 HTTP/HTTPS GET；超时默认 10s；响应截断 1024 字符；返回 `{statusCode, bodySnippet}`。

SSRF（必须，与 Java `HttpCallExecutor` 同级）：

- 只允许 `http` / `https`
- 拒绝无 host
- 拒绝 `localhost`、`*.local`、loopback、link-local、site-local、any-local
- 拒绝云 metadata：`169.254.169.254`、`metadata.google.internal`

P2 **不要**把 `http_get` 的白名单放宽到 Docker 内网；Agent 自身由 Java 经 Compose 网络调用，与工具出站不是同一通道。

### `calculator`

参数：`expression`（string，仅数字与 `+ - * / ( ) .` 与空白）

行为：计算并返回数字字符串。非法字符必须报错。禁止 `eval` 任意 Python。

## Java 执行器要点

`AgentTaskExecutor.taskType()` 返回 `"AGENT_TASK"`。

`execute(Task)`：

1. 解析 payload；`instruction` 为空则抛业务异常（会进重试，故创建接口就应 400；执行器作为第二道闸）
2. `forceFail` 仍要走 A2A（让 Python 失败），以便演示「Agent 失败 = 平台失败」，不要在 Java 里直接 `throw` 绕过 Agent
3. 调用 `AgentA2aClient`；超时抛错
4. 校验返回 JSON，通过则 `toString` 作为 `result_json`
5. 不在此方法里更新任务状态（仍由 `TaskExecutionService` 统一处理）

配置（`application.yml` + 环境变量）：

```yaml
async-forge:
  agent:
    base-url: ${AGENT_BASE_URL:http://localhost:8081}
    timeout-seconds: ${AGENT_TIMEOUT_SECONDS:60}
```

Compose 中 backend 必须设置 `AGENT_BASE_URL=http://agent:8081`。本地只起 Java、Agent 在宿主机时才用 localhost。

创建任务校验：未知 `taskType` 仍返回现有 `TASK_TYPE_UNSUPPORTED`。可在 `CreateTaskRequest` 或 service 对 `AGENT_TASK` 做 instruction 校验，避免空指令入队。

## Docker Compose

在 `deploy/docker-compose.yml` 与 `.example` 增加 `agent` 服务：

- build context：`../agent`
- container_name：`async-forge-agent`
- 端口：`${AGENT_PORT:-8081}:8081`
- 环境变量：`AI_BASE_URL`、`AI_API_KEY`、`AI_MODEL`、`AGENT_TIMEOUT_SECONDS`
- healthcheck：`GET /health`
- 网络：现有 `async-forge`
- backend `depends_on` agent healthy，并传入 `AGENT_BASE_URL`

`.env.example` 增加上述变量占位，**不要**提交真实 Key。现有 `deploy/.env` 若含密钥，修改 compose 时不要把密钥写进示例文件。

Agent 镜像：Python 3.12-slim；非 root 用户更佳但非必须；`CMD` 用 uvicorn 听 `0.0.0.0:8081`。

## 前端

`frontend/src/types.ts` 的 `TaskType` 增加 `'AGENT_TASK'`。

`ConsoleView.vue`：

- 下拉增加 `AGENT_TASK`
- 表单：多行 `instruction`（给一条 httpbin 默认示例）、`forceFail` 复选框
- 详情区沿用现有 payload / result / error，无需新页面

## 数据库

P2 **不改** `database/sql/schema.sql`。`task_type` 已是 `VARCHAR(32)`。

不要为跑通 Agent 去加 `task_step` / `ai_call_log`。

## RabbitMQ / Redis

不变。Agent 流量仍是「一条 `taskId` 消息」。不要让 Python 连 RabbitMQ。

Redis 仍未使用；P2 不要借机引入。

## 环境变量

| 变量 | 谁用 | 说明 |
|------|------|------|
| `AI_BASE_URL` | Python | OpenAI 兼容基址，不要硬编码 `/v1/chat/completions` 以外的私有协议 |
| `AI_API_KEY` | Python | 禁止写进代码 / 镜像层 |
| `AI_MODEL` | Python | 如 `deepseek-chat` |
| `AGENT_BASE_URL` | Java | Agent 根 URL |
| `AGENT_TIMEOUT_SECONDS` | Java（及可选 Python） | 默认 60 |
| `AGENT_PORT` | Compose | 默认 8081 |

本地跑 Agent：`agent/.env` 从 `.env.example` 复制，且 gitignore。

## 安全与隐私

- 任务查询必须带当前用户条件
- 不在日志输出 JWT 全文、密码、API Key
- `HTTP_CALL` 与 MCP `http_get` 都必须有 SSRF 限制
- Agent 指令可能含用户自写文本：日志只打 `taskId` 与截断后的 instruction（建议 ≤ 200 字）
- `.env` / 含真实密钥的 compose 不提交 Git

## 演示（README 必须可抄）

假定已登录并持有 token。默认 `max_retry=3`。

### 1. 平台可靠性（已有）

`DELAY_DEMO` + `"fail": true` → 观察重试次数增加 → `DEAD` → RabbitMQ 管理台看到 `task.execute.dlq`。

### 2. Agent 成功

```json
{
  "taskType": "AGENT_TASK",
  "payload": {
    "instruction": "GET https://httpbin.org/json ，告诉我 HTTP 状态码以及返回 JSON 的顶层字段名。",
    "forceFail": false
  }
}
```

期望：`SUCCESS`；`result.toolCalls` 含 `http_get`；`summary` 非空。

### 3. Agent 失败进死信

```json
{
  "taskType": "AGENT_TASK",
  "payload": {
    "instruction": "任意内容",
    "forceFail": true
  }
}
```

期望：不调用工具成功路径；任务最终 `DEAD`；`errorMessage` 可展示。用于证明 Agent 没有旁路重试策略。

可选加分（有时间再做，不做也不算 P2 失败）：指令要求计算简单算术，result 中出现 `calculator`。

## 开发节奏（P2 交付顺序）

后续代码模型必须按顺序做，每步可运行后再做下一步。不要先画 Multi-Agent。

### 步骤 A — Python 骨架

- `agent/` 包结构、依赖、Dockerfile、`GET /health`
- A2A Agent Card 能 curl 通
- `forceFail=true` 的 `message/send` 返回 failed
- `forceFail=false` 可先返回固定 JSON（无 LLM），用于 Java 联调

### 步骤 B — Java 接入

- `TaskType.AGENT_TASK`、`AgentProperties`、`AgentA2aClient`、`AgentTaskExecutor`
- 拆开远程调用与 DB 事务
- Compose：agent 服务 + backend 环境变量
- 用步骤 A 的固定成功 / `forceFail` 跑通 SUCCESS 与 DEAD

### 步骤 C — 真 Agent

- MCP `http_get` + `calculator`（含 SSRF 与表达式白名单）
- LangGraph + Function Calling 接到 MCP
- 去掉固定成功 stub；校验 schema
- 用 httpbin 指令做成功演示

### 步骤 D — 控制台与文档

- 前端表单
- README：启动、环境变量、三条演示、架构（Java 可靠性 × Python 智能）
- 同步本文件：若 method 名 / 端口与本文不一致，改文档而不是留两套说法

## 简历表述方向（完成后按实际裁剪）

```text
基于 Spring Boot 3 实现异步任务平台：任务经 RabbitMQ 投递并由 Worker 消费，
支持状态机、失败重试、死信队列与消费幂等。
扩展 AGENT_TASK：Java Worker 通过 A2A 调用独立的 Python Agent Runtime
（FastAPI + LangGraph），经 Function Calling / MCP 执行受限 HTTP 与计算工具，
结果回写任务表；工具超时与模型失败走与普通任务相同的重试和死信。
```

未完成的能力不要写入简历。不要把 VitaeLens 的简历分析写进本项目简历条目。

## 统一响应（Java API，不变）

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

Controller 不直接返回 Entity。

## AI 编码约束

1. 不要用线程池 / `@Async` 冒充本项目的主异步方案，也不要让 Python 取代 MQ。
2. 不要跳过鉴权与用户数据隔离。
3. 不要修改 `HTTP_CALL` / `DELAY_DEMO` 的成功演示语义来「顺便」接 Agent。
4. 不要把业务逻辑写在 Controller；不要在 Python 里写第二套用户体系。
5. 不要提交密钥与真实 `.env`。
6. 新增任务类型必须通过 `TaskExecutor` 注册表扩展。
7. 修改 MQ 语义时必须同步更新 README 与本文件。
8. 不要引入 Camunda / Celery / Kafka「更完整」。
9. 不要用正则从模型文本里抠 URL 代替 Function Calling。
10. 不要为通过演示而关闭 `http_get` 的 SSRF 限制。
11. 实现时若必须偏离本文（例如 A2A SDK 方法名变更），先改本文件再改代码，保持单一事实来源。
12. 不要把未规划的前端美化、工作流引擎、Redis 限流塞进同一次 Agent 交付。
