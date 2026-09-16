---
name: interview-prep
description: Prepares deep-dive interview answers for the async-forge portfolio project. Reads PRIVATE/简历描述.md (gitignored), grounds every claim in code, writes materials only to PRIVATE/面试/. Use when the user asks about 面试, 简历深挖, 项目介绍, STAR, 追问, behavioral/technical interview prep, or how to explain this project.
---

# 面试深挖准备

面向校招口述，不是产品功能。禁止在仓库里加聊天窗 / 模拟面试 / 简历解析。

## 路径

| 用途 | 绝对路径 |
|------|----------|
| 简历原文（必读） | `/home/phrolova/CodeProject/async-forge/PRIVATE/简历描述.md` |
| 方案输出 | `/home/phrolova/CodeProject/async-forge/PRIVATE/面试/` |

`PRIVATE/` 在 `.gitignore` 中。Glob / Grep / 代码搜索经常返回空：这不代表文件不存在。

1. `mkdir -p /home/phrolova/CodeProject/async-forge/PRIVATE/面试`
2. 用 Read 工具读简历绝对路径
3. Read 失败则 Shell：`cat "/home/phrolova/CodeProject/async-forge/PRIVATE/简历描述.md"`
4. 仍失败则停，告诉用户补文件，不要凭 `implement-p2-agent` 里的旧简历稿编造

已有 `PRIVATE/面试/*.md` 时先读再改，避免覆盖用户手改。

## 工序

1. **对齐简历**：只深挖 `简历描述.md` 里出现的能力。不要把 VitaeLens、未做的 Kafka/K8s/工作流引擎写进去。
2. **代码证伪**：每个结论打开对应实现再写。口述稿必须带类名 / 方法名 / 配置项，禁止「大概用了 RabbitMQ」。
3. **写到 PRIVATE/面试**：用户问单点 → 只写该主题文件；要系统准备 → 按下面清单补齐。聊天里给提纲，完整方案落盘。
4. **不进 Git**：不要 `git add PRIVATE/`。不要把 JWT、`.env`、API Key 写进面试稿。

## 输出文件

```text
PRIVATE/面试/
├── README.md           # 索引：简历条目 ↔ 文件 ↔ 先看哪些源码
├── 60秒介绍.md
├── 异步主链路.md
├── 可靠消费与死信.md
├── 消费幂等.md
├── 拆事务.md
├── AGENT_TASK.md
├── 工程与安全.md
└── 追问清单.md
```

用户另点主题时用 `PRIVATE/面试/<主题>.md`，并在 `README.md` 加一行索引。

## 单篇结构

```markdown
# <主题>

## 简历原句
（从 简历描述.md 原文摘抄）

## 30 秒口述
（口语句子，可被追问打断）

## 实现锚点
- `path/Class#method`：一句话说明它证明了什么

## 为什么这样而不是那样
- 选项 A vs B，以及本项目选 A 的原因（结合代码，不空谈）

## 面试官追问
- Q：…
  - A：…（含失败路径：重试 / DEAD / 校验失败）

## 边界（主动说「没做」）
- 简历没写、规则禁止扩的能力，用一句话说清范围
```

## 先搜这些（再按需扩）

- 主链路 / afterCommit：`TaskServiceImpl`、`TaskPublisher`、`TransactionSynchronization`
- 消费 / ACK / DLQ：`TaskConsumer`、Rabbit 配置、`handleFailure`、DLQ 监听
- 幂等抢占：`claimForExecution`（`PENDING`/`FAILED` → `RUNNING`）
- 拆事务：`TaskExecutionService.execute`（短事务抢占 → 事务外 execute → 短事务写终态）
- AGENT_TASK：`AgentTaskExecutor`、`AgentA2aClient`、`agent/` LangGraph+MCP、结果 JSON 校验
- 安全：JWT 用户隔离、`HTTP_CALL` SSRF 拦截

事实冲突时：以代码 + `.cursor/rules` 为准，并在稿里写「简历可收紧的表述」。

## 口述红线

- 异步靠 MQ Worker，不是 `@Async` / 线程池
- Agent 是任务类型 `AGENT_TASK`，不是独立 Agent 平台
- Java 不管 LLM；Python 不连 MySQL / RabbitMQ
- 未校验的模型原文不是 SUCCESS；`forceFail` / 超时走同一套重试与死信
- 不要把 `HTTP_CALL` 说成「调用了 Agent」
