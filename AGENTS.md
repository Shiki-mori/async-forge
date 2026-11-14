# AGENTS.md

async-forge 是校招作品集里的**异步任务平台**（提交 → RabbitMQ → Worker → 可查 → 重试 / 死信）。Agent 只是任务类型 `AGENT_TASK`，失败走同一套 DLQ。

## 给人类

- 启动、演示、环境变量：[README.md](README.md)
- Python Agent 骨架搭建：[devLog/环境配置/Python-Agent骨架搭建.md](devLog/环境配置/Python-Agent骨架搭建.md)
- 验证步骤 A：[devLog/验证/P2-步骤A.md](devLog/验证/P2-步骤A.md)
- Java 接入 AGENT_TASK：[devLog/环境配置/P2-步骤B-Java接入.md](devLog/环境配置/P2-步骤B-Java接入.md)
- 真 LangGraph / MCP：[devLog/环境配置/P2-步骤C-真Agent.md](devLog/环境配置/P2-步骤C-真Agent.md)
- 步骤 C 验证：[devLog/验证/P2-步骤C.md](devLog/验证/P2-步骤C.md)
- 控制台 / README 代码说明：[devLog/环境配置/P2-步骤D-控制台.md](devLog/环境配置/P2-步骤D-控制台.md)
- 步骤 D 验证：[devLog/验证/P2-步骤D.md](devLog/验证/P2-步骤D.md)
- P2 交付归档：[A–D 工序与 F20–F29 验收清单](devLog/归档/P2-AGENT_TASK/交付记录.md)
- 表结构：[database/sql/schema.sql](database/sql/schema.sql)（不改表）
- 接口契约：[A2A](docs/contracts/a2a-contract.md)、[result_json](docs/contracts/result-schema.md)、[LangGraph 与 MCP 工具](docs/contracts/langgraph-mcp.md)

## 项目红线（所有任务）

- 校招**异步任务平台**。与 VitaeLens **互补**：禁止简历解析、JD 匹配、模拟面试、聊天机器人。
- Agent 只是任务类型 `AGENT_TASK`（旧名 `AGENT_TOOL` 废弃），复用同一套状态机与 DLQ，不是新平台。
- Java 管可靠性（鉴权、入库、MQ、抢占、重试、死信）；Python 管智能。禁止：Java 加 LLM SDK；Python 连 MySQL / RabbitMQ；前端直连 Agent。
- 远程调用（A2A / HTTP）不得长时间占 DB 事务。未校验的模型原文不得当 SUCCESS。
- 密钥不入库、不进 Git。日志不打 JWT 全文、密码、API Key。
- 窄而硬：MCP 工具仅 2 个、LangGraph 3～5 节点、一种阻塞 A2A。禁止 Multi-Agent、向量库、长期记忆。
- P0 已完成：JWT、任务 CRUD、手动 ACK、`claimForExecution`、`HTTP_CALL` / `DELAY_DEMO`、DLQ。禁止用 `@Async` / 线程池替换 MQ；禁止改写这两类任务的成功演示语义来接 Agent。
- 明确不做：手动重试 HTTP、工作流引擎、Redis 限流、Kafka、K8s、改 `database/sql/schema.sql`、用 `HTTP_CALL` 打 Agent。
- 偏离契约（A2A method / 端口 / `result_json` 字段）须先改 `docs/contracts/` 或对应规则，再改代码。
- 个人面试准备走 skill `interview-prep`，不是产品功能。

## 给 Agent（事实来源）

### 维护约定

- 本文件维护项目边界、红线、读取条件和文档索引，不复制模块规则或技能流程。
- 模块规则在 `.claude/rules/*.md`，frontmatter 的 `paths` 供 Claude Code 按文件自动加载；Codex 按下方条件读取。
- 技能正文唯一位置是 `.claude/skills/<技能名>/`；`.agents/skills/<技能名>` 是指向 `../../.claude/skills/<技能名>` 的相对符号链接，供 Codex 发现。新增技能时先建完整目录，再加链接，二者一同纳入版本控制。
- 接口契约在 `docs/contracts/`，人与 Agent 共用。
- 根目录 `CLAUDE.md` 只用 `@AGENTS.md` 导入本文件，不写正文。

### Rules

分析或修改相关模块前，读取以下对应规则。涉及多个范围时须同时读取。文档链接本身不代表正文已加载。

- Java 后端分层、鉴权，或修改 `backend/**/*.java`：[.claude/rules/java-backend.md](.claude/rules/java-backend.md)
- Worker、MQ、执行器、任务状态机、抢占、重试、死信或执行事务边界：[.claude/rules/task-worker-mq.md](.claude/rules/task-worker-mq.md)
- Python Agent，或修改 `agent/` 下的 Python、依赖、Dockerfile、环境变量示例：[.claude/rules/python-agent.md](.claude/rules/python-agent.md)
- 前端控制台，或修改 `frontend/` 下的 TypeScript / Vue：[.claude/rules/frontend-console.md](.claude/rules/frontend-console.md)
- 部署、Compose、数据库、密钥、环境变量、Dockerfile、gitignore，或修改 `README.md`：[.claude/rules/compose-secrets.md](.claude/rules/compose-secrets.md)

### 改完验证

按改动范围运行，失败时报告输出，不要跳过：

- `backend/`：`mvn verify`。需要本机 Docker（Testcontainers 起 MySQL + RabbitMQ），会跑单元测试和 `*IT` 集成测试；只跑单元测试用 `mvn test`。
- `agent/`：在虚拟环境中 `pip install -e ".[tests]"` 后运行 `pytest`。
- `frontend/`：`npm run build`（含 `vue-tsc` 类型检查）。

### Skills

- 新增任务类型：[extend-task-executor](.claude/skills/extend-task-executor/SKILL.md)
- 面试深挖准备：[interview-prep](.claude/skills/interview-prep/SKILL.md)（读 `PRIVATE/简历描述.md`，写 `PRIVATE/面试/`）
- 代码带读：[drill](.claude/skills/drill/SKILL.md)（仅用户显式 `/drill`；点名某个方法才只讲该方法，否则按链路讲解；不改代码、不落盘）

## 当前阶段

P0 已完成。P2 **步骤 A、B、C、D 已完成**（Python LangGraph + MCP；Java `AGENT_TASK` / 拆事务 / A2A `SendMessage`；控制台 + README 三条演示）。

F20–F29 已勾完。不要重做 A–C；不要用 `HTTP_CALL` 打 Agent；不要加聊天窗 / 第三个 MCP 工具 / 改 `schema.sql`。
