"""LangGraph: fail_fast → reason ⇄ tools → finalize. Tools only via MCP Client."""

from __future__ import annotations

import json
import logging
import os
import sys
import time
from pathlib import Path
from typing import Annotated, Any, Literal, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.tools import BaseTool
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_mcp_adapters.tools import load_mcp_tools
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages

from app.llm import LlmConfigError, get_chat_model
from app.schemas import AgentInstruction, AgentResult

logger = logging.getLogger(__name__)

MAX_TOOL_ROUNDS = 5
_SUMMARY_LIMIT = 240
_ANSWER_LIMIT = 400
_SNIPPET_LIMIT = 1024
_AGENT_ROOT = Path(__file__).resolve().parents[1]
_EXPECTED_TOOLS = frozenset({"http_get", "calculator"})

_SYSTEM_PROMPT = """你是 async-forge 的任务执行 Agent，只完成当前这一条指令。
规则：
- 指令里出现需要访问的 http/https URL 时，必须调用工具 http_get，禁止编造网页内容或状态码。
- 需要四则运算时，必须调用工具 calculator，禁止口算后直接给出未经工具确认的结果。
- 不要请求 localhost、内网或云 metadata。
- 工具返回后，用简短中文总结（状态码、JSON 顶层字段名、或计算结果）。不要粘贴长篇原文。
- 若工具失败，说明失败原因。"""


class ForceFailError(Exception):
    """Raised when payload.forceFail is true."""


class AgentRunError(Exception):
    """Raised when the graph cannot produce a valid result_json."""


class AgentState(TypedDict):
    instruction: str
    force_fail: bool
    task_id: str
    messages: Annotated[list[BaseMessage], add_messages]
    tool_calls: list[dict[str, Any]]
    tool_rounds: int
    summary: str
    final_answer: str
    error: str | None


def _clip(text: str, limit: int) -> str:
    collapsed = " ".join((text or "").split())
    if len(collapsed) <= limit:
        return collapsed
    return collapsed[: limit - 3] + "..."


def _message_text(message: BaseMessage) -> str:
    content = message.content
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and block.get("type") == "text":
                parts.append(str(block.get("text") or ""))
        return "\n".join(parts)
    return str(content or "")


def _as_text(output: Any) -> str:
    if output is None:
        return ""
    if isinstance(output, bytes):
        return output.decode("utf-8", errors="replace")
    if isinstance(output, str):
        return output
    if isinstance(output, list):
        parts: list[str] = []
        for block in output:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and block.get("text") is not None:
                parts.append(str(block["text"]))
            else:
                parts.append(_as_text(block))
        return "\n".join(p for p in parts if p)
    if isinstance(output, dict) and output.get("text") is not None and output.get("type") == "text":
        return str(output["text"])
    return json.dumps(output, ensure_ascii=False, default=str)


def _mcp_env() -> dict[str, str]:
    env = os.environ.copy()
    env.pop("AI_API_KEY", None)
    extra = str(_AGENT_ROOT)
    current = env.get("PYTHONPATH", "")
    env["PYTHONPATH"] = extra if not current else extra + os.pathsep + current
    return env


def _create_mcp_client() -> MultiServerMCPClient:
    return MultiServerMCPClient(
        {
            "async-forge": {
                "transport": "stdio",
                "command": sys.executable,
                "args": ["-m", "app.mcp_server"],
                "cwd": str(_AGENT_ROOT),
                "env": _mcp_env(),
            }
        }
    )


def _assert_expected_tools(tools: list[BaseTool]) -> None:
    names = {tool.name for tool in tools}
    if names != _EXPECTED_TOOLS:
        raise AgentRunError(f"unexpected MCP tools: {sorted(names)}")


def fail_fast(state: AgentState) -> dict[str, Any]:
    if state.get("force_fail"):
        return {"error": "forceFail=true"}
    return {
        "error": None,
        "tool_calls": [],
        "tool_rounds": 0,
        "messages": [
            SystemMessage(content=_SYSTEM_PROMPT),
            HumanMessage(content=state["instruction"]),
        ],
    }


def _last_ai_message(messages: list[BaseMessage]) -> AIMessage | None:
    for message in reversed(messages):
        if isinstance(message, AIMessage):
            return message
    return None


def _compose_summary(text: str, tool_calls: list[dict[str, Any]]) -> str:
    clipped = _clip(text, _SUMMARY_LIMIT)
    if clipped:
        return clipped
    if tool_calls:
        names = ", ".join(str(item.get("name") or "tool") for item in tool_calls)
        return _clip(f"已执行工具：{names}。", _SUMMARY_LIMIT)
    return "已根据指令完成，未调用工具。"


def finalize(state: AgentState) -> dict[str, Any]:
    if state.get("error"):
        return {}
    last = _last_ai_message(state.get("messages") or [])
    raw_text = _message_text(last) if last is not None else ""
    # Never persist the raw long model dump as summary.
    summary = _compose_summary(raw_text, state.get("tool_calls") or [])
    final_answer = _clip(raw_text, _ANSWER_LIMIT) or summary
    try:
        AgentResult.model_validate(
            {
                "summary": summary,
                "finalAnswer": final_answer,
                "toolCalls": state.get("tool_calls") or [],
                "durationMs": 0,
            }
        )
    except ValueError as exc:
        logger.info(
            "finalize schema failed taskId=%s err=%s",
            state.get("task_id"),
            exc,
        )
        return {"error": f"result schema invalid: {exc}"}
    return {"summary": summary, "final_answer": final_answer}


def _route_after_fail_fast(state: AgentState) -> Literal["reason", "__end__"]:
    if state.get("error"):
        return "__end__"
    return "reason"


def _route_after_reason(state: AgentState) -> Literal["tools", "finalize"]:
    if state.get("error"):
        return "finalize"
    last = (state.get("messages") or [None])[-1]
    rounds = int(state.get("tool_rounds") or 0)
    if (
        isinstance(last, AIMessage)
        and last.tool_calls
        and rounds < MAX_TOOL_ROUNDS
    ):
        return "tools"
    return "finalize"


def _build_graph(tools: list[BaseTool]):
    tool_map = {tool.name: tool for tool in tools}

    async def reason(state: AgentState) -> dict[str, Any]:
        try:
            model = get_chat_model()
        except LlmConfigError as exc:
            return {"error": str(exc)}
        bound = model.bind_tools(tools) if tools else model
        try:
            response = await bound.ainvoke(state["messages"])
        except Exception as exc:
            logger.info(
                "reason llm failed taskId=%s err=%s",
                state.get("task_id"),
                type(exc).__name__,
            )
            return {"error": f"model unavailable: {type(exc).__name__}"}
        return {"messages": [response]}

    async def tools_node(state: AgentState) -> dict[str, Any]:
        last = state["messages"][-1]
        if not isinstance(last, AIMessage) or not last.tool_calls:
            return {}
        recorded = list(state.get("tool_calls") or [])
        tool_messages: list[ToolMessage] = []
        for call in last.tool_calls:
            name = str(call.get("name") or "")
            args = call.get("args") if isinstance(call.get("args"), dict) else {}
            tool_call_id = str(call.get("id") or name)
            tool = tool_map.get(name)
            ok = True
            if tool is None:
                ok = False
                output = f"unknown tool: {name}"
            else:
                try:
                    output = _as_text(await tool.ainvoke(args))
                except Exception as exc:
                    ok = False
                    output = str(exc)
            snippet = _clip(output, _SNIPPET_LIMIT)
            recorded.append(
                {
                    "name": name,
                    "input": args,
                    "ok": ok,
                    "outputSnippet": snippet,
                }
            )
            tool_messages.append(
                ToolMessage(content=snippet or "(empty)", tool_call_id=tool_call_id)
            )
            logger.info(
                "tool taskId=%s name=%s ok=%s round=%s",
                state.get("task_id"),
                name,
                ok,
                int(state.get("tool_rounds") or 0) + 1,
            )
        return {
            "messages": tool_messages,
            "tool_calls": recorded,
            "tool_rounds": int(state.get("tool_rounds") or 0) + 1,
        }

    builder = StateGraph(AgentState)
    builder.add_node("fail_fast", fail_fast)
    builder.add_node("reason", reason)
    builder.add_node("tools", tools_node)
    builder.add_node("finalize", finalize)
    builder.add_edge(START, "fail_fast")
    builder.add_conditional_edges(
        "fail_fast",
        _route_after_fail_fast,
        {"reason": "reason", "__end__": END},
    )
    builder.add_conditional_edges(
        "reason",
        _route_after_reason,
        {"tools": "tools", "finalize": "finalize"},
    )
    builder.add_edge("tools", "reason")
    builder.add_edge("finalize", END)
    return builder.compile()


def _initial_state(instruction: AgentInstruction) -> AgentState:
    return {
        "instruction": instruction.instruction,
        "force_fail": instruction.force_fail,
        "task_id": "" if instruction.task_id is None else str(instruction.task_id),
        "messages": [],
        "tool_calls": [],
        "tool_rounds": 0,
        "summary": "",
        "final_answer": "",
        "error": None,
    }


def _raise_if_error(out: AgentState) -> None:
    err = out.get("error")
    if not err:
        return
    if err == "forceFail=true":
        raise ForceFailError(err)
    raise AgentRunError(err)


async def run_instruction(instruction: AgentInstruction) -> dict[str, Any]:
    started = time.perf_counter()
    if instruction.force_fail:
        out = await _build_graph([]).ainvoke(_initial_state(instruction))
        _raise_if_error(out)
        raise AgentRunError("forceFail did not set error")

    client = _create_mcp_client()
    async with client.session("async-forge") as session:
        tools = await load_mcp_tools(session, handle_tool_errors=False)
        _assert_expected_tools(tools)
        out = await _build_graph(tools).ainvoke(
            _initial_state(instruction),
            {"recursion_limit": 20},
        )

    _raise_if_error(out)
    duration_ms = int((time.perf_counter() - started) * 1000)
    result = AgentResult.model_validate(
        {
            "summary": out.get("summary") or "",
            "finalAnswer": out.get("final_answer") or "",
            "toolCalls": out.get("tool_calls") or [],
            "durationMs": duration_ms,
        }
    )
    logger.info(
        "graph done taskId=%s durationMs=%s tools=%s",
        instruction.task_id if instruction.task_id is not None else "",
        duration_ms,
        [item.name for item in result.tool_calls],
    )
    return result.model_dump(by_alias=True)
