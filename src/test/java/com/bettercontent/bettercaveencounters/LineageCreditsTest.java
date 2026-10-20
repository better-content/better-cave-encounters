package com.bettercontent.bettercaveencounters;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LineageCreditsTest {
    @Test void prepaidEnvelopeIsSharedByAllGenerationsAndChildTypes() {
        LineageCredits<String> account = new LineageCredits<>(Map.of("thrall", 4, "beast", 4), 4);
        assertTrue(account.reserve("thrall"));
        assertTrue(account.reserve("thrall"));
        assertTrue(account.reserve("beast"));
        assertTrue(account.reserve("thrall"));
        assertFalse(account.reserve("beast"));
        assertFalse(account.reserve("unrelated_victim_child"));
        assertEquals(0, account.remaining());
        assertEquals(4, account.counts().values().stream().mapToInt(Integer::intValue).sum());
    }
    @Test void failedInsertionReturnsOnlyItsReservedUnitAndNeverCreatesCredit() {
        LineageCredits<String> account = new LineageCredits<>(Map.of("maggot", 2), 2);
        assertTrue(account.reserve("maggot"));
        account.rollback("maggot");
        assertEquals(2, account.remaining());
        assertTrue(account.counts().isEmpty());
        assertThrows(IllegalStateException.class, () -> account.rollback("maggot"));
        assertTrue(account.reserve("maggot")); assertTrue(account.reserve("maggot"));
        assertFalse(account.reserve("maggot"));
    }
    @Test void capacityLimitedLegacyFundingIsNotAFreeNativeEnvelope() {
        LineageCredits<String> partial = new LineageCredits<>(Map.of("maggot", 4), 1);
        assertTrue(partial.reserve("maggot")); assertFalse(partial.reserve("maggot"));
        LineageCredits<String> unfunded = new LineageCredits<>(Map.of("maggot", 4), 0);
        assertFalse(unfunded.reserve("maggot"));
    }
    @Test void restoreDoesNotRefundCreditsWhenMembersUnloadOrEncounterRetires() {
        LineageCredits<String> original = new LineageCredits<>(Map.of("slime", 20), 20);
        for (int n = 0; n < 17; n++) assertTrue(original.reserve("slime"));
        LineageCredits<String> restored = LineageCredits.restore(Map.of("slime", 20), original.funded(),
                original.remaining(), original.counts());
        assertEquals(3, restored.remaining());
        for (int n = 0; n < 3; n++) assertTrue(restored.reserve("slime"));
        assertFalse(restored.reserve("slime"));
        assertEquals(20, restored.used("slime"));
    }
    @Test void malformedSavedCreditCannotMintOrHideProduction() {
        assertThrows(IllegalArgumentException.class, () -> LineageCredits.restore(Map.of("slime", 20), 20, 20, Map.of("slime", 1)));
        assertThrows(IllegalArgumentException.class, () -> LineageCredits.restore(Map.of("slime", 20), 20, 21, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> LineageCredits.restore(Map.of("slime", 20), 20, 19, Map.of("other", 1)));
    }
}
