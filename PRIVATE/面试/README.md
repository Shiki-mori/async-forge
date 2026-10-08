# 面试深挖材料

本目录 gitignore，不进仓库。由 skill `interview-prep` 根据 `PRIVATE/简历描述.md` 生成。

| 简历条目 | 材料 | 先看源码 |
|----------|------|----------|
| 「介绍一下这个项目」 | [60秒介绍.md](60秒介绍.md) | `TaskServiceImpl#createTask`、`TaskProducer#publishAfterCommit`、`TaskExecutionService` |
| 为什么选 RabbitMQ | [RabbitMQ.md](RabbitMQ.md) | `RabbitMqConfig`、`TaskConsumer`、`application.yml` `listener.simple`、`DlqListener` |
| 为什么用 Spring Boot | [SpringBoot.md](SpringBoot.md) | `pom.xml` starters、`application.yml` `listener.simple`、`TaskProducer#publishAfterCommit`、`TaskExecutionService`、`TaskExecutorRegistry` |
| 追问：为什么不用 RocketMQ | [RocketMQ.md](RocketMQ.md) | `TaskProducer#publishAfterCommit`、`DelayDemoExecutor`、`handleFailure`；无 RocketMQ 依赖 |
| 消费幂等怎么实现 | [消费幂等.md](消费幂等.md) | `TaskMapper#claimForExecution`、`TaskExecutionService#claimInShortTransaction` |
| 追问：InnoDB 行锁 | [InnoDB行锁.md](InnoDB行锁.md) | `claimForExecution` 主键 UPDATE；`schema.sql` `ENGINE=InnoDB` |
| 出门前速记 | [00-一页纸.md](00-一页纸.md) | — |
| 状态机 / PENDING→RUNNING→SUCCESS | [状态机.md](状态机.md) | `TaskStatus`、`TaskMapper#claimForExecution`、`TaskExecutionService` |
| 陷阱：Redis 怎么写的 | [Redis.md](Redis.md) | 无 Redis。看 `pom.xml`、Compose、`TaskMapper#claimForExecution` |
| 最大困难 / 怎么解决 | [最大困难.md](最大困难.md) | `TaskExecutionService#execute`、`claimForExecution`、`AgentA2aClient` |
| 工程与安全 / 调度过程数据安全 | [工程与安全.md](工程与安全.md) | `JwtAuthFilter`、`TaskServiceImpl#requireOwnedTask`、`HttpCallExecutor#validateUrl`、`tools.py#_assert_public_http_url` |
| 接口怎么设计、为什么 | [接口设计.md](接口设计.md) | `TaskController`、`CreateTaskRequest`、`Result`、`TaskServiceImpl#createTask`、`requireOwnedTask` |
| 对着代码讲整条链路 | [项目讲解/整体链路.md](项目讲解/整体链路.md) | `TaskServiceImpl#createTask`、`TaskProducer#publishAfterCommit`、`TaskConsumer`、`TaskExecutionService#execute`、`claimForExecution`、`handleFailure`、`DlqListener` |
