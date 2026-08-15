"""LangGraph pipeline. Step A returns a fixed JSON stub (no LLM)."""

from app.schemas import AgentInstruction, stub_success_result


class ForceFailError(Exception):
    """Raised when payload.forceFail is true."""


def run_stub(instruction: AgentInstruction) -> dict:
    if instruction.force_fail:
        raise ForceFailError("forceFail=true")
    return stub_success_result()
