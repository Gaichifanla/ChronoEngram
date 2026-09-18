package com.jinjin.memory.infrastructure.associative;

import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.domain.MemoryUnit;
import com.jinjin.memory.domain.MemoryUnitType;
import com.jinjin.memory.infrastructure.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SparseUnitEncoder {
    private static final Pattern ABOUT_PATTERN = Pattern.compile("关于([^，。,.]{2,16})的");
    private static final Pattern BOOK_PATTERN = Pattern.compile("《([^》]{2,20})》");
    private static final List<String> FEATURE_VOCABULARY = List.of(
            "古诗", "语文", "登鹳雀楼", "王之涣", "作者", "全文", "第一句", "背诵", "课堂", "老师",
            "合同", "付款", "客户", "部署", "服务器", "数据库", "接口", "权限", "会议", "项目",
            "饮水", "睡眠", "运动", "早餐", "蔬菜", "水果", "牙齿", "洗手", "厨房", "冰箱",
            "雨天", "出门", "手机", "电脑", "阅读", "学习", "记账", "时间管理", "交通", "安全",
            "感冒", "用药", "眼睛", "坐姿", "沟通", "购物", "收纳", "清洁", "旅行", "生活常识"
    );
    private static final Map<String, String> QUERY_ASSOCIATIONS = Map.of(
            "内容", "全文",
            "诗句", "全文",
            "谁写", "作者",
            "写过", "作者",
            "背错", "第一句",
            "上课", "课堂"
    );

    public List<EncodedUnit> encodeRecord(MemoryEvent record) {
        LinkedHashMap<String, EncodedUnit> units = new LinkedHashMap<>();
        add(units, MemoryUnitType.SCHEMA, inferSchema(record), 0.62);
        add(units, MemoryUnitType.THEME, inferTheme(record), 0.78);
        add(units, MemoryUnitType.CONCEPT, record.topicKey(), 1.0);
        add(units, MemoryUnitType.CONCEPT, record.conceptLabel(), 1.0);

        for (String entity : record.entities()) {
            add(units, MemoryUnitType.ENTITY, entity, 0.9);
        }
        for (String tag : record.tags()) {
            add(units, MemoryUnitType.FEATURE, tag, 0.68);
        }

        String content = record.content();
        Matcher about = ABOUT_PATTERN.matcher(content);
        if (about.find()) {
            add(units, MemoryUnitType.CONCEPT, about.group(1), 0.92);
        }
        Matcher book = BOOK_PATTERN.matcher(content);
        while (book.find()) {
            add(units, MemoryUnitType.CONCEPT, book.group(1), 0.95);
        }
        for (String feature : FEATURE_VOCABULARY) {
            if (content.contains(feature)) {
                add(units, MemoryUnitType.FEATURE, feature, 0.72);
            }
        }

        if (units.isEmpty()) {
            add(units, MemoryUnitType.CONCEPT, conciseTitle(record.title()), 0.75);
        }
        return List.copyOf(units.values());
    }

    private String inferSchema(MemoryEvent record) {
        if (record.schemaLabel() != null && !record.schemaLabel().isBlank()) {
            return record.schemaLabel();
        }
        String content = record.content();
        if (containsAny(content, "老师", "学习", "古诗", "课堂", "背诵", "阅读")) {
            return "学习";
        }
        if (containsAny(content, "服务器", "数据库", "部署", "客户", "合同", "项目", "会议")) {
            return "工作";
        }
        if (containsAny(content, "睡眠", "运动", "感冒", "用药", "眼睛", "健康")) {
            return "健康";
        }
        if (containsAny(content, "付款", "预算", "记账", "财务")) {
            return "财务";
        }
        if (containsAny(content, "朋友", "同事", "沟通", "家人")) {
            return "人际";
        }
        return "生活";
    }

    private String inferTheme(MemoryEvent record) {
        if (record.themeLabel() != null && !record.themeLabel().isBlank()) {
            return record.themeLabel();
        }
        if (containsAny(record.content(), "古诗", "登鹳雀楼", "王之涣")) {
            return "古诗";
        }
        if (record.content().contains("生活常识")) {
            return "生活常识";
        }
        return record.tags().stream()
                .filter(tag -> !tag.isBlank() && !"未分类".equals(tag))
                .findFirst()
                .orElse("日常记忆");
    }

    public List<EncodedUnit> encodeQuery(String query, List<MemoryUnit> knownUnits) {
        String normalizedQuery = TextNormalizer.normalize(query);
        Map<String, EncodedUnit> cues = new LinkedHashMap<>();

        for (MemoryUnit unit : knownUnits) {
            String normalizedLabel = TextNormalizer.normalize(unit.label());
            if (normalizedLabel.length() >= 2 && normalizedQuery.contains(normalizedLabel)) {
                add(cues, unit.type(), unit.label(), cueStrength(unit.type()));
            }
        }
        for (Map.Entry<String, String> association : QUERY_ASSOCIATIONS.entrySet()) {
            if (!query.contains(association.getKey())) {
                continue;
            }
            knownUnits.stream()
                    .filter(unit -> unit.label().equals(association.getValue()))
                    .findFirst()
                    .ifPresent(unit -> add(cues, unit.type(), unit.label(), 0.76));
        }

        if (cues.size() < 3) {
            knownUnits.stream()
                    .map(unit -> Map.entry(unit, bigramSimilarity(normalizedQuery, TextNormalizer.normalize(unit.label()))))
                    .filter(entry -> entry.getValue() >= 0.34)
                    .sorted(Map.Entry.<MemoryUnit, Double>comparingByValue(Comparator.reverseOrder()))
                    .limit(8 - cues.size())
                    .forEach(entry -> add(cues, entry.getKey().type(), entry.getKey().label(), entry.getValue() * 0.72));
        }
        return new ArrayList<>(cues.values());
    }

    private void add(Map<String, EncodedUnit> units, MemoryUnitType type, String rawLabel, double strength) {
        if (rawLabel == null) {
            return;
        }
        String label = rawLabel.trim().replace("...", "");
        if (label.isBlank() || "未分类".equals(label) || label.length() > 32) {
            return;
        }
        String key = TextNormalizer.normalize(label);
        EncodedUnit current = units.get(key);
        if (current == null || strength > current.strength()) {
            units.put(key, new EncodedUnit(type, label, strength));
        }
    }

    private double cueStrength(MemoryUnitType type) {
        return switch (type) {
            case CONCEPT, ENTITY -> 1.0;
            case FEATURE -> 0.82;
            case THEME -> 0.72;
            case SCHEMA -> 0.58;
        };
    }

    private String conciseTitle(String title) {
        String value = title == null ? "" : title.trim();
        int punctuation = value.indexOf('：');
        if (punctuation >= 0 && punctuation + 1 < value.length()) {
            value = value.substring(punctuation + 1);
        }
        return value.length() <= 16 ? value : value.substring(0, 16);
    }

    private double bigramSimilarity(String left, String right) {
        if (left.length() < 2 || right.length() < 2) {
            return left.equals(right) ? 1.0 : 0.0;
        }
        List<String> leftPairs = bigrams(left);
        List<String> rightPairs = bigrams(right);
        long intersection = leftPairs.stream().distinct().filter(rightPairs::contains).count();
        long union = java.util.stream.Stream.concat(leftPairs.stream(), rightPairs.stream()).distinct().count();
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private List<String> bigrams(String value) {
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < value.length() - 1; i++) {
            pairs.add(value.substring(i, i + 2));
        }
        return pairs;
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
