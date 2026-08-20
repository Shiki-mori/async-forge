"""OpenAI-compatible Chat Completions client. No business Chain wrapper."""

from langchain_openai import ChatOpenAI

from app.config import get_settings


class LlmConfigError(RuntimeError):
    """Raised when AI_BASE_URL / AI_API_KEY / AI_MODEL are missing."""


def get_chat_model() -> ChatOpenAI:
    settings = get_settings()
    base_url = settings.ai_base_url.strip()
    api_key = settings.ai_api_key.strip()
    model = settings.ai_model.strip()
    if not base_url or not api_key or not model:
        raise LlmConfigError("AI_BASE_URL, AI_API_KEY, and AI_MODEL are required")
    return ChatOpenAI(
        model=model,
        api_key=api_key,
        base_url=base_url,
        temperature=0,
        timeout=45,
        max_retries=1,
        max_tokens=512,
    )
