package com.jinjin.memory.infrastructure.associative;

import com.jinjin.memory.domain.AssociativeRecallResult;
import com.jinjin.memory.domain.AssociativeMemoryStats;
import com.jinjin.memory.domain.MemoryEngram;
import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.domain.MemoryUnit;
import com.jinjin.memory.domain.MemoryUnitType;
import com.jinjin.memory.domain.Synapse;
import com.jinjin.memory.infrastructure.persistence.AssociativeMemoryRepository;
import com.jinjin.memory.infrastructure.util.SnowflakeIdGenerator;
import com.jinjin.memory.infrastructure.util.TextNormalizer;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AssociativeMemoryEngine {
    private static final int PROPAGATION_ROUNDS = 2;
    private static final int ACTIVE_UNIT_LIMIT = 64;

    private final AssociativeMemoryRepository repository;
    private final SparseUnitEncoder encoder;
    private final SnowflakeIdGenerator idGenerator = SnowflakeIdGenerator.defaultGenerator();

    public AssociativeMemoryEngine(AssociativeMemoryRepository repository, SparseUnitEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    public void learn(MemoryEvent record) throws IOException {
        learn(List.of(record));
    }

    public void learn(List<MemoryEvent> records) throws IOException {
        TrainingState state = loadTrainingState();
        List<MemoryEngram> newEngrams = train(records, state);
        repository.savePlasticState(List.copyOf(state.unitsById.values()), List.copyOf(state.synapsesByKey.values()));
        repository.appendEngrams(newEngrams);
    }

    public int rebuild(List<MemoryEvent> records, ProgressListener listener) throws IOException {
        TrainingState state = new TrainingState(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
        List<MemoryEngram> engrams = new ArrayList<>(records.size());
        int batchSize = 2000;
        for (int offset = 0; offset < records.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, records.size());
            engrams.addAll(train(records.subList(offset, end), state));
            if (listener != null) {
                listener.onProgress(end, records.size());
            }
        }
        repository.replaceAll(List.copyOf(state.unitsById.values()), List.copyOf(state.synapsesByKey.values()), engrams);
        return engrams.size();
    }

    public List<AssociativeRecallResult> recall(String query, List<MemoryEvent> records, int limit) throws IOException {
        List<MemoryUnit> units = repository.loadUnits();
        if (units.isEmpty()) {
            return List.of();
        }

        Map<String, MemoryUnit> unitsById = units.stream().collect(Collectors.toMap(MemoryUnit::id, Function.identity()));
        Map<String, MemoryUnit> unitsByLabel = units.stream().collect(Collectors.toMap(
                unit -> TextNormalizer.normalize(unit.label()),
                Function.identity(),
                (left, right) -> left
        ));
        List<EncodedUnit> encodedCues = encoder.encodeQuery(query, units);
        Map<String, Double> activations = new HashMap<>();
        List<String> cueLabels = new ArrayList<>();
        for (EncodedUnit cue : encodedCues) {
            MemoryUnit unit = unitsByLabel.get(TextNormalizer.normalize(cue.label()));
            if (unit == null) {
                continue;
            }
            activations.merge(unit.id(), cue.strength() * rarity(unit), Math::max);
            cueLabels.add(unit.label());
        }
        if (activations.isEmpty()) {
            return List.of();
        }

        List<Synapse> synapses = repository.loadSynapses();
        Map<String, List<Neighbor>> adjacency = adjacency(synapses);
        for (int round = 0; round < PROPAGATION_ROUNDS; round++) {
            activations = propagate(activations, adjacency, unitsById);
        }
        Map<String, Double> finalActivations = activations;

        Map<String, MemoryEvent> recordsById = records.stream().collect(Collectors.toMap(
                MemoryEvent::id,
                Function.identity(),
                (left, right) -> left
        ));
        List<MemoryEngram> engrams = repository.loadEngrams();
        List<ScoredEngram> scored = new ArrayList<>();
        for (MemoryEngram engram : engrams) {
            MemoryEvent record = recordsById.get(engram.traceId());
            if (record == null || record.deleted()) {
                continue;
            }
            double score = score(engram, finalActivations);
            if (score > 0.02) {
                scored.add(new ScoredEngram(engram, record, score));
            }
        }

        List<ScoredEngram> winners = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredEngram::score).reversed()
                        .thenComparing(item -> item.record().learnedAt(), Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        List<AssociativeRecallResult> results = new ArrayList<>();
        for (ScoredEngram winner : winners) {
            List<MemoryUnit> activeInEngram = activeUnits(winner.engram(), finalActivations, unitsById);
            List<String> activatedLabels = activeInEngram.stream()
                    .sorted(Comparator.comparingDouble((MemoryUnit unit) -> finalActivations.getOrDefault(unit.id(), 0.0)).reversed())
                    .map(MemoryUnit::label)
                    .distinct()
                    .limit(8)
                    .toList();
            List<String> path = activeInEngram.stream()
                    .sorted(Comparator.comparingInt(unit -> pathOrder(unit.type())))
                    .map(MemoryUnit::label)
                    .distinct()
                    .limit(6)
                    .toList();
            String reason = "线索激活“" + String.join("、", cueLabels.stream().distinct().limit(4).toList())
                    + "”，扩散后命中“" + String.join("、", activatedLabels) + "”";
            results.add(new AssociativeRecallResult(
                    winner.record(),
                    winner.score(),
                    cueLabels.stream().distinct().toList(),
                    activatedLabels,
                    path,
                    reason
            ));
        }

        if (!winners.isEmpty()) {
            reinforce(winners.getFirst().engram(), finalActivations, unitsById, synapses);
        }
        return results;
    }

    public boolean hasIndex() throws IOException {
        return !repository.loadUnits().isEmpty();
    }

    public AssociativeMemoryStats stats() throws IOException {
        return new AssociativeMemoryStats(
                repository.loadUnits().size(),
                repository.loadSynapses().size(),
                repository.loadEngrams().size()
        );
    }

    private List<MemoryEngram> train(List<MemoryEvent> records, TrainingState state) {
        List<MemoryEngram> engrams = new ArrayList<>();
        for (MemoryEvent record : records) {
            if (record.deleted()) {
                continue;
            }
            List<EncodedUnit> encoded = encoder.encodeRecord(record);
            if (encoded.isEmpty()) {
                continue;
            }

            List<String> unitIds = new ArrayList<>();
            List<Double> strengths = new ArrayList<>();
            for (EncodedUnit value : encoded) {
                String labelKey = TextNormalizer.normalize(value.label());
                MemoryUnit unit = state.unitsByLabel.get(labelKey);
                if (unit == null) {
                    Instant now = Instant.now();
                    unit = new MemoryUnit(
                            idGenerator.nextIdString(),
                            value.type(),
                            value.label(),
                            1,
                            0,
                            0.45,
                            now,
                            now
                    );
                } else {
                    long occurrences = unit.occurrenceCount() + 1;
                    unit = new MemoryUnit(
                            unit.id(),
                            unit.type(),
                            unit.label(),
                            occurrences,
                            unit.activationCount(),
                            Math.min(0.99, 0.45 + Math.log1p(occurrences) / 12.0),
                            unit.createdAt(),
                            unit.lastActivatedAt()
                    );
                }
                state.unitsByLabel.put(labelKey, unit);
                state.unitsById.put(unit.id(), unit);
                unitIds.add(unit.id());
                strengths.add(value.strength());
            }

            strengthenCoActivations(unitIds, state.synapsesByKey);
            engrams.add(new MemoryEngram(
                    record.id(),
                    record.id(),
                    List.copyOf(unitIds),
                    List.copyOf(strengths),
                    record.learnedAt()
            ));
        }
        return engrams;
    }

    private void strengthenCoActivations(List<String> unitIds, Map<String, Synapse> synapses) {
        List<String> unique = unitIds.stream().distinct().sorted().toList();
        Instant now = Instant.now();
        for (int i = 0; i < unique.size(); i++) {
            for (int j = i + 1; j < unique.size(); j++) {
                String from = unique.get(i);
                String to = unique.get(j);
                String key = synapseKey(from, to);
                Synapse current = synapses.get(key);
                long count = current == null ? 1 : current.coActivationCount() + 1;
                double weight = Math.min(0.97, 0.10 + 0.84 * (1.0 - Math.exp(-count / 5.0)));
                synapses.put(key, new Synapse(from, to, weight, count, now));
            }
        }
    }

    private Map<String, Double> propagate(
            Map<String, Double> current,
            Map<String, List<Neighbor>> adjacency,
            Map<String, MemoryUnit> unitsById
    ) {
        Map<String, Double> next = new HashMap<>();
        current.forEach((unitId, activation) -> next.merge(unitId, activation * 0.64, Double::sum));
        for (Map.Entry<String, Double> active : current.entrySet()) {
            for (Neighbor neighbor : adjacency.getOrDefault(active.getKey(), List.of())) {
                MemoryUnit target = unitsById.get(neighbor.unitId());
                if (target == null) {
                    continue;
                }
                double contribution = active.getValue() * neighbor.weight() * 0.58 * rarity(target);
                if (contribution >= 0.015) {
                    next.merge(target.id(), contribution, Double::sum);
                }
            }
        }
        return next.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(ACTIVE_UNIT_LIMIT)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Math.min(1.5, entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private double score(MemoryEngram engram, Map<String, Double> activations) {
        double score = 0.0;
        double norm = 0.0;
        for (int i = 0; i < engram.unitIds().size(); i++) {
            double strength = i < engram.strengths().size() ? engram.strengths().get(i) : 1.0;
            norm += strength * strength;
            score += activations.getOrDefault(engram.unitIds().get(i), 0.0) * strength;
        }
        return norm == 0.0 ? 0.0 : score / Math.sqrt(norm);
    }

    private List<MemoryUnit> activeUnits(
            MemoryEngram engram,
            Map<String, Double> activations,
            Map<String, MemoryUnit> unitsById
    ) {
        List<MemoryUnit> values = new ArrayList<>();
        for (String unitId : engram.unitIds()) {
            if (activations.getOrDefault(unitId, 0.0) >= 0.02 && unitsById.containsKey(unitId)) {
                values.add(unitsById.get(unitId));
            }
        }
        return values;
    }

    private void reinforce(
            MemoryEngram winner,
            Map<String, Double> activations,
            Map<String, MemoryUnit> unitsById,
            List<Synapse> synapses
    ) throws IOException {
        Instant now = Instant.now();
        Set<String> activeIds = new LinkedHashSet<>();
        for (String unitId : winner.unitIds()) {
            if (activations.getOrDefault(unitId, 0.0) >= 0.04) {
                activeIds.add(unitId);
                MemoryUnit unit = unitsById.get(unitId);
                if (unit != null) {
                    unitsById.put(unitId, new MemoryUnit(
                            unit.id(), unit.type(), unit.label(), unit.occurrenceCount(),
                            unit.activationCount() + 1,
                            Math.min(0.999, unit.stability() + 0.002),
                            unit.createdAt(), now
                    ));
                }
            }
        }

        List<Synapse> strengthened = synapses.stream().map(synapse -> {
            if (activeIds.contains(synapse.fromUnitId()) && activeIds.contains(synapse.toUnitId())) {
                return new Synapse(
                        synapse.fromUnitId(), synapse.toUnitId(),
                        Math.min(0.995, synapse.weight() + (1.0 - synapse.weight()) * 0.02),
                        synapse.coActivationCount() + 1,
                        now
                );
            }
            return synapse;
        }).toList();
        repository.savePlasticState(List.copyOf(unitsById.values()), strengthened);
    }

    private TrainingState loadTrainingState() throws IOException {
        Map<String, MemoryUnit> unitsById = repository.loadUnits().stream()
                .collect(Collectors.toMap(MemoryUnit::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, MemoryUnit> unitsByLabel = unitsById.values().stream()
                .collect(Collectors.toMap(
                        unit -> TextNormalizer.normalize(unit.label()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, Synapse> synapses = repository.loadSynapses().stream()
                .collect(Collectors.toMap(
                        synapse -> synapseKey(synapse.fromUnitId(), synapse.toUnitId()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return new TrainingState(unitsById, unitsByLabel, synapses);
    }

    private Map<String, List<Neighbor>> adjacency(List<Synapse> synapses) {
        Map<String, List<Neighbor>> adjacency = new HashMap<>();
        for (Synapse synapse : synapses) {
            adjacency.computeIfAbsent(synapse.fromUnitId(), ignored -> new ArrayList<>())
                    .add(new Neighbor(synapse.toUnitId(), synapse.weight()));
            adjacency.computeIfAbsent(synapse.toUnitId(), ignored -> new ArrayList<>())
                    .add(new Neighbor(synapse.fromUnitId(), synapse.weight()));
        }
        return adjacency;
    }

    private double rarity(MemoryUnit unit) {
        return 1.0 / (1.0 + Math.log1p(unit.occurrenceCount()) / 3.5);
    }

    private int pathOrder(MemoryUnitType type) {
        return switch (type) {
            case SCHEMA -> 0;
            case THEME -> 1;
            case CONCEPT -> 2;
            case ENTITY -> 3;
            case FEATURE -> 4;
        };
    }

    private String synapseKey(String left, String right) {
        return left.compareTo(right) <= 0 ? left + ":" + right : right + ":" + left;
    }

    public interface ProgressListener {
        void onProgress(int done, int total);
    }

    private record Neighbor(String unitId, double weight) {
    }

    private record ScoredEngram(MemoryEngram engram, MemoryEvent record, double score) {
    }

    private record TrainingState(
            Map<String, MemoryUnit> unitsById,
            Map<String, MemoryUnit> unitsByLabel,
            Map<String, Synapse> synapsesByKey
    ) {
    }
}
