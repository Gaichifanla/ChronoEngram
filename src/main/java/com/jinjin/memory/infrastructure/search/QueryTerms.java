package com.jinjin.memory.infrastructure.search;

import com.jinjin.memory.infrastructure.util.StopWords;
import com.jinjin.memory.infrastructure.util.TextNormalizer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record QueryTerms(String original, List<String> terms) {
    static QueryTerms from(String query) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String normalized = TextNormalizer.normalize(query);

        for (String part : normalized.split("[^\\p{IsHan}a-z0-9]+")) {
            if (part.length() >= 2 && !StopWords.contains(part)) {
                terms.add(part);
            }
        }

        Matcher chineseMatcher = Pattern.compile("\\p{IsHan}{2,}").matcher(normalized);
        while (chineseMatcher.find()) {
            String text = chineseMatcher.group();
            if (!StopWords.contains(text)) {
                terms.add(text);
            }
            for (int size = Math.min(4, text.length()); size >= 2; size--) {
                for (int i = 0; i <= text.length() - size; i++) {
                    String term = text.substring(i, i + size);
                    if (!StopWords.contains(term)) {
                        terms.add(term);
                    }
                }
            }
        }

        return new QueryTerms(query, List.copyOf(terms));
    }
}
