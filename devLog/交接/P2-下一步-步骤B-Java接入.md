# P2 交接：下一步做步骤 B（Java 接入）

> **已完成（2026-08-18）。** 下一轮请改读 [P2-下一步-步骤C-真Agent.md](P2-下一步-步骤C-真Agent.md)。  
> 本文保留作步骤 B 的进度与坑，**不是第二套规格**。  
> 动手前必须先读 skill，以 skill / rule 为准。

## 0. 下一轮 Agent 开场必做

1. 读 [implement-p2-agent](../../.cursor/skills/implement-p2-agent/SKILL.md)（A→D 硬顺序）。
2. 读 [extend-task-executor](../../.cursor/skills/extend-task-executor/SKILL.md)（步骤 B 的操作清单）。
3. 读 [a2a-contract.md](../../.cursor/skills/python-a2a-mcp/a2a-contract.md) 与 [result-schema.md](../../.cursor/skills/python-a2a-mcp/result-schema.md)。
4. **只做步骤 B。** 不要开始 LangGraph / MCP（C），不要改控制台表单（D），不要重做 Python 骨架（A）。

## 1. 当前进度（事实）

| 步骤 | 状态 | 说明 |
|------|------|------|
| A Python 骨架 | **已完成** | `agent/` 可独立运行；固定成功 JSON + `forceFail` |
| B Java 接入 | **未开始** | 无 `AGENT_TASK`、无 A2A Client、执行仍在长大事务里 |
| C 真 Agent | 未开始 | `graph.py` / `llm.py` / `mcp_server/tools.py` 仍是 stub / 空 |
| D 控制台 + 三条演示 | 未开始 | `frontend` 的 `TaskType` 仍只有 `HTTP_CALL` \| `DELAY_DEMO` |

F20–F29 对照：

- [x] F22 Python：`GET /health` + Agent Card + JSON-RPC（见下文 method 名）
- [x] F27 Compose `agent` 服务 + 示例里 `AGENT_BASE_URL=http://agent:8081`（Java 配置尚未读取）
- [ ] F20 `TaskType.AGENT_TASK`
- [ ] F21 `AgentTaskExecutor`
- [ ] F25 Java 侧校验结构化结果（步骤 B 就要做；C 再换真结果）
- [ ] F26 超时 / `forceFail` 走现有重试与 DLQ（步骤 B 用 stub 的 `forceFail` 跑通即可）
- [ ] F23 / F24 LangGraph + MCP → **步骤 C**
- [ ] F28 / F29 控制台与 README 三条演示 → **步骤 D**

P0（JWT、任务 CRUD、手动 ACK、`claimForExecution`、`HTTP_CALL` / `DELAY_DEMO`、DLQ）保持不动。

## 2. 步骤 A 不要重做（已落地）

骨架说明：[Python-Agent骨架搭建.md](../环境配置/Python-Agent骨架搭建.md)。

- 目录：`agent/`（`pyproject.toml`、`Dockerfile`、`app/main.py`、`app/a2a_server.py`、stub `graph.py`）
- Compose 示例：[deploy/docker-compose.yml.example](../../deploy/docker-compose.yml.example) 已有 `agent`；backend 已注入 `AGENT_BASE_URL` / `depends_on: agent healthy`
- 环境变量占位：[deploy/.env.example](../../deploy/.env.example)
- `.gitignore` 已忽略 `agent/.env`、`agent/.venv`、`__pycache__`、`*.egg-info`

**A2A 实际信封（必须按这个写 Java，不要抄 skill 里过时的 `message/send`）：**

| 项 | 值 |
|----|-----|
| Card | `GET {AGENT_BASE_URL}/.well-known/agent-card.json` |
| JSON-RPC | `POST {AGENT_BASE_URL}/` |
| method | **`SendMessage`**（a2a-sdk 1.x / A2A 1.0） |
| 头 | **`A2A-Version: 1.0`**、`Content-Type: application/json` |
| 用户文本 | JSON 字符串：`{"taskId":123,"instruction":"…","forceFail":false}` |

`extend-task-executor` 第 5 步仍写「发 `message/send`」。那是 v0.3 旧名。以 [a2a-contract.md](../../.cursor/skills/python-a2a-mcp/a2a-contract.md) 为准。若 Java 实现对通后仍想改 skill 用词，**先改 skill 再改代码**；不要把主协议改成 `/api/agent/run`。

Python stub 成功体（步骤 B 联调期望）：

```json
{
  "summary": "stub: skipped LLM; instruction accepted for Java wiring.",
  "finalAnswer": "stub success",
  "toolCalls": [],
  "durationMs": 0
}
```

`forceFail=true` → A2A `TASK_STATE_FAILED`。Java 视为 `execute()` 抛错。

SDK 成功响应里 JSON 在 **artifact `result_json` 的 text** 和 **status.message 的 text** 两处都有。Client 抽出文本即可，不要在 Client 里写摘要逻辑。

**步骤 B 不要拿 [demos.md](../../.cursor/skills/implement-p2-agent/demos.md) 第 2 条当验收。** 那条要求 `toolCalls` 含 `http_get`，属于步骤 C 之后。步骤 B 成功路径是 stub：`toolCalls` 为空数组也合法（result-schema 允许）。

## 3. 步骤 B 要做什么

按 [extend-task-executor](../../.cursor/skills/extend-task-executor/SKILL.md) 逐项。包根 `com.phrolova.asyncforge`，不新起 Maven 模块，`pom.xml` **不加** LLM SDK。

### 3.1 类型与创建校验

- [`TaskType.java`](../../backend/src/main/java/com/phrolova/asyncforge/entity/TaskType.java) 增加 `AGENT_TASK`（当前只有 `HTTP_CALL`、`DELAY_DEMO`）。
- 不改 [`database/sql/schema.sql`](../../database/sql/schema.sql)（`task_type` 已是 `VARCHAR(32)`）。
- [`TaskServiceImpl`](../../backend/src/main/java/com/phrolova/asyncforge/service/impl/TaskServiceImpl.java)：创建时校验 `instruction` trim 后非空、建议 ≤2000，否则 400。`forceFail` 可选，默认 false。`validateTaskType` 跟枚举走即可。
- **不要**在 Java 里解析用户 instruction 里的 URL。

### 3.2 拆事务（必做，A2A 会很慢）

当前 [`TaskExecutionService.execute`](../../backend/src/main/java/com/phrolova/asyncforge/service/TaskExecutionService.java) 整个方法 `@Transactional`，内部调用 `executor.execute`（HTTP 已在事务里，A2A 会更糟）。

目标：

1. 短事务：加载、跳过终态、`claimForExecution` → `RUNNING`
2. **无事务**：`taskExecutorRegistry.get(...).execute(task)`（A2A / HTTP）
3. 短事务：写 `SUCCESS` + `result_json`，或走现有 `handleFailure`
4. `handleFailure` 写回 `PENDING` 后必须 `publishAfterCommit`（语义已有，拆开后不要弄丢）
5. 不要改松 `claimForExecution`（仅 `PENDING` / `FAILED` → `RUNNING`）

可用 `TransactionTemplate` 或把 claim / 写结果拆成带 `@Transactional` 的短方法。Agent **必须**复用 `handleFailure`，禁止旁路重试。

### 3.3 新类

| 类 | 职责 |
|----|------|
| `agent/AgentProperties.java` | 绑定 `async-forge.agent.*` |
| `agent/AgentA2aClient.java` | 拉 Card → `SendMessage` → 超时 60s → 抽出 JSON；failed / 超时 / 非 2xx 抛错 |
| `worker/AgentTaskExecutor.java` | `taskType()` = `"AGENT_TASK"`；解析 payload；**`forceFail` 仍走 A2A**；校验 `summary` 非空且 `toolCalls` 为数组；**不**更新任务状态 |

Java `agent` 包 **只允许** 上述两个配置/客户端类。执行器放 `worker/`。

HTTP 客户端：现有 [`HttpCallExecutor`](../../backend/src/main/java/com/phrolova/asyncforge/worker/HttpCallExecutor.java) 用 JDK `HttpClient`。A2A Client 可同样用 JDK `HttpClient` 或 Spring 的，但：

- **禁止**把 Agent URL 交给 `HttpCallExecutor`（SSRF 会拦 Docker 名 `agent`）
- 超时读 `AGENT_TIMEOUT_SECONDS`（默认 60）
- 阻塞一次等最终 Task，不要 Java 侧轮询 + 流式

`TaskExecutorRegistry` 已按 `@Component` 收集实现，**不要**改成巨型 switch。勿改 `TaskExecutor` 接口语义；勿改 `HTTP_CALL` / `DELAY_DEMO` 演示语义。

### 3.4 配置

[`application.yml`](../../backend/src/main/resources/application.yml) 增加：

```yaml
async-forge:
  agent:
    base-url: ${AGENT_BASE_URL:http://localhost:8081}
    timeout-seconds: ${AGENT_TIMEOUT_SECONDS:60}
```

Compose 内 backend 必须 `AGENT_BASE_URL=http://agent:8081`（示例里已有，不要改成 localhost）。本地只起 Java、Agent 在宿主机时才用 `http://localhost:8081`。

## 4. 步骤 B 验收

Agent 与 Java 都要能连上（本机 Agent：`cd agent && .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8081`；或 Compose 起 `agent`）。

1. `POST /api/tasks`，`taskType=AGENT_TASK`，`forceFail=false` → 任务最终 **SUCCESS**，`result.summary` 非空，`result.toolCalls` 为数组（stub 为空数组）。
2. 同样类型 `forceFail=true` → 不在 Java 里直接失败；经 A2A failed → 重试 → 最终 **DEAD**，`errorMessage` 可查；RabbitMQ `task.execute.dlq` 有消息（默认 `max_retry=3`）。
3. 回归：`DELAY_DEMO` + `fail=true` 仍能重试至 DEAD；`HTTP_CALL` 成功语义不变。
4. 空 `instruction` 创建返回 400。

## 5. 明确不要做

- 用 `HTTP_CALL` 调 Agent；`@Async` / 线程池替换 MQ
- Python 连 MySQL / RabbitMQ；改 `schema.sql`
- 开始 LangGraph、MCP 两工具、去掉 stub（那是 C）
- 改 `ConsoleView.vue` / `types.ts` 加 `AGENT_TASK` 表单（那是 D，B 用 curl 即可）
- 第三个 MCP 工具、聊天窗、流式、`ai_call_log`、Kafka、K8s、工作流引擎、Redis 限流、向量库、Multi-Agent
- 简历 / JD / 面试能力（VitaeLens 互补红线）

## 6. 给人类：B 之后还有什么

- **C**：按 [python-a2a-mcp](../../.cursor/skills/python-a2a-mcp/SKILL.md) 接真 MCP（`http_get` + `calculator`）和 LangGraph 3～5 节点；去掉固定 JSON；httpbin 指令做成功演示。需要 `AI_*` 环境变量。
- **D**：控制台表单 + README 抄 [demos.md](../../.cursor/skills/implement-p2-agent/demos.md) 三条演示。

## 7. 人类上下文（下一轮不必再教 Python 入门）

维护者 Java 更熟、Python 较新。已理解：`dict`≈Map、`stub`=占位实现、`->` 是类型标注、`__init__.py` 可空、`.venv` / `__pycache__` / `egg-info` 是生成物。步骤 B 以 Java 为主，少讲 Python 语法，除非改 A2A Client 对不了协议。
