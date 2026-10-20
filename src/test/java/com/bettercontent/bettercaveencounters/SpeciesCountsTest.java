package com.bettercontent.bettercaveencounters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeciesCountsTest {
    @Test void splitRejoinAndAnotherSplitCannotResetOrMultiplyHistoricalAdmissions() {
        Map<String, Integer> before = Map.of("shared_across_rocks", 7, "heavy", 2, "swarm", 19);
        var split = SpeciesCounts.partition(before, List.of(2, 1, 1));
        Map<String, Integer> merged = new HashMap<>();
        split.forEach(child -> child.forEach((id, count) -> merged.merge(id, count, Math::addExact)));
        assertEquals(before, merged);
        var splitAgain = SpeciesCounts.partition(merged, List.of(1, 3));
        Map<String, Integer> joinedAgain = new HashMap<>();
        splitAgain.forEach(child -> child.forEach((id, count) -> joinedAgain.merge(id, count, Math::addExact)));
        assertEquals(before, joinedAgain);
        assertThrows(UnsupportedOperationException.class, () -> split.get(0).put("reset", 0));
    }
    @Test void finitePerPlayerHeadroomRemainsConservedAcrossSplitAndMerge() {
        var split = SpeciesCounts.partition(Map.of("line", 3), List.of(1, 1));
        int available = split.stream().mapToInt(child -> 4 - child.getOrDefault("line", 0)).sum();
        assertEquals(2 * 4 - 3, available);
        assertThrows(IllegalArgumentException.class, () -> SpeciesCounts.partition(Map.of("corrupt", -1), List.of(1)));
    }
}
