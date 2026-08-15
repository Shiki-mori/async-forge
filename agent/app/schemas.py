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


def stub_success_result() -> dict[str, Any]:
    """Fixed SUCCESS JSON for Java wiring (no LLM)."""
    return {
        "summary": "stub: skipped LLM; instruction accepted for Java wiring.",
        "finalAnswer": "stub success",
        "toolCalls": [],
        "durationMs": 0,
    }
