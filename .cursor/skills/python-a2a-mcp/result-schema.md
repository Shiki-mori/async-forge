# AGENT_TASK payload 与 result_json

## 创建任务 payload

```json
{
  "instruction": "GET https://httpbin.org/json，摘要 HTTP 状态码和 JSON 的顶层字段名。失败则说明原因。",
  "forceFail": false
}
```

校验：

- `instruction` 必填，字符串，trim 后非空，建议最长 2000
- `forceFail` 可选，默认 `false`
- 不要在 Java 里解析用户 URL；是否调 `http_get` 由 Agent 决定

## 成功时 `result_json`

```json
{
  "summary": "请求成功，状态码 200，顶层字段包括 slideshow。",
  "finalAnswer": "状态码 200；顶层字段：slideshow。",
  "toolCalls": [
    {
      "name": "http_get",
      "input": { "url": "https://httpbin.org/json" },
      "ok": true,
      "outputSnippet": "{\"slideshow\": ...}"
    }
  ],
  "durationMs": 1840
}
```

Java 校验：必须存在非空 `summary`，且 `toolCalls` 为数组（允许空数组，例如指令无需工具）。缺一不可写 SUCCESS。
