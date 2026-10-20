package com.bettercontent.bettercaveencounters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Conserved encounter history, apportioned like budgets instead of resetting at a locality split. */
final class SpeciesCounts {
    private SpeciesCounts() { }
    static <T> List<Map<T, Integer>> partition(Map<T, Integer> counts, List<Integer> weights) {
        List<Map<T, Integer>> result = new ArrayList<>();
        weights.forEach(ignored -> result.add(new HashMap<>()));
        counts.forEach((entity, count) -> {
            if (count < 0) throw new IllegalArgumentException("Negative historical species count");
            int[] shares = DirectorPolicy.allocateConserved(count, weights);
            for (int i = 0; i < shares.length; i++) if (shares[i] > 0) result.get(i).put(entity, shares[i]);
        });
        return result.stream().map(Map::copyOf).toList();
    }
}
