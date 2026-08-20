# P2 交接：步骤 D 已完成（控制台 + README）

> 给后续 Agent 与人类。本文是进度，**不是第二套规格**。规格以 skill / rule 为准。

## 1. 当前进度（事实）

| 步骤 | 状态 | 说明 |
|------|------|------|
| A Python 骨架 | **已完成** | `/health` + Agent Card + `SendMessage` |
| B Java 接入 | **已完成** | `AGENT_TASK`、拆事务、`AgentA2aClient`（`SendMessage` + HTTP/1.1） |
| C 真 Agent | **已完成** | LangGraph `fail_fast` → `reason` ⇄ `tools` → `finalize`；MCP `http_get` + `calculator` |
| D 控制台 + 三条演示 | **已完成** | `types.ts` + `ConsoleView.vue`；README 架构与三条可抄演示 |

F20–F29 已勾完。验证清单：[P2-步骤D.md](../验证/P2-步骤D.md)

## 2. 步骤 D 落地内容

1. `frontend/src/types.ts`：`TaskType` 含 `'AGENT_TASK'`
2. `ConsoleView.vue`：下拉 `AGENT_TASK`；多行 `instruction`（默认 httpbin）+ `forceFail`；payload `{ instruction, forceFail }`
3. 详情仍用 Payload / Result / Error。API 仍走 `/api/tasks`，浏览器不直连 Agent
4. README：启动、环境变量、三条演示、架构（Java 可靠性 × Python 智能）；已去掉固定 stub / 「无需 API Key」过时表述

## 3. 明确不要做

- 重做 Java 执行器（B）或 LangGraph / MCP（C）
- 第三个 MCP 工具、聊天窗、流式、`ai_call_log`、向量库、Multi-Agent
- 用 `HTTP_CALL` 打 Agent；改 `schema.sql`；Python 连 MySQL / RabbitMQ
