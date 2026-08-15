# README 必须可抄的三条演示

假定已登录并持有 token。默认 `max_retry=3`。

## 1. 平台可靠性（已有）

`DELAY_DEMO` + `"fail": true` → 观察重试次数增加 → `DEAD` → RabbitMQ 管理台看到 `task.execute.dlq`。

## 2. Agent 成功

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

## 3. Agent 失败进死信

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
