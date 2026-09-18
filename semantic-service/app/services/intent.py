import re

from app.services.text_normalize import normalize


def detect_intent(query: str) -> str:
    q = normalize(query)
    if re.search(r"(谁|哪位|什么人)", query):
        return "RECALL_WHO"
    if re.search(r"(什么时候|何时|哪天|上周|昨天|最近)", query):
        return "RECALL_WHEN"
    if re.search(r"(内容|全文|怎么说|说过什么|背过|哪句|首句|作者)", query):
        return "RECALL_CONTENT"
    if re.search(r"(是什么|多少|哪里|哪儿|为什么|怎么)", query) or "?" in query or "？" in query:
        return "RECALL_FACT"
    if len(q) <= 12 and " " not in query:
        return "KEYWORD_LOOKUP"
    return "UNKNOWN"
