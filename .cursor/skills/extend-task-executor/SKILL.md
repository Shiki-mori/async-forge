---
name: extend-task-executor
description: Extends async-forge with a new TaskType via TaskExecutor registry, including AGENT_TASK Java wiring and splitting TaskExecutionService so A2A/HTTP is outside DB transactions. Use when adding a task executor, TaskType, AgentTaskExecutor, AgentA2aClient, or refactoring claim/execute/handleFailure.
---

# 扩展 TaskExecutor（含 AGENT_TASK）

新任务类型只通过注册表扩展。A2A 字段见 [a2a-contract.md](../python-a2a-mcp/a2a-contract.md) 与 [result-schema.md](../python-a2a-mcp/result-schema.md)。

## 目标文件

- `entity/TaskType.java` — 增加 `AGENT_TASK`
- `service/impl/TaskServiceImpl.java` — 创建时校验 `instruction`；`validateTaskType` 随枚举自动放开
- `service/TaskExecutionService.java` — **拆事务**（必做）
- `worker/AgentTaskExecutor.java` — 新 `@Component`
- `agent/AgentProperties.java`、`agent/AgentA2aClient.java`
- `resources/application.yml` — `async-forge.agent.*`
- 勿改 `worker/TaskExecutor.java` 接口语义；勿改 `HttpCallExecutor` / `DelayDemoExecutor` 演示语义

## 步骤

1. `TaskType` 增加 `AGENT_TASK`。未知类型仍返回 `TASK_TYPE_UNSUPPORTED`。
2. 创建路径：`instruction` trim 后非空（建议 ≤2000）否则 400。不要在 Java 里解析用户 URL。执行器作为第二道闸（空 instruction 抛错，会进重试）。
3. 拆 `TaskExecutionService.execute`：
   - 短事务：加载、跳过终态、`claimForExecution` → `RUNNING`
   - **无事务**：`taskExecutorRegistry.get(...).execute(task)`（含 A2A / HTTP）
   - 短事务：写 `SUCCESS` + `result_json`，或走现有 `handleFailure`
   - `handleFailure` 写 `PENDING` 后须 `publishAfterCommit`（事务提交后再投递）
   - 不要改松 `claimForExecution`（`PENDING` / `FAILED` → `RUNNING`）
4. `AgentTaskExecutor`：
   - `taskType()` 返回 `"AGENT_TASK"`
   - 解析 payload；`forceFail` **仍走 A2A**，禁止 Java 直接 throw 绕过 Python
   - 调 `AgentA2aClient`；超时抛错
   - 校验返回 JSON：非空 `summary` 且 `toolCalls` 为数组；通过则 `toString` 作为 `result_json`
   - **不**在此方法更新任务状态
5. `AgentA2aClient`：拉 Card → 发 `SendMessage`（A2A 1.0；不是 v0.3 的 `message/send`）→ 超时默认 60s → 抽出 JSON。失败（含 A2A Task failed）视为 `execute()` 抛错。不要写业务摘要逻辑。
6. 配置：

```yaml
async-forge:
  agent:
    base-url: ${AGENT_BASE_URL:http://localhost:8081}
    timeout-seconds: ${AGENT_TIMEOUT_SECONDS:60}
```

Compose 中 backend 必须 `AGENT_BASE_URL=http://agent:8081`。禁止把该 URL 交给 `HttpCallExecutor`。

7. 回归：`DELAY_DEMO` + `fail=true` 仍能重试至 `DEAD`；`HTTP_CALL` 成功语义不变。
