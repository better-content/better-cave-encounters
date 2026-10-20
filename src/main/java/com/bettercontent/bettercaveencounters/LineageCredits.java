package com.bettercontent.bettercaveencounters;

import java.util.HashMap;
import java.util.Map;

/** A prepaid, non-renewing account; absence/unload/retirement has no credit operation. */
final class LineageCredits<K> {
    private final Map<K, Integer> limits;
    private final Map<K, Integer> counts = new HashMap<>();
    private final int funded;
    private int remaining;

    LineageCredits(Map<K, Integer> limits, int funded) {
        if (funded < 0 || funded > 256 || limits.values().stream().anyMatch(n -> n < 1 || n > 256))
            throw new IllegalArgumentException("Invalid finite lineage account");
        this.limits = Map.copyOf(limits);
        this.funded = funded;
        this.remaining = funded;
    }

    boolean canReserve(K type) { return remaining > 0 && used(type) < limits.getOrDefault(type, 0); }
    boolean reserve(K type) {
        if (!canReserve(type)) return false;
        remaining--;
        counts.merge(type, 1, Integer::sum);
        return true;
    }
    void rollback(K type) {
        int used = used(type);
        if (used <= 0) throw new IllegalStateException("Cannot refund an unreserved descendant");
        if (used == 1) counts.remove(type); else counts.put(type, used - 1);
        remaining++;
    }
    int funded() { return funded; }
    int remaining() { return remaining; }
    int used(K type) { return counts.getOrDefault(type, 0); }
    Map<K, Integer> counts() { return Map.copyOf(counts); }

    static <K> LineageCredits<K> restore(Map<K, Integer> limits, int funded, int remaining, Map<K, Integer> counts) {
        LineageCredits<K> account = new LineageCredits<>(limits, funded);
        if (remaining < 0 || remaining > funded || counts.entrySet().stream()
                .anyMatch(e -> e.getValue() < 0 || e.getValue() > limits.getOrDefault(e.getKey(), 0))
                || counts.values().stream().mapToInt(Integer::intValue).sum() != funded - remaining)
            throw new IllegalArgumentException("Corrupt lineage credit conservation");
        account.remaining = remaining;
        account.counts.putAll(counts);
        return account;
    }
}
