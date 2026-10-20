package com.bettercontent.bettercaveencounters;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GeologyWeightsTest {
    @Test void allThreeHabitatsContributeWithoutRosterLengthBias() {
        Map<String, Map<String, Integer>> rosters = Map.of("a", Map.of("a1", 8),
                "b", Map.of("b1", 8, "b2", 8, "b3", 8), "c", Map.of("c1", 3, "c2", 1));
        Map<String, Double> p = GeologyWeights.combine(Map.of("a", 12, "b", 6, "c", 6), rosters::get);
        assertEquals(.5, p.get("a1"), 1e-12);
        assertEquals(.25, p.get("b1") + p.get("b2") + p.get("b3"), 1e-12);
        assertEquals(.1875, p.get("c1"), 1e-12);
        assertEquals(.0625, p.get("c2"), 1e-12);
    }
    @Test void emptyHabitatAndIneligibleEntriesAreRemovedBeforeNormalization() {
        Map<String, Double> p = GeologyWeights.combine(Map.of("empty", 90, "live", 10),
                h -> h.equals("empty") ? Map.of() : Map.of("eligible", 8, "disabled", 0));
        assertEquals(Map.of("eligible", 1.0), p);
        assertTrue(GeologyWeights.combine(Map.of("empty", 20), h -> Map.of()).isEmpty());
    }
    @Test void largestRemaindersAreBoundedStableAndDoNotBiasShorterRosters() {
        Map<String, Double> input = new LinkedHashMap<>();
        input.put("d", 1.0 / 6); input.put("c", 1.0 / 6); input.put("b", 1.0 / 6); input.put("a", .5);
        Map<String, Integer> result = GeologyWeights.integerWeights(input);
        assertEquals(Map.of("a", 5000, "b", 1667, "c", 1667, "d", 1666), result);
        assertEquals(10_000, result.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(result, GeologyWeights.integerWeights(Map.of("a", .5, "b", 1.0 / 6, "c", 1.0 / 6, "d", 1.0 / 6)));
        assertThrows(IllegalArgumentException.class, () -> GeologyWeights.integerWeights(Map.of("bad", Double.NaN)));
    }
    @Test void sampleStencilAndNativeSlimeEnvelopeAreFrozen() {
        assertArrayEquals(new int[]{-4, -2, 0, 2, 4}, GeologyWeights.OFFSETS);
        assertEquals(125, (int) Math.pow(GeologyWeights.OFFSETS.length, 3));
        assertEquals(8, GeologyWeights.MINIMUM_SAMPLES);
        assertEquals(0, GeologyWeights.slimeEnvelope(1));
        assertEquals(4, GeologyWeights.slimeEnvelope(2));
        assertEquals(4, GeologyWeights.slimeEnvelope(3));
        assertEquals(20, GeologyWeights.slimeEnvelope(4));
        assertEquals(84, GeologyWeights.slimeEnvelope(8));
        assertThrows(IllegalArgumentException.class, () -> GeologyWeights.slimeEnvelope(0));
        assertThrows(IllegalArgumentException.class, () -> GeologyWeights.slimeEnvelope(17));
    }
}
