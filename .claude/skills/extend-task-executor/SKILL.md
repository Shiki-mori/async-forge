---
name: extend-task-executor
description: Checklist for adding a new TaskType to async-forge via the TaskExecutor registry, keeping remote calls outside DB transactions and reusing handleFailure. Use when adding a task executor or TaskType, or changing claim/execute/handleFailure in TaskExecutionService.
---

# 新增 TaskType 清单

新类型只通过注册表扩展，禁止在 Worker 里写 `switch`。状态机、拆事务、延迟重试与抢占条件以 [Worker / MQ 规则](../../rules/task-worker-mq.md) 为准；现有 `AgentTaskExecutor` 是参考实现。

## 要改的地方

1. `entity/TaskType.java` 加枚举值。`validateTaskType` 随枚举自动放开，未知类型仍返回 `TASK_TYPE_UNSUPPORTED`。
2. `service/impl/TaskServiceImpl.java`：payload 有必填字段时，仿照 `validateAgentPayload` 在创建时校验，失败抛 `BusinessException`。
3. `worker/<Xxx>Executor.java`：新 `@Component` 实现 `TaskExecutor`，`taskType()` 返回枚举名。
   - `execute(Task)` 只返回 `result_json` 或抛异常；**不更新任务状态**。
   - 执行器内再校验一次 payload（第二道闸，抛错会进重试）。
   - 远程调用自带超时；结果要先校验结构再返回，未校验的原文不当 SUCCESS。
4. 需要配置时加到 `resources/application.yml` 的 `async-forge.<类型>.*`，环境变量占位同步到 `deploy/.env.example`。
5. `frontend/src/types.ts` 的 `TaskType` 与 `ConsoleView.vue` 的表单分支同步；README 补一条演示。

## 不要碰

- `TaskExecutor` 接口语义、`TaskExecutionService` 的三段式（短事务抢占 → 事务外 `execute` → 短事务回写 / `handleFailure`）。
- `HttpCallExecutor` / `DelayDemoExecutor` 的演示语义；不要借 `HttpCallExecutor` 调内部服务。

## 回归

- `backend/` 下 `mvn verify`（需要 Docker）：`DelayDemoRetryIT` 仍是 `FAILED/1 → FAILED/2 → DEAD/3`。
- 新类型至少覆盖一条成功、一条失败到 `DEAD` 的路径。
