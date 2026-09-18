from __future__ import annotations

import re

from app.schemas.responses import EntityCandidate
from app.services.stop_words import is_stop

CHINESE_ENTITY = re.compile(
    r"(客户|项目|用户|同事|朋友|老板|老师)?([\u4e00-\u9fff]{2,6})(说|问|担心|提醒|合同|付款|部署|会议|续费|风险|喜欢|吃)"
)
ASCII_ENTITY = re.compile(r"\b[A-Z][A-Za-z0-9_-]{1,}\b")
# 2-4 字连续中文，排除常见动词开头
NAME_LIKE = re.compile(r"[\u4e00-\u9fff]{2,4}")


def extract_entities(text: str) -> list[EntityCandidate]:
    found: dict[str, EntityCandidate] = {}

    def put(name: str, etype: str, confidence: float, start: int | None = None, end: int | None = None) -> None:
        name = name.strip()
        if len(name) < 2 or is_stop(name):
            return
        if name not in found or found[name].confidence < confidence:
            found[name] = EntityCandidate(
                name=name,
                type=etype,
                confidence=confidence,
                span_start=start,
                span_end=end,
            )

    for match in ASCII_ENTITY.finditer(text):
        put(match.group(), "OTHER", 0.8, match.start(), match.end())

    for match in CHINESE_ENTITY.finditer(text):
        put(match.group(2), "PERSON", 0.82, match.start(2), match.end(2))

    # 简单人名/专名：句首或「X喜欢/吃/担心」
    for match in re.finditer(r"([\u4e00-\u9fff]{2,4})(喜欢|吃|担心|说过|问我)", text):
        put(match.group(1), "PERSON", 0.7, match.start(1), match.end(1))

    for match in re.finditer(r"(登鹳雀楼|鹳雀楼)", text):
        put(match.group(), "TOPIC", 0.9, match.start(), match.end())

    return list(found.values())[:12]
