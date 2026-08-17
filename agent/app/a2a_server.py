"""Official a2a-sdk JSON-RPC server (no custom /api/agent/run)."""

from __future__ import annotations

import json
import logging
from typing import Any

from a2a.server.agent_execution.agent_executor import AgentExecutor
from a2a.server.agent_execution.context import RequestContext
from a2a.server.events.event_queue import EventQueue
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.routes import (
    add_a2a_routes_to_fastapi,
    create_agent_card_routes,
    create_jsonrpc_routes,
)
from a2a.server.tasks.inmemory_task_store import InMemoryTaskStore
from a2a.server.tasks.task_updater import TaskUpdater
from a2a.types import (
    AgentCapabilities,
    AgentCard,
    AgentInterface,
    AgentSkill,
    Part,
    Task,
    TaskState,
    TaskStatus,
)
from fastapi import FastAPI

from app.config import Settings
from app.graph import ForceFailError, run_stub
from app.schemas import AgentInstruction


# 相当于Java中的`LoggerFactory.getLogger(A2aServer.class)`
logger = logging.getLogger(__name__)

_INSTRUCTION_LOG_LIMIT = 200


# 截断文本，如果文本长度超过限制，则截断并添加省略号
def _truncate(text: str, limit: int = _INSTRUCTION_LOG_LIMIT) -> str:
    collapsed = " ".join(text.split())
    if len(collapsed) <= limit:
        return collapsed
    return collapsed[:limit] + "..."


def build_agent_card(settings: Settings) -> AgentCard:
    return AgentCard(
        name="async-forge-agent",
        description="执行自然语言指令，可通过 MCP 工具访问受限 HTTP GET 与四则运算",
        version="0.1.0",
        capabilities=AgentCapabilities(streaming=False, push_notifications=False),
        default_input_modes=["text"],
        default_output_modes=["text", "task-status"],
        skills=[
            AgentSkill(
                id="run-instruction",
                name="run-instruction",
                description="Run a natural-language instruction via MCP tools",
                tags=["instruction", "http_get", "calculator"],
                examples=[
                    "GET https://httpbin.org/json ，告诉我 HTTP 状态码以及返回 JSON 的顶层字段名。"
                ],
                input_modes=["text"],
                output_modes=["text", "task-status"],
            )
        ],
        supported_interfaces=[
            AgentInterface(
                protocol_binding="JSONRPC",
                protocol_version="1.0",
                url=settings.agent_jsonrpc_url,
            )
        ],
    )


class StubAgentExecutor(AgentExecutor):
    async def execute(
        self, context: RequestContext, event_queue: EventQueue
    ) -> None:
        """
        context: 请求上下文（用户消息、任务ID等）
        event_queue: 事件队列。SDK据此组装最终HTTP响应。
        """

        task_id = context.task_id or ""
        context_id = context.context_id or ""
        user_message = context.message
        raw_text = context.get_user_input()

        if user_message is not None:
            await event_queue.enqueue_event(
            # 阻塞式 SendMessage 需要事件流中先有一个 Task
                Task(
                    id=task_id,
                    context_id=context_id,
                    status=TaskStatus(state=TaskState.TASK_STATE_SUBMITTED),
                    history=[user_message],
                )
            )

        updater = TaskUpdater(
            event_queue=event_queue,
            task_id=task_id,
            context_id=context_id,
        )
        # 启动任务，状态变为 working
        await updater.start_work()

        try:
            # 将字符串转为dict，然后按DTO校验，转为AgentInstruction对象
            payload = AgentInstruction.model_validate(json.loads(raw_text))
        except (json.JSONDecodeError, ValueError) as exc:
            logger.warning(
                "invalid A2A payload taskId=%s instruction=%s err=%s",
                task_id,
                _truncate(raw_text),
                exc,
            )
            await updater.failed(
                message=updater.new_agent_message(
                    parts=[Part(text=f"invalid payload: {exc}")]
                )
            )
            return

        # 记录日志
        logger.info(
            "agent stub taskId=%s instruction=%s forceFail=%s",
            payload.task_id if payload.task_id is not None else task_id,
            _truncate(payload.instruction),
            payload.force_fail,
        )

        try:
            result: dict[str, Any] = run_stub(payload)
        except ForceFailError as exc:
            await updater.failed(
                message=updater.new_agent_message(parts=[Part(text=str(exc))])
            )
            return

        # 将结果由dict转为JSON字符串
        result_json = json.dumps(result, ensure_ascii=False)
        await updater.add_artifact(
            # 将JSON添加到Task
            parts=[Part(text=result_json)],
            name="result_json",
            last_chunk=True,
        )
        await updater.complete(
            message=updater.new_agent_message(parts=[Part(text=result_json)])
        )

    # A2A协议要求必须实现cancel方法
    async def cancel(
        self, context: RequestContext, event_queue: EventQueue
    ) -> None:
        updater = TaskUpdater(
            event_queue=event_queue,
            task_id=context.task_id or "",
            context_id=context.context_id or "",
        )
        await updater.cancel()


def mount_a2a(app: FastAPI, settings: Settings) -> DefaultRequestHandler:
    agent_card = build_agent_card(settings)
    request_handler = DefaultRequestHandler(
        agent_executor=StubAgentExecutor(),
        task_store=InMemoryTaskStore(),
        agent_card=agent_card,
    )
    add_a2a_routes_to_fastapi(
        app,
        agent_card_routes=create_agent_card_routes(agent_card),
        jsonrpc_routes=create_jsonrpc_routes(request_handler, rpc_url="/"),
    )
    app.state.a2a_handler = request_handler
    return request_handler
