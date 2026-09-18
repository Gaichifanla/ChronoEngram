package com.jinjin.memory.infrastructure.temporal;

import com.jinjin.memory.domain.EventType;

public class EventClassifier {
    public EventType classify(String content) {
        if (containsAny(content, "确认", "证实", "核实")) {
            return EventType.CONFIRMATION;
        }
        if (containsAny(content, "纠正", "更正", "有误", "说错", "记错")) {
            return EventType.CORRECTION;
        }
        if (containsAny(content, "更新", "改为", "延期", "取消", "撤回", "变更")) {
            return EventType.UPDATE;
        }
        if (containsAny(content, "回忆", "想起", "记起")) {
            return EventType.RECALL;
        }
        if (containsAny(content, "完成", "参加", "去了", "购买", "提交", "执行")) {
            return EventType.ACTION;
        }
        return EventType.OBSERVATION;
    }

    private boolean containsAny(String content, String... words) {
        for (String word : words) {
            if (content.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
