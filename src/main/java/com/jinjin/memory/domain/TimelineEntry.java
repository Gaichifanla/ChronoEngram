package com.jinjin.memory.domain;

public record TimelineEntry(
        MemoryEvent event,
        EventRelationType relationToPrevious
) {
}
