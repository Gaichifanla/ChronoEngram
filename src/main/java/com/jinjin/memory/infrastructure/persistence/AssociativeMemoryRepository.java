package com.jinjin.memory.infrastructure.persistence;

import com.jinjin.memory.domain.MemoryEngram;
import com.jinjin.memory.domain.MemoryUnit;
import com.jinjin.memory.domain.Synapse;

import java.io.IOException;
import java.util.List;

public interface AssociativeMemoryRepository {
    List<MemoryUnit> loadUnits() throws IOException;

    List<Synapse> loadSynapses() throws IOException;

    List<MemoryEngram> loadEngrams() throws IOException;

    void replaceAll(List<MemoryUnit> units, List<Synapse> synapses, List<MemoryEngram> engrams) throws IOException;

    void savePlasticState(List<MemoryUnit> units, List<Synapse> synapses) throws IOException;

    void appendEngrams(List<MemoryEngram> engrams) throws IOException;
}
