from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    agent_jsonrpc_url: str = "http://localhost:8081"
    ai_base_url: str = ""
    ai_api_key: str = ""
    ai_model: str = ""


@lru_cache
def get_settings() -> Settings:
    return Settings()
