# P2 步骤 C 验证（真 LangGraph / MCP）

步骤 C 已把固定成功 stub 换成：MCP 两工具 + LangGraph Function Calling。规格以 [python-a2a-mcp](../../.cursor/skills/python-a2a-mcp/SKILL.md) 为准。

本机 Agent 映射端口看 `deploy/.env` 的 `AGENT_PORT`（占用 8081 时常用 **8082**）。Compose 内 Java 仍是 `http://agent:8081`。

## 1. 环境

在 `deploy/.env`（Compose）或 `agent/.env`（本机 uvicorn）填写 OpenAI 兼容 Chat Completions，**不要提交 Git**：

```text
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=sk-...
AI_MODEL=gpt-4o-mini
```

改完后重建 Agent 镜像（密钥走环境变量，不烘焙进镜像）：

```bash
cd deploy
docker compose build agent
docker compose up -d agent
docker compose ps agent
```

本机不走 Compose 时：

```bash
cd agent
source .venv/bin/activate
pip install -e .
cp .env.example .env   # 再填 AI_*
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

`forceFail=true` **不需要**模型密钥。未填密钥时，`forceFail=false` 会 A2A failed（不再返回 stub）。

## 2. 健康与名片（与 A 相同）

```bash
AGENT_URL=http://localhost:8082   # 按 AGENT_PORT 改

curl -sS "${AGENT_URL}/health"
# {"status":"ok"}

curl -sS "${AGENT_URL}/.well-known/agent-card.json" | jq '{name, skills: [.skills[].id]}'
```

## 3. forceFail → A2A failed（无需 LLM）

```bash
curl -sS -X POST "${AGENT_URL}/" \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"2","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-fail","parts":[{"text":"{\"taskId\":2,\"instruction\":\"任意内容\",\"forceFail\":true}"}]}}}' \
  | jq '.result.task.status.state'
```

期望：`TASK_STATE_FAILED`。经 Java `AGENT_TASK` 提交同样 payload 时，走现有重试 → `DEAD`，不要旁路 `handleFailure`。

## 4. httpbin 成功（需要 LLM）

A2A 直测：

```bash
curl -sS -X POST "${AGENT_URL}/" \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"1","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-ok","parts":[{"text":"{\"taskId\":1,\"instruction\":\"GET https://httpbin.org/json ，告诉我 HTTP 状态码以及返回 JSON 的顶层字段名。\",\"forceFail\":false}"}]}}}' \
  | jq '{state: .result.task.status.state, artifact: .result.task.artifacts[0].parts[0].text}'
```

期望：`TASK_STATE_COMPLETED`；artifact JSON 的 `summary` 非空；`toolCalls` 含 `http_get`。

走 Java（已登录 token）：

```bash
curl -sS -X POST http://localhost:8090/api/tasks \
  -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"AGENT_TASK","payload":{"instruction":"GET https://httpbin.org/json ，告诉我 HTTP 状态码以及返回 JSON 的顶层字段名。","forceFail":false}}'
```

期望：任务 `SUCCESS`。不要用 `HTTP_CALL` 打 Agent。

## 5. 签字清单

```text
[ ] MCP 仅 http_get + calculator；图经 MCP Client 调工具
[ ] LangGraph：fail_fast → reason ⇄ tools → finalize，最多 5 轮
[ ] 已去掉固定成功 stub
[ ] forceFail=true → A2A TASK_STATE_FAILED（无需 API Key）
[ ] 已填 AI_* 后，httpbin 指令 SUCCESS，toolCalls 含 http_get，summary 非空
[ ] 未改控制台表单（那是 D）；未改 Java 执行器（除非 schema 对不齐）
[ ] 未用 HTTP_CALL 打 Agent
```
