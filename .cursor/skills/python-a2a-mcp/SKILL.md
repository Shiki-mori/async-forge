---
name: python-a2a-mcp
description: Implements the Python Agent Runtime: official a2a-sdk server, LangGraph function-calling loop, and real MCP tools http_get plus calculator with SSRF and expression whitelist. Use when working on A2A, agent-card, message/send, LangGraph, MCP, langchain-mcp-adapters, http_get, or calculator.
---

# Python A2A + LangGraph + MCP

契约：[a2a-contract.md](a2a-contract.md)。结果：[result-schema.md](result-schema.md)。图与工具：[langgraph-mcp.md](langgraph-mcp.md)。

## 实现顺序

1. Agent Card 可 `curl` 通 `GET /.well-known/agent-card.json`
2. `forceFail=true` 的 JSON-RPC `SendMessage`（`POST /`，头 `A2A-Version: 1.0`）→ A2A Task failed
3. `forceFail=false` 先返回固定成功 JSON（无 LLM），供 Java 联调
4. MCP 实现 `http_get` + `calculator`（SSRF 与表达式白名单）
5. LangGraph Function Calling 接到 MCP Client（如 `langchain-mcp-adapters`）
6. 去掉固定成功 stub；`finalize` 校验 schema；httpbin 指令做成功演示

## 硬约束

- 官方 `a2a-sdk` 作 A2A Server（JSON-RPC over HTTP）。禁止主协议改成 `/api/agent/run`。
- Java 阻塞一次调用，同步等待最终 Task。不要做 Java 侧轮询 + 流式。
- 图必须经 MCP Client 调工具。禁止「目录里放普通函数、图直接调用」。
- Function Calling 必须真实发生。禁止正则从模型文本抠 URL。
- 用 LangGraph 条件边循环，禁止 `while True` 调模型。最多 5 轮工具。
- `finalize` 不得把模型原始长文本直接当 `summary`。
- 不连 MySQL / RabbitMQ。日志：`taskId` + instruction 截断 ≤200 字；禁止打印 API Key。

## 模型

OpenAI 兼容 Chat Completions：`AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL`。LangChain 只作 Chat 模型与工具绑定，不要再包一层业务 Chain。
