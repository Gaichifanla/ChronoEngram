from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="JINJIN_SEMANTIC_", env_file=".env", extra="ignore")

    host: str = "127.0.0.1"
    port: int = 8710
    # 中文轻量 embedding；无 GPU 时 CPU 即可
    embedding_model: str = "BAAI/bge-small-zh-v1.5"
    # none = 不加载 transformers，仅用规则 + jieba（便于 CI / 无网环境）
    embedding_backend: str = "auto"
    max_text_length: int = 8000
    default_top_k: int = 8


settings = Settings()
