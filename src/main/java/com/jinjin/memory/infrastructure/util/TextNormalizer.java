package com.jinjin.memory.infrastructure.util;

import java.util.Locale;

public class TextNormalizer {
    private TextNormalizer() {
    }

    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replace('，', ',')
                .replace('。', '.')
                .replace('？', '?')
                .replace('！', '!')
                .replaceAll("\\s+", "");
    }
}
