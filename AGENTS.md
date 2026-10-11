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

### 维护约定

- `.cursor/rules/` 与 `.cursor/skills/` 是规则和技能正文的唯一维护位置，Cursor 与 Codex 共用。
- `.agents/skills/<技能名>` 是指向 `../../.cursor/skills/<技能名>` 的相对符号链接，仅供 Codex 发现技能；不要在此创建独立副本。新增技能时，在 `.cursor/skills/` 创建完整目录，再在 `.agents/skills/` 添加对应链接。
- 链接整个技能目录，保留 `SKILL.md` 及其附属文档、脚本的相对引用；链接与源目录一同纳入版本控制。
- 本文件只维护项目边界、读取条件和文档索引，不复制详细规则或技能流程。

### Rules

每个任务开始时读取全局红线；分析或修改相关模块前，读取以下对应规则。涉及多个范围时须同时读取。Codex 按此处的条件读取规则，不依赖 Cursor 的 `globs` / `alwaysApply` 自动加载；文档链接本身不代表正文已加载。

- 所有任务：[.cursor/rules/project-guardrails.mdc](.cursor/rules/project-guardrails.mdc)
- Java 后端分层、鉴权，或修改 `backend/**/*.java`：[.cursor/rules/java-backend.mdc](.cursor/rules/java-backend.mdc)
- Worker、MQ、执行器、任务状态机、抢占、重试、死信或执行事务边界：[.cursor/rules/task-worker-mq.mdc](.cursor/rules/task-worker-mq.mdc)
- Python Agent，或修改 `agent/` 下的 Python、依赖、Dockerfile、环境变量示例：[.cursor/rules/python-agent.mdc](.cursor/rules/python-agent.mdc)
- 前端控制台，或修改 `frontend/` 下的 TypeScript / Vue：[.cursor/rules/frontend-console.mdc](.cursor/rules/frontend-console.mdc)
- 部署、Compose、数据库、密钥、环境变量、Dockerfile、gitignore，或修改 `README.md`：[.cursor/rules/compose-secrets.mdc](.cursor/rules/compose-secrets.mdc)
- 面试、简历深挖、项目介绍、STAR 或追问准备：[.cursor/rules/interview-prep.mdc](.cursor/rules/interview-prep.mdc)

### Skills

- P2 交付 A→D：[implement-p2-agent](.cursor/skills/implement-p2-agent/SKILL.md)
- 执行器与拆事务：[extend-task-executor](.cursor/skills/extend-task-executor/SKILL.md)
- A2A / LangGraph / MCP：[python-a2a-mcp](.cursor/skills/python-a2a-mcp/SKILL.md)
- 面试深挖准备：[interview-prep](.cursor/skills/interview-prep/SKILL.md)（读 `PRIVATE/简历描述.md`，写 `PRIVATE/面试/`）
- 代码带读：[drill](.cursor/skills/drill/SKILL.md)（仅用户显式 `/drill`；点名某个方法才只讲该方法，否则按链路讲解；不改代码、不落盘）

## 当前阶段

P0 已完成。P2 **步骤 A、B、C、D 已完成**（Python LangGraph + MCP；Java `AGENT_TASK` / 拆事务 / A2A `SendMessage`；控制台 + README 三条演示）。

F20–F29 已勾完。不要重做 A–C；不要用 `HTTP_CALL` 打 Agent；不要加聊天窗 / 第三个 MCP 工具 / 改 `schema.sql`。
