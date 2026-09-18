import re


def normalize(text: str) -> str:
    if not text:
        return ""
    t = text.strip().lower()
    t = (
        t.replace("，", ",")
        .replace("。", ".")
        .replace("？", "?")
        .replace("！", "!")
        .replace("：", ":")
        .replace("；", ";")
    )
    return re.sub(r"\s+", "", t)
