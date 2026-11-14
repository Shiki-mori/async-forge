# AGENTS.md

async-forge 是校招作品集里的**异步任务平台**（提交 → RabbitMQ → Worker → 可查 → 重试 / 死信）。Agent 只是任务类型 `AGENT_TASK`，失败走同一套 DLQ。

## 给人类

- 启动、演示、环境变量：[README.md](README.md)
- 优化路线（P3）：[devLog/优化/P3-路线.md](devLog/优化/P3-路线.md)
- 接口契约：[A2A](docs/contracts/a2a-contract.md)、[result_json](docs/contracts/result-schema.md)、[LangGraph 与 MCP 工具](docs/contracts/langgraph-mcp.md)
- 表结构：[database/sql/schema.sql](database/sql/schema.sql)，说明见 [数据库设计](database/数据库设计.md)
- 历史记录：P2 各步骤的搭建说明在 `devLog/环境配置/`，验证清单在 `devLog/验证/`，交付汇总见 [P2 交付记录](devLog/归档/P2-AGENT_TASK/交付记录.md)

## 功能取舍（所有任务）

本项目用于本科生校招（Java 后端 / 全栈岗位）的简历。功能扩展按这个水平取舍：

- 优先加深现有链路（提交 → MQ → Worker → 重试 / 死信）的可靠性、测试和可观测性，而不是横向堆砌新组件。
- 每项新能力都要能讲清原理和 trade-off，并有测试或可复现的演示支撑；讲不深的技术名词不要引入。
- 改动规模与收益要相称：能用现有组件解决的，不新增中间件或服务。

## 架构约束（所有任务）

- 定位是**异步任务平台**。Agent 是任务类型 `AGENT_TASK`，复用同一套状态机与 DLQ。
- Java 管可靠性（鉴权、入库、MQ、抢占、重试、死信）；Python 管智能。Java 不引入 LLM SDK；Python 不连 MySQL / RabbitMQ；前端不直连 Agent。
- 异步执行靠 MQ Worker，不用 `@Async` / 线程池替代。
- 远程调用（A2A / HTTP）不得长时间占 DB 事务。未校验的模型原文不得当 SUCCESS。
- 密钥不入库、不进 Git。日志不打 JWT 全文、密码、API Key。
- 改契约（A2A method / 端口 / `result_json` 字段）须先改 `docs/contracts/` 或对应规则，再改代码。改表须同步 `schema.sql` 与 `数据库设计.md`。

## 给 Agent（事实来源）

### 维护约定

- 本文件维护功能取舍、架构约束、读取条件和文档索引，不复制模块规则或技能流程。
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

- P0 已完成：JWT、任务 CRUD、手动 ACK、`claimForExecution`、`HTTP_CALL` / `DELAY_DEMO`、DLQ。
- P2 已完成：Python LangGraph + MCP；Java `AGENT_TASK` / 拆事务 / A2A `SendMessage`；控制台 + README 三条演示。
- P3 进行中，按 [P3 路线](devLog/优化/P3-路线.md) 推进。已完成：Testcontainers 集成测试、Python 工具测试、2s / 8s 延迟重试与 `FAILED` 状态、Worker 并发可配置。完成一项后在路线文档里标注「已完成」及提交号。
