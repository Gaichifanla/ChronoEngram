package com.jinjin.memory.infrastructure.semantic;

import java.util.List;

public record EmbedItem(String id, List<Double> embedding) {
}
