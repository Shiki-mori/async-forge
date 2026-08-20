from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


class AgentInstruction(BaseModel):
    """A2A user-message text part (JSON string)."""

    model_config = ConfigDict(populate_by_name=True)

    task_id: int | str | None = Field(default=None, alias="taskId")
    instruction: str
    force_fail: bool = Field(default=False, alias="forceFail")

    @field_validator("instruction")
    @classmethod
    def instruction_not_blank(cls, value: str) -> str:
        trimmed = value.strip()
        if not trimmed:
            raise ValueError("instruction must be non-empty")
        return trimmed


class ToolCallRecord(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    name: str
    input: dict[str, Any]
    ok: bool
    output_snippet: str = Field(alias="outputSnippet")


class AgentResult(BaseModel):
    """result_json written by finalize and returned on A2A Task completed."""

    model_config = ConfigDict(populate_by_name=True)

    summary: str
    final_answer: str = Field(alias="finalAnswer")
    tool_calls: list[ToolCallRecord] = Field(alias="toolCalls")
    duration_ms: int = Field(alias="durationMs")

    @field_validator("summary")
    @classmethod
    def summary_not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("summary must be non-empty")
        return value
