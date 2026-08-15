from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.a2a_server import mount_a2a
from app.config import get_settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield
    handler = getattr(app.state, "a2a_handler", None)
    if handler is not None:
        await handler.aclose()


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title="async-forge-agent", lifespan=lifespan)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    mount_a2a(app, settings)
    return app


app = create_app()
