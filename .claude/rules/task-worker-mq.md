---
description: 任务执行器注册表、状态机、拆事务、MQ/DLQ 与 AGENT_TASK Worker
paths:
  - "backend/**/worker/**/*.java"
  - "backend/**/mq/**/*.java"
  - "backend/**/config/RabbitMqConfig.java"
  - "backend/**/config/MqProperties.java"
  - "backend/**/service/TaskExecutionService.java"
  - "backend/**/service/impl/TaskServiceImpl.java"
  - "backend/**/mapper/TaskMapper.java"
  - "backend/**/entity/TaskType.java"
  - "backend/**/entity/TaskStatus.java"
  - "backend/**/agent/**/*.java"
---

# Worker / MQ

- 勿改 `TaskExecutor` 接口语义（`taskType()` + `execute(Task)`）。新类型 = 新 `@Component` + `TaskType` 枚举，禁止巨型 switch。
- 状态机：`PENDING → RUNNING → SUCCESS`；未耗尽的失败写 `FAILED` 并等待延迟消息重新抢占，失败耗尽写 `DEAD`。失败时累加 `retryCount` 并记录错误，成功时清空 `error_message`。默认 `max_retry=3` 对应最多三次执行。Agent 必须复用 `handleFailure`，禁止旁路。
- **拆事务**：claim 和结果回写使用短事务；`executor.execute()`（含 A2A）在事务外。首次投递和重试投递均在对应的状态事务提交后执行。
- **延迟重试拓扑**：`task.exchange` 按 `task.retry.2s` / `task.retry.8s` 路由到 `task.retry.2s.q` / `task.retry.8s.q`。两条持久化队列无消费者，`x-message-ttl` 分别为 2000 / 8000 毫秒，到期经 `task.exchange` 和 `task.execute` 回到执行队列。尚可重试时，第一次失败使用 2 秒档，之后使用 8 秒档。
- **原子抢占**：无 `task-retry-count` 消息头的消息只能抢占 `PENDING`，兼容旧版重投的 `PENDING` 任务。带该消息头的消息只能抢占 `FAILED` 且 `retry_count` 与消息头一致的任务。抢占成功后在同一事务内读取任务。
- 手动 ACK / NACK 与队列名（`task.execute.q`、`task.execute.dlq`）不要顺便改；改 MQ 必须同步 README。Agent 仍是一条 `taskId` 消息。
- `AgentTaskExecutor.taskType()` 返回 `"AGENT_TASK"`。`forceFail` 仍走 A2A，Java 不要直接 throw 绕过 Python。成功须校验非空 `summary` 且 `toolCalls` 为数组；执行器不更新任务状态。
- 禁止把 Agent URL 交给 `HttpCallExecutor`（SSRF 会拦 Docker 名 `agent`）。不要用 `@Async` / 线程池替换 MQ。
