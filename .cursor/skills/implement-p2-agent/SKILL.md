---
name: implement-p2-agent
description: Orchestrates async-forge P2 AGENT_TASK delivery in order A→D (Python skeleton, Java worker, real LangGraph/MCP, console+README). Use when the user asks to implement P2, AGENT_TASK, F20–F29, the Python Agent Runtime, or to follow P2 delivery steps A→D.
---

# P2 AGENT_TASK 交付

按 A → B → C → D **硬顺序**。每步可运行后再做下一步。不要先画 Multi-Agent。

Java 执行器细节读 [extend-task-executor](../extend-task-executor/SKILL.md)。A2A / LangGraph / MCP 读 [python-a2a-mcp](../python-a2a-mcp/SKILL.md)。三条演示见 [demos.md](demos.md)。

## F20–F29

- [x] F20 `TaskType.AGENT_TASK`（不改表）
- [x] F21 `AgentTaskExecutor`（注册表扩展，禁止巨型 switch）
- [x] F22 Python：`GET /health` + A2A Card + `SendMessage`
- [ ] F23 LangGraph 3～5 节点，最多 5 轮工具
- [ ] F24 MCP 真实协议：`http_get` + `calculator`
- [x] F25 结构化结果（缺字段视为失败）
- [x] F26 超时 / `forceFail` 走现有重试与 DLQ
- [x] F27 Compose `agent` + `AGENT_BASE_URL=http://agent:8081`
- [ ] F28 控制台可提交并展示 result / error
- [ ] F29 README：启动、环境变量、三条演示

## 步骤 A — Python 骨架

创建 `agent/`（`pyproject.toml` 或 `requirements.txt` 二选一）：

```text
agent/
├── Dockerfile
├── pyproject.toml
├── .env.example
└── app/
    ├── __init__.py
    ├── main.py              # FastAPI：/health + 挂载 A2A
    ├── config.py
    ├── schemas.py
    ├── a2a_server.py
    ├── graph.py             # 本步可 stub
    ├── llm.py               # 本步可空
    └── mcp_server/
        ├── __init__.py
        └── tools.py         # 本步可空
```

- `GET /health` → `{"status":"ok"}`
- Agent Card 能 curl 通
- `forceFail=true` → A2A Task failed
- `forceFail=false` → 固定成功 JSON（无 LLM），供 Java 联调

## 步骤 B — Java 接入

按 skill `extend-task-executor`：类型、拆事务、A2A Client、执行器、Compose。

用步骤 A 的固定成功与 `forceFail` 跑通 `SUCCESS` 与 `DEAD`。

## 步骤 C — 真 Agent

按 skill `python-a2a-mcp`：MCP 两工具 + LangGraph Function Calling。去掉固定成功 stub。用 httpbin 指令做成功演示。

## 步骤 D — 控制台与文档

- `types.ts` + `ConsoleView.vue`：`AGENT_TASK`、`instruction`、`forceFail`
- README：启动、环境变量、[demos.md](demos.md) 三条演示、架构（Java 可靠性 × Python 智能）
- 若 A2A method / 端口与 skill 不一致：先改 skill / rule，再改代码

## 明确不做

- 用 `HTTP_CALL` 调 Agent；Java 做工具循环；Python 连 MQ / 写 `task` 表
- 第三个工具、聊天窗、流式打字、`ai_call_log` 表
- Kafka、K8s、工作流引擎、Redis 限流、向量库、Multi-Agent

## 简历（仅已完成能力）

```text
基于 Spring Boot 3 实现异步任务平台：任务经 RabbitMQ 投递并由 Worker 消费，
支持状态机、失败重试、死信队列与消费幂等。
扩展 AGENT_TASK：Java Worker 通过 A2A 调用独立的 Python Agent Runtime
（FastAPI + LangGraph），经 Function Calling / MCP 执行受限 HTTP 与计算工具，
结果回写任务表；工具超时与模型失败走与普通任务相同的重试和死信。
```

不要把 VitaeLens 的简历分析写进本项目简历条目。
