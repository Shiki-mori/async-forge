# P2 交接：下一步做步骤 D（控制台 + README）

> 给**下一轮对话的 Agent** 与人类。本文是进度与坑，**不是第二套规格**。  
> 动手前必须先读 skill，以 skill / rule 为准。

## 0. 下一轮 Agent 开场必做

1. 读 [implement-p2-agent](../../.cursor/skills/implement-p2-agent/SKILL.md)（A→D 硬顺序）。
2. 读 [frontend-console.mdc](../../.cursor/rules/frontend-console.mdc) 与 [demos.md](../../.cursor/skills/implement-p2-agent/demos.md)。
3. **只做步骤 D。** 不要重做 Java 执行器（B），不要改 LangGraph / MCP（C）。

## 1. 当前进度（事实）

| 步骤 | 状态 | 说明 |
|------|------|------|
| A Python 骨架 | **已完成** | `/health` + Agent Card + `SendMessage` |
| B Java 接入 | **已完成** | `AGENT_TASK`、拆事务、`AgentA2aClient`（`SendMessage` + HTTP/1.1） |
| C 真 Agent | **已完成** | LangGraph `fail_fast` → `reason` ⇄ `tools` → `finalize`；MCP `http_get` + `calculator` |
| D 控制台 + 三条演示 | **未开始** | `frontend` 的 `TaskType` 仍只有 `HTTP_CALL` \| `DELAY_DEMO` |

F20–F29 对照：

- [x] F20–F27（含 F23 LangGraph、F24 MCP）
- [ ] F28 控制台可提交并展示 result / error → **本步**
- [ ] F29 README：启动、环境变量、三条演示 → **本步**

## 2. 步骤 C 不要重做（已落地）

- MCP：`python -m app.mcp_server`（stdio），图经 `langchain-mcp-adapters` 调工具，禁止图直接 import `http_get` / `calculator`
- 节点：`fail_fast` → `reason` ⇄ `tools`（最多 5 轮）→ `finalize`；已去掉固定成功 stub
- `forceFail=true` 仍走 A2A Task failed，不经过 LLM / MCP
- 成功 `result_json`：`summary` 非空、`toolCalls` 为数组；httpbin 指令应含 `http_get`
- 需要 `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL`（OpenAI 兼容）。密钥不入库、不进 Git
- 本机 nginx 占用 8081 时，`deploy/.env` 里 `AGENT_PORT=8082`；Compose 内 Java 仍用 `http://agent:8081`
- 改完 Python 后须 **重建 agent 镜像**：`docker compose build agent && docker compose up -d agent`
- 验证清单：[P2-步骤C.md](../验证/P2-步骤C.md)

## 3. 步骤 D 要做什么

按 [implement-p2-agent](../../.cursor/skills/implement-p2-agent/SKILL.md) 步骤 D 与 [frontend-console.mdc](../../.cursor/rules/frontend-console.mdc)：

1. `frontend/src/types.ts`：`TaskType` 增加 `'AGENT_TASK'`
2. `ConsoleView.vue`：下拉增加 `AGENT_TASK`；多行 `instruction`（默认 httpbin 示例）+ `forceFail` 复选框；payload `{ instruction, forceFail }`
3. 详情继续用现有 Payload / Result / Error 面板。不新页面、不对话 UI、不流式、禁止浏览器直连 Agent
4. README：启动、环境变量、[demos.md](../../.cursor/skills/implement-p2-agent/demos.md) 三条演示、架构（Java 可靠性 × Python 智能）
5. 删掉 README 里过时的「固定 stub / 无需 API Key」表述（C 之后已不成立）

## 4. 步骤 D 验收

1. 控制台能提交 `AGENT_TASK`，详情能看到 `result.summary` / `toolCalls` 或 `errorMessage`
2. README 可抄三条演示：`DELAY_DEMO` 死信、Agent 成功（httpbin）、Agent `forceFail` → `DEAD`
3. 不要用 `HTTP_CALL` 打 Agent

## 5. 明确不要做

- 第三个 MCP 工具、聊天窗、流式、`ai_call_log`、向量库、Multi-Agent
- Python 连 MySQL / RabbitMQ；改 `schema.sql`；改 Java 执行器
- 简历 / JD / 面试能力（VitaeLens 互补红线）
