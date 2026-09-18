package com.jinjin.memory.infrastructure.util;

public class TitleGenerator {
    private TitleGenerator() {
    }

    public static String title(String content) {
        String trimmed = content.strip();
        int end = Math.min(trimmed.length(), 24);
        String title = trimmed.substring(0, end);
        return end < trimmed.length() ? title + "..." : title;
    }
}
