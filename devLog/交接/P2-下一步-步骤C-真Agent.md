# P2 交接：下一步做步骤 C（真 LangGraph / MCP）

> **已完成。** 步骤 D 也已完成：[P2-下一步-步骤D-控制台.md](P2-下一步-步骤D-控制台.md)。  
> 下文是 C 开工时的交接稿，保留作坑记录，**不是**当前进度表。

## 0. 下一轮 Agent 开场必做

1. 读 [implement-p2-agent](../../.cursor/skills/implement-p2-agent/SKILL.md)（A→D 硬顺序）。
2. 读 [python-a2a-mcp](../../.cursor/skills/python-a2a-mcp/SKILL.md) 以及 [langgraph-mcp.md](../../.cursor/skills/python-a2a-mcp/langgraph-mcp.md)、[result-schema.md](../../.cursor/skills/python-a2a-mcp/result-schema.md)。
3. **只做步骤 C。** 不要改 Java 执行器 / 拆事务（B 已完成），不要改控制台表单（D）。

## 1. 当前进度（事实）

| 步骤 | 状态 | 说明 |
|------|------|------|
| A Python 骨架 | **已完成** | `/health` + Agent Card + `SendMessage` stub |
| B Java 接入 | **已完成** | `AGENT_TASK`、拆事务、`AgentA2aClient`（`SendMessage` + HTTP/1.1） |
| C 真 Agent | **未开始** | `graph.py` / `llm.py` / `mcp_server/tools.py` 仍是 stub / 空 |
| D 控制台 + 三条演示 | 未开始 | `frontend` 的 `TaskType` 仍只有 `HTTP_CALL` \| `DELAY_DEMO` |

F20–F29 对照：

- [x] F20 `TaskType.AGENT_TASK`
- [x] F21 `AgentTaskExecutor`
- [x] F22 Python：`GET /health` + Agent Card + `SendMessage`
- [x] F25 Java 侧校验 `summary` 非空且 `toolCalls` 为数组
- [x] F26 `forceFail` 经 A2A failed → 现有 `handleFailure` → `DEAD`
- [x] F27 Compose `agent` + `AGENT_BASE_URL=http://agent:8081`
- [ ] F23 / F24 LangGraph + MCP → **本步**
- [ ] F28 / F29 控制台与 README 三条演示 → **步骤 D**

## 2. 步骤 B 不要重做（已落地）

- `TaskType.AGENT_TASK`；创建校验 `instruction` trim 非空、≤2000
- `TaskExecutionService`：claim / 写结果短事务，`executor.execute()`（含 A2A）在事务外
- `agent/AgentProperties.java`、`agent/AgentA2aClient.java`、`worker/AgentTaskExecutor.java`
- A2A：**`SendMessage`** + 头 `A2A-Version: 1.0`；JSON-RPC `POST {AGENT_BASE_URL}/`
- **JDK `HttpClient` 必须 HTTP/1.1**。默认 HTTP/2 upgrade 会让 uvicorn 丢掉 POST body，表现为 JSON-RPC `-32700` / `Expecting value: line 1 column 1`
- 联调时 POST 打配置的 `AGENT_BASE_URL`，不要用 Card 里的 `http://agent:8081` 覆盖宿主机映射端口（本机 Agent 映射是 `8082`）
- `forceFail` **仍走 A2A**，Java 不得直接 throw 绕过 Python
- 成功 stub 体（C 去掉后应换成真结果）：

```json
{
  "summary": "stub: skipped LLM; instruction accepted for Java wiring.",
  "finalAnswer": "stub success",
  "toolCalls": [],
  "durationMs": 0
}
```

## 3. 步骤 C 要做什么

按 [python-a2a-mcp](../../.cursor/skills/python-a2a-mcp/SKILL.md) 与 [langgraph-mcp.md](../../.cursor/skills/python-a2a-mcp/langgraph-mcp.md)。

1. MCP 真实协议实现仅两工具：`http_get` + `calculator`（SSRF / 表达式白名单与 rule 一致）。
2. LangGraph 3～5 节点：`fail_fast` → `reason` ⇄ `tools` → `finalize`；条件边循环，最多 5 轮工具。
3. 图必须经 MCP Client 调工具（如 `langchain-mcp-adapters`）。禁止目录里放普通函数、图直接调用。
4. Function Calling 必须真实发生；禁止正则从模型文本抠 URL。
5. `finalize` 校验 schema；不得把模型长文当 `summary`。
6. **去掉** `graph.py` 的固定成功 stub。`forceFail=true` 仍要 A2A Task failed。
7. 需要 `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL`（OpenAI 兼容 Chat Completions）。密钥不入库、不进 Git、不打日志。
8. 成功演示指令用 httpbin（见 [demos.md](../../.cursor/skills/implement-p2-agent/demos.md) 第 2 条）：`result.toolCalls` 须含 `http_get`。

Java 侧 **不必**为 C 再改执行器，除非 result schema 对不齐（先改 skill 再改代码）。

## 4. 步骤 C 验收

1. `forceFail=false` + httpbin 指令 → 任务 `SUCCESS`，`summary` 非空，`toolCalls` 含 `http_get`。
2. `forceFail=true` → 仍 A2A failed → 重试 → `DEAD`（不要旁路 `handleFailure`）。
3. 不调工具也能成功的指令：`toolCalls` 允许 `[]`（Java 校验仍过）。
4. 不要用 `HTTP_CALL` 打 Agent。

## 5. 明确不要做

- 第三个 MCP 工具、聊天窗、流式、`ai_call_log`、向量库、Multi-Agent
- Python 连 MySQL / RabbitMQ；改 `schema.sql`
- 改 `ConsoleView.vue` / `types.ts`（那是 D）
- 简历 / JD / 面试能力（VitaeLens 互补红线）
