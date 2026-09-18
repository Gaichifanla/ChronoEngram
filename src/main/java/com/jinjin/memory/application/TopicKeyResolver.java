package com.jinjin.memory.application;

import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.infrastructure.util.TextNormalizer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TopicKeyResolver {
    private static final Pattern CORE_EVENT = Pattern.compile("([\\p{IsHan}A-Za-z0-9]{2,16}(?:离职|入职|结婚|毕业|生病|去世|发布|上线|下线|签约|付款|延期|取消))");
    private static final Pattern ABOUT = Pattern.compile("关于([^，。,.]{2,16})的");
    private static final Pattern TIME_WORDS = Pattern.compile("\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}日?|上周[一二三四五六日天]|前天|昨天|今天|刚刚|现在");
    private static final Pattern EPISTEMIC_WORDS = Pattern.compile("我才?知道|我得知|已经|后来|再次|HR|确认|证实|核实|纠正|更正|更新|回忆|想起|记起");

    public String resolve(MemoryEvent event) {
        String canonical = EPISTEMIC_WORDS.matcher(TIME_WORDS.matcher(event.content()).replaceAll("")).replaceAll("");
        Matcher core = CORE_EVENT.matcher(canonical);
        if (core.find()) {
            return TextNormalizer.normalize(core.group(1));
        }
        Matcher about = ABOUT.matcher(event.content());
        if (about.find()) {
            return TextNormalizer.normalize(about.group(1));
        }
        if (event.conceptLabel() != null && !event.conceptLabel().isBlank()) {
            return TextNormalizer.normalize(event.conceptLabel());
        }
        if (!event.entities().isEmpty()) {
            return TextNormalizer.normalize(event.entities().getFirst());
        }
        return TextNormalizer.normalize(event.title().replace("...", ""));
    }
}
