package com.jinjin.memory.infrastructure.associative;

import com.jinjin.memory.domain.MemoryUnitType;

public record EncodedUnit(MemoryUnitType type, String label, double strength) {
}
