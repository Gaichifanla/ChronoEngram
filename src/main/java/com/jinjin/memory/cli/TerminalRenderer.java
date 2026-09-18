package com.jinjin.memory.cli;

import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.domain.SearchResult;
import com.jinjin.memory.domain.AssociativeRecallResult;
import com.jinjin.memory.domain.TimelineEntry;

import java.util.List;

public class TerminalRenderer {
    public void render(CommandResult result) {
        if (!result.message().isBlank()) {
            System.out.println(result.message());
        }

        Object payload = result.payload();
        if (payload instanceof MemoryEvent record) {
            renderMemory(record);
        } else if (payload instanceof List<?> values) {
            renderList(values);
        }
    }

    private void renderList(List<?> values) {
        if (values.isEmpty()) {
            return;
        }

        Object first = values.getFirst();
        if (first instanceof SearchResult) {
            renderSearchResults(values);
        } else if (first instanceof AssociativeRecallResult) {
            renderAssociativeResults(values);
        } else if (first instanceof MemoryEvent) {
            renderMemoryList(values);
        } else if (first instanceof TimelineEntry) {
            renderTimeline(values);
        }
    }

    private void renderTimeline(List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            TimelineEntry entry = (TimelineEntry) values.get(i);
            MemoryEvent event = entry.event();
            String relation = entry.relationToPrevious() == null ? "起点" : entry.relationToPrevious().name();
            System.out.printf("%d. [%s] %s  %s%n", i + 1, event.shortId(), event.eventType(), relation);
            System.out.println("   " + event.content());
            System.out.println("   发生：" + event.displayOccurredTime() + "  得知：" + event.displayLearnedTime());
        }
    }

    private void renderAssociativeResults(List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            AssociativeRecallResult result = (AssociativeRecallResult) values.get(i);
            MemoryEvent record = result.record();
            System.out.println();
            System.out.printf("%d. [%s] %s  activation=%.3f%n", i + 1, record.shortId(), record.title(), result.score());
            System.out.println("   " + record.content());
            System.out.println("   初始线索：" + String.join("、", result.cueUnits()));
            System.out.println("   扩散激活：" + String.join("、", result.activatedUnits()));
            if (!result.recallPath().isEmpty()) {
                System.out.println("   回忆路径：" + String.join(" -> ", result.recallPath()));
            }
            System.out.println("   原因：" + result.reason());
        }
    }

    private void renderSearchResults(List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            SearchResult result = (SearchResult) values.get(i);
            MemoryEvent record = result.record();
            System.out.println();
            System.out.printf("%d. [%s] %s  score=%.2f%n", i + 1, record.shortId(), record.title(), result.score());
            System.out.println("   " + result.snippet());
            System.out.println("   标签：" + String.join(", ", record.tags()));
            System.out.println("   匹配：" + result.reason());
            if (!result.recallPath().isEmpty()) {
                System.out.println("   回忆路径：" + result.recallPathDisplay());
            }
            System.out.println("   发生：" + record.displayOccurredTime() + "  得知：" + record.displayLearnedTime());
        }
    }

    private void renderMemoryList(List<?> values) {
        for (Object value : values) {
            MemoryEvent record = (MemoryEvent) value;
            System.out.printf("[%s] %s  发生:%s  得知:%s%n",
                    record.shortId(), record.title(), record.displayOccurredTime(), record.displayLearnedTime());
        }
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void renderMemory(MemoryEvent memory) {
        System.out.println("ID：" + memory.id());
        System.out.println("标题：" + memory.title());
        System.out.println("内容：" + memory.content());
        System.out.println("事件类型：" + memory.eventType());
        System.out.println("主题键：" + memory.topicKey());
        System.out.println("标签：" + String.join(", ", memory.tags()));
        System.out.println("实体：" + String.join(", ", memory.entities()));
        if (!memory.schemaLabel().isBlank() || !memory.themeLabel().isBlank() || !memory.conceptLabel().isBlank()) {
            System.out.printf(
                    "分层：%s -> %s -> %s%n",
                    blankToDash(memory.schemaLabel()),
                    blankToDash(memory.themeLabel()),
                    blankToDash(memory.conceptLabel())
            );
        }
        if (memory.hasEmbedding()) {
            System.out.println("向量：已生成（dim=" + memory.embedding().size() + "）");
        }
        System.out.println("发生时间：" + memory.displayOccurredTime()
                + "（" + memory.occurredTime().precision() + "，表达："
                + blankToDash(memory.occurredTime().originalExpression()) + "）");
        System.out.println("得知时间：" + memory.displayLearnedTime());
    }
}
