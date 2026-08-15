# A2A 契约（Java Client ↔ Python Server）

Python **必须**用官方 `a2a-sdk` 作为 A2A Server（JSON-RPC over HTTP），不要发明 `/api/agent/run` 作为主协议。

## 最低要求

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

5. 成功：A2A Task completed；artifact 或最终 message 的文本为 `result_json`（见 [result-schema.md](result-schema.md)）
6. 失败：A2A Task failed（含 `forceFail`、工具致命错误、模型不可用、结果校验失败）。Java 将其视为 `execute()` 抛错
7. Java **不要**把 Agent 的 URL 交给 `HttpCallExecutor`（SSRF 黑名单会拦 Docker DNS 名 `agent`）

`AgentA2aClient` 职责：拉 Card → 发 message → 设超时 → 抽出 JSON。不要在 Client 里写业务摘要逻辑。

## 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 返回 `{"status":"ok"}`，供 Compose healthcheck |
