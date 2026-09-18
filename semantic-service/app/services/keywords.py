import re

import jieba

from app.services.stop_words import STOP_WORDS, is_stop
from app.services.text_normalize import normalize


def extract_keywords(text: str, max_terms: int = 12) -> list[str]:
    normalized = normalize(text)
    terms: list[str] = []
    seen: set[str] = set()

    def add(term: str) -> None:
        term = term.strip()
        if len(term) < 2 or is_stop(term) or term in seen:
            return
        seen.add(term)
        terms.append(term)

    for part in re.split(r"[^a-z0-9\u4e00-\u9fff]+", normalized):
        if part:
            add(part)

    for token in jieba.lcut(text, cut_all=False):
        token = token.strip()
        if len(token) >= 2 and not is_stop(token):
            add(token)

    for block in re.findall(r"[\u4e00-\u9fff]{2,}", text):
        if not is_stop(block):
            add(block)
        for size in range(min(4, len(block)), 1, -1):
            for i in range(0, len(block) - size + 1):
                add(block[i : i + size])

    return terms[:max_terms]


def title_hint(text: str, max_len: int = 24) -> str:
    trimmed = text.strip()
    if len(trimmed) <= max_len:
        return trimmed
    return trimmed[:max_len] + "..."
