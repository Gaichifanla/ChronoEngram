package com.jinjin.memory.infrastructure.tagging;

import com.jinjin.memory.infrastructure.util.StopWords;
import com.jinjin.memory.infrastructure.util.TextNormalizer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoTagger {
    private static final Map<String, List<String>> TAG_KEYWORDS = new LinkedHashMap<>();
    private static final Pattern ASCII_ENTITY = Pattern.compile("\\b[A-Z][A-Za-z0-9_-]{1,}\\b");
    private static final Pattern CHINESE_ENTITY = Pattern.compile("(客户|项目|用户|同事|朋友|老板|老师)?([\\p{IsHan}]{2,6})(说|问|担心|提醒|合同|付款|部署|会议|续费|风险)");

    static {
        TAG_KEYWORDS.put("提醒", List.of("提醒", "明天", "后天", "下周", "待办", "记得", "跟进"));
        TAG_KEYWORDS.put("客户", List.of("客户", "续费", "合同", "付款", "报价", "需求"));
        TAG_KEYWORDS.put("合同", List.of("合同", "付款", "账期", "周期", "签约", "续约"));
        TAG_KEYWORDS.put("风险", List.of("风险", "担心", "问题", "阻塞", "延期", "顾虑"));
        TAG_KEYWORDS.put("技术", List.of("部署", "服务器", "数据库", "接口", "代码", "系统", "模型"));
        TAG_KEYWORDS.put("学习", List.of("学习", "课程", "读书", "论文", "知识"));
        TAG_KEYWORDS.put("会议", List.of("会议", "沟通", "讨论", "纪要", "同步"));
        TAG_KEYWORDS.put("账号", List.of("账号", "密码", "token", "密钥", "权限"));
    }

    public List<String> tags(String content) {
        String normalized = TextNormalizer.normalize(content);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : TAG_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (normalized.contains(TextNormalizer.normalize(keyword))) {
                    tags.add(entry.getKey());
                    break;
                }
            }
        }
        if (tags.isEmpty()) {
            tags.add("未分类");
        }
        return List.copyOf(tags);
    }

    public List<String> entities(String content) {
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        Matcher asciiMatcher = ASCII_ENTITY.matcher(content);
        while (asciiMatcher.find()) {
            entities.add(asciiMatcher.group());
        }

        Matcher chineseMatcher = CHINESE_ENTITY.matcher(content);
        while (chineseMatcher.find()) {
            String entity = chineseMatcher.group(2);
            if (!StopWords.contains(entity)) {
                entities.add(entity);
            }
        }
        return List.copyOf(entities);
    }
}
