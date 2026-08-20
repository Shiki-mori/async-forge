# AGENTS.md

async-forge 是校招作品集里的**异步任务平台**（提交 → RabbitMQ → Worker → 可查 → 重试 / 死信），与 VitaeLens（简历分析）互补。Agent 只是任务类型 `AGENT_TASK`，失败走同一套 DLQ。禁止做成第二个简历 / 面试 App。

## 给人类

- 启动、演示、环境变量：[README.md](README.md)
- Python Agent 骨架搭建：[devLog/环境配置/Python-Agent骨架搭建.md](devLog/环境配置/Python-Agent骨架搭建.md)
- 验证步骤 A：[devLog/验证/P2-步骤A.md](devLog/验证/P2-步骤A.md)
- Java 接入 AGENT_TASK：[devLog/环境配置/P2-步骤B-Java接入.md](devLog/环境配置/P2-步骤B-Java接入.md)
- 真 LangGraph / MCP：[devLog/环境配置/P2-步骤C-真Agent.md](devLog/环境配置/P2-步骤C-真Agent.md)
- 步骤 C 验证：[devLog/验证/P2-步骤C.md](devLog/验证/P2-步骤C.md)
- 控制台 / README 代码说明：[devLog/环境配置/P2-步骤D-控制台.md](devLog/环境配置/P2-步骤D-控制台.md)
- 步骤 D 验证：[devLog/验证/P2-步骤D.md](devLog/验证/P2-步骤D.md)
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

P0 已完成。P2 **步骤 A、B、C、D 已完成**（Python LangGraph + MCP；Java `AGENT_TASK` / 拆事务 / A2A `SendMessage`；控制台 + README 三条演示）。

F20–F29 已勾完。不要重做 A–C；不要用 `HTTP_CALL` 打 Agent；不要加聊天窗 / 第三个 MCP 工具 / 改 `schema.sql`。
