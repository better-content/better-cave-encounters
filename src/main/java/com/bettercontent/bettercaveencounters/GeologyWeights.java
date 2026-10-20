package com.bettercontent.bettercaveencounters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Pure probability algebra. Roster length must not change a habitat's geological share. */
final class GeologyWeights {
    static final int WEIGHT_BASIS = 10_000;
    static final int MINIMUM_SAMPLES = 8;
    static final int[] OFFSETS = {-4, -2, 0, 2, 4};
    private GeologyWeights() {}

    static <H, E> Map<E, Double> combine(Map<H, Integer> votes, Function<H, Map<E, Integer>> rosters) {
        Map<H, Map<E, Integer>> eligible = new LinkedHashMap<>();
        votes.forEach((habitat, count) -> {
            Map<E, Integer> entries = rosters.apply(habitat);
            if (count > 0 && entries.values().stream().anyMatch(weight -> weight > 0)) eligible.put(habitat, entries);
        });
        double totalVotes = eligible.keySet().stream().mapToInt(votes::get).sum();
        Map<E, Double> result = new LinkedHashMap<>();
        if (totalVotes == 0) return result;
        eligible.forEach((habitat, entries) -> {
            double totalWeight = entries.values().stream().filter(weight -> weight > 0).mapToInt(Integer::intValue).sum();
            entries.forEach((entity, weight) -> {
                if (weight > 0) result.merge(entity, votes.get(habitat) / totalVotes * weight / totalWeight, Double::sum);
            });
        });
        return result;
    }

    /** Largest-remainder allocation with registry-ID/string-order tie breaking. */
    static <T> Map<T, Integer> integerWeights(Map<T, Double> probabilities) {
        if (probabilities.values().stream().anyMatch(p -> !Double.isFinite(p)))
            throw new IllegalArgumentException("Non-finite ordinary probability");
        List<T> keys = probabilities.entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey)
                .sorted(Comparator.comparing(Object::toString)).toList();
        if (keys.size() > WEIGHT_BASIS) throw new IllegalArgumentException("Too many ordinary entries");
        Map<T, Integer> result = new LinkedHashMap<>();
        if (keys.isEmpty()) return result;
        double total = keys.stream().mapToDouble(probabilities::get).sum();
        int distributable = WEIGHT_BASIS;
        List<Remainder<T>> remainders = new ArrayList<>();
        int used = 0;
        for (int index = 0; index < keys.size(); index++) {
            T key = keys.get(index);
            double exact = probabilities.get(key) / total * distributable;
            int floor = (int) Math.floor(exact);
            result.put(key, floor);
            used += floor;
            remainders.add(new Remainder<>(key, exact - floor, index));
        }
        remainders.sort(Comparator.<Remainder<T>>comparingDouble(Remainder::fraction).reversed()
                .thenComparingInt(Remainder::index));
        for (int i = 0; i < WEIGHT_BASIS - used; i++) result.merge(remainders.get(i).key(), 1, Integer::sum);
        return result;
    }

    static int slimeEnvelope(int size) {
        if (size < 1 || size > 16) throw new IllegalArgumentException("Unsupported finalized slime size: " + size);
        return size <= 1 ? 0 : Math.multiplyExact(4, 1 + slimeEnvelope(size / 2));
    }

    private record Remainder<T>(T key, double fraction, int index) {}
}
