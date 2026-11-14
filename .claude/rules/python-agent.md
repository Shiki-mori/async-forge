---
description: Python Agent Runtime 约束：FastAPI、A2A、LangGraph、真实 MCP 与 SSRF
paths:
  - "agent/**/*.py"
  - "agent/pyproject.toml"
  - "agent/requirements.txt"
  - "agent/Dockerfile"
  - "agent/.env.example"
---

# Python Agent

- FastAPI：`GET /health` 返回 `{"status":"ok"}`。A2A 用官方 `a2a-sdk`，禁止把 `/api/agent/run` 当主协议。
- LangGraph 图：`fail_fast` → `reason` ⇄ `tools` → `finalize`。用条件边循环，最多 5 轮工具；禁止 `while True` 调模型。
- Function Calling 必须真实发生；禁止正则从模型文本抠 URL。
- MCP：经 MCP Client 调工具（如 `langchain-mcp-adapters`）。禁止目录里放普通函数、图直接调用。
- 现有工具 `http_get` 与 `calculator`；新增工具同样经 MCP 暴露，并做输入校验与出站限制。`calculator` 只允许数字与 `+ - * / ( ) .` 与空白，禁止 `eval`。
- `http_get` SSRF 与 Java 同级：只 `http`/`https`；拒无 host、`localhost`、`*.local`、loopback、link-local、site-local、any-local；拒 `169.254.169.254`、`metadata.google.internal`。不要放宽到 Docker 内网。
- `forceFail=true` → A2A Task failed。`finalize` 产出短摘要，不得把模型长文当 `summary`。
- 不连 MySQL / RabbitMQ。日志：`taskId` + instruction 截断 ≤200 字；禁止打印 API Key。
- 模型走 OpenAI 兼容 Chat Completions（`AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL`）。LangChain 只作 Chat 模型与工具绑定，不再包一层业务 Chain。
- 契约：[A2A](../../docs/contracts/a2a-contract.md)、[result_json](../../docs/contracts/result-schema.md)、[LangGraph 与 MCP 工具](../../docs/contracts/langgraph-mcp.md)。
