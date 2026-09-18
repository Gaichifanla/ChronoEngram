package com.jinjin.memory.infrastructure.util;

import java.util.Set;

public class StopWords {
    private static final Set<String> WORDS = Set.of(
            "什么", "之前", "以前", "哪个", "哪些", "一个", "一下", "这个", "那个", "我们", "他们",
            "怎么", "为什么", "是不是", "有没有", "相关", "信息", "内容", "记忆", "数据", "想要",
            "的", "了", "呢", "吗", "啊"
    );

    private StopWords() {
    }

    public static boolean contains(String word) {
        return WORDS.contains(word);
    }
}
