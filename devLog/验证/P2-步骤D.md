# P2 步骤 D 验证（控制台 + README）

步骤 D 只增量控制台表单与 README，不重做 Java 执行器（B）或 LangGraph / MCP（C）。规格以 [implement-p2-agent](../../.cursor/skills/implement-p2-agent/SKILL.md) 步骤 D、[frontend-console.mdc](../../.cursor/rules/frontend-console.mdc)、[demos.md](../../.cursor/skills/implement-p2-agent/demos.md) 为准。

## 通过标准

1. 控制台能提交 `AGENT_TASK`，详情能看到 `result.summary` / `toolCalls` 或 `errorMessage`
2. README 可抄三条演示：`DELAY_DEMO` 死信、Agent 成功（httpbin）、Agent `forceFail` → `DEAD`
3. 浏览器只走 `/api/tasks`，不直连 Agent；不要用 `HTTP_CALL` 打 Agent

## 1. 控制台表单

1. 打开 http://localhost:5173（Compose 默认；或本机 `npm run dev`）
2. 登录后，「任务类型」下拉应有 `DELAY_DEMO` / `HTTP_CALL` / `AGENT_TASK`
3. 选 `AGENT_TASK`：多行指令默认是 httpbin 示例；有 `forceFail` 复选框
4. 提交后列表出现新任务；详情仍是 Payload / Result / Error 三块，没有聊天窗

成功路径（需 `AI_API_KEY`）：不勾强制失败，直接提交。轮询到 `SUCCESS` 后，Result 里 `summary` 非空，`toolCalls` 含 `http_get`。

失败路径：勾选强制失败。最终 `DEAD`，Error 面板有 `errorMessage`。

## 2. README 三条 curl

已登录 token 后，按 [README.md](../../README.md)「演示流程」抄：

1. `DELAY_DEMO` + `"fail": true` → `DEAD`，RabbitMQ 管理台有 `task.execute.dlq`
2. `AGENT_TASK` + httpbin 指令 + `forceFail: false` → `SUCCESS`
3. `AGENT_TASK` + `forceFail: true` → `DEAD`

不要把 Agent 的 URL（`http://agent:8081` 或 `localhost:8081`）交给 `HTTP_CALL`。

## 3. 类型与 API

- `frontend/src/types.ts` 的 `TaskType` 含 `'AGENT_TASK'`
- 创建请求仍走 `frontend/src/api/tasks.ts` 的 `POST /api/tasks`
- payload 为 `{ instruction, forceFail }`
