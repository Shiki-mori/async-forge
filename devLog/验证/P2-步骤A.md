# 验证 P2 步骤 A（Python Agent 骨架）

目标读者：开发者。用来确认步骤 A **已经做完、可以进步骤 B**，不是讲骨架怎么搭。

搭建背景见 [Python-Agent骨架搭建.md](../环境配置/Python-Agent骨架搭建.md)。Docker 启停与本机端口见 [Python-Agent-Docker.md](../环境配置/Python-Agent-Docker.md)。

## 通过标准（步骤 A 声称完成的事）

全部满足才算 A 完成：

1. `GET /health` 返回 `{"status":"ok"}`
2. `GET /.well-known/agent-card.json` 可访问，且名片字段符合契约
3. `forceFail=false` 的 A2A `SendMessage` → Task **completed**，文本为固定成功 JSON（无 LLM）
4. `forceFail=true` → Task **failed**
5. Compose 能起 `agent` 服务，healthcheck 走 `/health`（不必每次都打全套镜像，但示例文件必须在）

**本步不算失败、也不要拿来当 A 的验收：**

- 控制台没有 `AGENT_TASK`
- `POST /api/tasks` 还不接受 `AGENT_TASK`（Java 是步骤 B）
- 结果里没有 `http_get` / 没有打 httpbin（那是步骤 C）
- 没有填写 `AI_API_KEY`

契约：[a2a-contract.md](../../.cursor/skills/python-a2a-mcp/a2a-contract.md)。skill 旧文里的 `message/send` 不要用来测本骨架。

## 0. 先让进程起来

默认基址 `http://localhost:8081`。若 `deploy/.env` 里改了 `AGENT_PORT`（例如本机 8081 被占用时用 8082），把下面的 `8081` 换成该端口。Compose 内 Java 仍应使用 `http://agent:8081`，与宿主机映射无关。

**方式一：宿主机 venv（适合改代码后立刻测）**

```bash
cd agent
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
cp -n .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

无需 `AI_API_KEY`。另开一个终端做下面的 curl。

**方式二：只起 Agent 容器**

```bash
cd deploy
docker compose up -d agent
docker compose ps agent   # 期望 State 含 healthy
```

`deploy/docker-compose.yml` 若还是旧文件、没有 `agent` 服务，从 `docker-compose.yml.example` 再拷一次。细节见 Docker 文档。

下面用环境变量，避免端口写死：

```bash
AGENT_URL="http://localhost:${AGENT_PORT:-8081}"
```

`jq` 可选。没有就肉眼对 JSON。

## 1. 静态：仓库里该有的文件

在仓库根目录：

```bash
test -f agent/pyproject.toml \
  && test -f agent/Dockerfile \
  && test -f agent/.env.example \
  && test -f agent/app/main.py \
  && test -f agent/app/a2a_server.py \
  && test -f agent/app/graph.py \
  && echo "ok: agent skeleton files"
```

Compose 示例须含服务 `agent`，且 backend 注入 `AGENT_BASE_URL=http://agent:8081`：

```bash
grep -n "container_name: async-forge-agent" deploy/docker-compose.yml.example
grep -n "AGENT_BASE_URL: http://agent:8081" deploy/docker-compose.yml.example
```

`llm.py` / `mcp_server/tools.py` 本步允许几乎为空。

## 2. 运行时：health

```bash
curl -sS "${AGENT_URL}/health"
```

期望：**恰好** `{"status":"ok"}`（Compose healthcheck 探的是这个路径，不是 Card）。

失败：`Connection refused` → 进程没起来或端口不对。容器 `unhealthy` → 看 `docker compose logs agent`。

## 3. 运行时：Agent Card

```bash
curl -sS "${AGENT_URL}/.well-known/agent-card.json"
```

至少核对：

| 字段 | 期望 |
|------|------|
| `name` | `async-forge-agent` |
| `skills` | 含 `id` 为 `run-instruction` 的一项 |
| `supportedInterfaces` | 含 `protocolBinding=JSONRPC`、`protocolVersion=1.0` |
| 接口 `url` | 本机进程默认 `http://localhost:8081`；Compose 内由 `AGENT_JSONRPC_URL=http://agent:8081` 写入 |

有 `jq` 时：

```bash
curl -sS "${AGENT_URL}/.well-known/agent-card.json" | jq '{name, skills: [.skills[].id], interfaces: .supportedInterfaces}'
```

## 4. 运行时：固定成功（无 LLM）

协议：**`POST /`**，JSON-RPC method **`SendMessage`**，头 **`A2A-Version: 1.0`**。用户 Message 的 text part 是 **JSON 字符串**。

```bash
curl -sS -X POST "${AGENT_URL}/" \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"1","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-ok","parts":[{"text":"{\"taskId\":1,\"instruction\":\"hello\",\"forceFail\":false}"}]}}}'
```

期望：

| 检查点 | 期望 |
|--------|------|
| HTTP | 200，且是 JSON-RPC `result`，不是 `error` |
| `result.task.status.state` | `TASK_STATE_COMPLETED` |
| artifact 或 status.message 的 text | 可 `json.loads` 的对象 |
| 该对象 | `summary` 非空；`toolCalls` 为数组（stub 为 `[]`）；含 `finalAnswer` |

stub 成功体固定为：

```json
{
  "summary": "stub: skipped LLM; instruction accepted for Java wiring.",
  "finalAnswer": "stub success",
  "toolCalls": [],
  "durationMs": 0
}
```

`summary` 以 `stub:` 开头说明 **没有调模型**，这是步骤 A 的正确行为。

有 `jq` 时抽状态与 artifact 文本：

```bash
curl -sS -X POST "${AGENT_URL}/" \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"1","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-ok","parts":[{"text":"{\"taskId\":1,\"instruction\":\"hello\",\"forceFail\":false}"}]}}}' \
  | jq '{state: .result.task.status.state, artifact: .result.task.artifacts[0].parts[0].text}'
```

## 5. 运行时：强制失败

```bash
curl -sS -X POST "${AGENT_URL}/" \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"2","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-fail","parts":[{"text":"{\"taskId\":2,\"instruction\":\"任意内容\",\"forceFail\":true}"}]}}}'
```

期望：`result.task.status.state` 为 **`TASK_STATE_FAILED`**（不是 HTTP 500 随便崩掉；步骤 B 会把这种 failed 当 `execute()` 抛错）。

```bash
# 接上一命令的响应：
# jq '.result.task.status.state'  → "TASK_STATE_FAILED"
```

## 6. 建议加测：非法 payload

不是 skill 硬性条目，但能确认执行器会 failed 而不是 completed：

```bash
curl -sS -X POST "${AGENT_URL}/" \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"3","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"msg-bad","parts":[{"text":"not-json"}]}}}'
```

期望仍是 `TASK_STATE_FAILED`。

## 7. 常见失败

| 现象 | 原因 |
|------|------|
| `Method not found` | 用了 v0.3 的 `message/send`，应改 `SendMessage` |
| 版本 / 0.3 线格式报错 | 缺头 `A2A-Version: 1.0` |
| Card 200、POST 404 | JSON-RPC 不在 `/`，或打到了别的端口 |
| 连不上 8081 | 本机被占用；看 `deploy/.env` 的 `AGENT_PORT` |
| 成功但 `summary` 不像 stub | 已经进了步骤 C，或打到了别的进程 |

## 8. 签字清单

复制自用：

```text
[ ] agent/ 骨架文件存在
[ ] compose example 有 agent，backend 为 http://agent:8081
[ ] GET /health → {"status":"ok"}
[ ] GET /.well-known/agent-card.json → name=async-forge-agent，skill=run-instruction，JSONRPC 1.0
[ ] SendMessage forceFail=false → TASK_STATE_COMPLETED，summary 非空，toolCalls 为 []
[ ] SendMessage forceFail=true → TASK_STATE_FAILED
[ ] 未把「控制台 AGENT_TASK」或「http_get」当成步骤 A 失败
```

全勾上后再做步骤 B。交接：[P2-下一步-步骤B-Java接入.md](../交接/P2-下一步-步骤B-Java接入.md)。
