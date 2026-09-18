from typing import List, Optional

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    """写入路径：从原文抽取分层记忆线索 + 向量。"""

    text: str = Field(min_length=1, description="用户原始输入")
    memory_id: Optional[str] = Field(default=None, description="Java 侧雪花 ID，便于对齐")
    source: str = Field(default="manual", description="来源：manual / import / seed")


class UnderstandRequest(BaseModel):
    """检索路径：查询理解与向量。"""

    query: str = Field(min_length=1, description="用户查询（问题/陈述/关键词）")
    limit: int = Field(default=8, ge=1, le=50)


class EmbedRequest(BaseModel):
    """批量向量化，供 Java 对已有节点补 embedding。"""

    texts: List[str] = Field(min_length=1, max_length=64)
    ids: Optional[List[str]] = Field(default=None, description="与 texts 一一对应的外部 ID")
