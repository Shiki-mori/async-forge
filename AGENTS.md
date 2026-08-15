# AGENTS.md

async-forge 是校招作品集里的**异步任务平台**（提交 → RabbitMQ → Worker → 可查 → 重试 / 死信），与 VitaeLens（简历分析）互补。Agent 只是任务类型 `AGENT_TASK`，失败走同一套 DLQ。禁止做成第二个简历 / 面试 App。

## 给人类

- 启动、演示、环境变量：[README.md](README.md)
- Python Agent 骨架搭建：[devLog/环境配置/Python-Agent骨架搭建.md](devLog/环境配置/Python-Agent骨架搭建.md)
- 表结构：[database/sql/schema.sql](database/sql/schema.sql)（P2 不改表）

## 给 Agent（事实来源）

可执行约束在 `.cursor/rules/`，工序与契约在 `.cursor/skills/`。偏离契约（例如 A2A SDK method / 端口变更）须**先改对应 rule 或 skill，再改代码**。不要把本文件再写成第二套规格。

### Rules

- 全局红线（always）：[.cursor/rules/project-guardrails.mdc](.cursor/rules/project-guardrails.mdc)
- Java 分层 / 鉴权：[.cursor/rules/java-backend.mdc](.cursor/rules/java-backend.mdc)
- Worker / MQ / 拆事务：[.cursor/rules/task-worker-mq.mdc](.cursor/rules/task-worker-mq.mdc)
- Python Agent：[.cursor/rules/python-agent.mdc](.cursor/rules/python-agent.mdc)
- 控制台：[.cursor/rules/frontend-console.mdc](.cursor/rules/frontend-console.mdc)
- Compose / 密钥 / 表：[.cursor/rules/compose-secrets.mdc](.cursor/rules/compose-secrets.mdc)

### Skills

- P2 交付 A→D：[implement-p2-agent](.cursor/skills/implement-p2-agent/SKILL.md)
- 执行器与拆事务：[extend-task-executor](.cursor/skills/extend-task-executor/SKILL.md)
- A2A / LangGraph / MCP：[python-a2a-mcp](.cursor/skills/python-a2a-mcp/SKILL.md)

## 当前阶段

P0 已完成。P2（`AGENT_TASK` + Python Agent Runtime）未完成，按 `implement-p2-agent` 的 A→D 做。
