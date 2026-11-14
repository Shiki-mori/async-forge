# LangGraph 与 MCP 工具

## LangGraph

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
