"""规则 + 关键词：为分层记忆图提供 Schema / Theme / Concept 候选。"""

from app.schemas.responses import LayerHint
from app.services.text_normalize import normalize

SCHEMA_RULES: dict[str, list[str]] = {
    "学习": ["学习", "课程", "老师", "课堂", "背诵", "古诗", "语文", "考试", "作业", "读书", "论文"],
    "工作": ["工作", "项目", "客户", "合同", "部署", "服务器", "接口", "会议", "需求", "上线", "同事"],
    "生活": ["生活", "购物", "出行", "旅行", "收纳", "清洁", "做饭", "家务"],
    "健康": ["健康", "运动", "睡眠", "喝水", "感冒", "用药", "医院", "肚子疼", "饮食"],
    "财务": ["财务", "记账", "付款", "账期", "续费", "报价", "预算"],
    "人际": ["朋友", "同事", "沟通", "老板", "客户", "张三", "李四"],
}

THEME_RULES: dict[str, list[str]] = {
    "古诗": ["古诗", "诗词", "登鹳雀楼", "背诵", "作者", "名句"],
    "合同": ["合同", "签约", "续约", "付款", "账期"],
    "部署": ["部署", "服务器", "上线", "发布", "环境"],
    "提醒": ["提醒", "待办", "跟进", "记得", "明天", "后天"],
    "生活常识": ["生活常识", "习惯", "检查", "经验"],
    "客户": ["客户", "续费", "报价", "需求"],
    "技术": ["代码", "数据库", "接口", "系统", "模型", "token"],
}

TAG_KEYWORDS: dict[str, list[str]] = {
    "提醒": ["提醒", "明天", "后天", "下周", "待办", "记得", "跟进"],
    "客户": ["客户", "续费", "合同", "付款", "报价", "需求"],
    "合同": ["合同", "付款", "账期", "周期", "签约", "续约"],
    "风险": ["风险", "担心", "问题", "阻塞", "延期", "顾虑"],
    "技术": ["部署", "服务器", "数据库", "接口", "代码", "系统", "模型"],
    "学习": ["学习", "课程", "读书", "论文", "知识"],
    "会议": ["会议", "沟通", "讨论", "纪要", "同步"],
    "账号": ["账号", "密码", "token", "密钥", "权限"],
}


def _match_rules(text: str, rules: dict[str, list[str]], layer: str, base_confidence: float) -> list[LayerHint]:
    normalized = normalize(text)
    hints: list[LayerHint] = []
    for label, keywords in rules.items():
        for kw in keywords:
            if normalize(kw) in normalized:
                hints.append(
                    LayerHint(
                        layer=layer,
                        label=label,
                        confidence=base_confidence,
                        reason=f"命中关键词「{kw}」",
                    )
                )
                break
    return hints


def schema_hints(text: str) -> list[LayerHint]:
    return _match_rules(text, SCHEMA_RULES, "SCHEMA", 0.75)


def theme_hints(text: str) -> list[LayerHint]:
    return _match_rules(text, THEME_RULES, "THEME", 0.7)


def tags(text: str) -> list[str]:
    normalized = normalize(text)
    found: list[str] = []
    for label, keywords in TAG_KEYWORDS.items():
        for kw in keywords:
            if normalize(kw) in normalized:
                found.append(label)
                break
    return found if found else ["未分类"]


def concept_hints(text: str, entities: list[str], keywords: list[str]) -> list[LayerHint]:
    hints: list[LayerHint] = []
    for name in entities:
        hints.append(
            LayerHint(layer="CONCEPT", label=name, confidence=0.85, reason="实体候选")
        )
    for kw in keywords:
        if len(kw) >= 2 and kw not in {h.label for h in hints}:
            hints.append(
                LayerHint(layer="CONCEPT", label=kw, confidence=0.55, reason="关键词候选")
            )
    return hints[:8]
