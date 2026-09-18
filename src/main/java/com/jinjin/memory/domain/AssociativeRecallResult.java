package com.jinjin.memory.domain;

import java.util.List;

public record AssociativeRecallResult(
        MemoryEvent record,
        double score,
        List<String> cueUnits,
        List<String> activatedUnits,
        List<String> recallPath,
        String reason
) {
}
