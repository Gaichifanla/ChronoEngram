import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.routes import router
from app.config import settings
from app.services import embedder

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


@asynccontextmanager
async def lifespan(_app: FastAPI):
    embedder.ensure_ready()
    yield


app = FastAPI(
    title="Jinjin Semantic Service",
    description="本地语义侧车：写入分析、查询理解、向量化",
    version="0.1.0",
    lifespan=lifespan,
)
app.include_router(router, prefix="/api/v1")


def run() -> None:
    import uvicorn

    uvicorn.run("app.main:app", host=settings.host, port=settings.port, reload=False)


if __name__ == "__main__":
    run()
