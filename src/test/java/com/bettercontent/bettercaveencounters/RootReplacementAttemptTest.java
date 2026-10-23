package com.bettercontent.bettercaveencounters;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RootReplacementAttemptTest {
    private final Object root = new Object(), child = new Object();
    private RootReplacementAttempt<Object> started() {
        var attempt = new RootReplacementAttempt<>(root);
        attempt.beginNative(RootReplacementAttempt.VARIANTS.get(0));
        return attempt;
    }
    private RootReplacementAttempt<Object> inserted() {
        var attempt = started(); attempt.claim(child); attempt.complete(child, true); attempt.returned(child); return attempt;
    }
    @Test void allFourNativeOutcomesUseActualIdentity() {
        for (String variant : RootReplacementAttempt.VARIANTS) {
            var attempt = new RootReplacementAttempt<>(root); attempt.beginNative(variant);
            assertTrue(attempt.matches(root, variant)); attempt.claim(child);
            attempt.complete(child, true); attempt.returned(child);
            assertSame(child, attempt.candidate()); assertTrue(attempt.canCommit(true, true));
            attempt.commit(true, true); assertTrue(attempt.committed());
        }
    }
    @Test void noConversionIsNotAnObservedReplacement() { assertFalse(new RootReplacementAttempt<>(root).observed()); }
    @Test void noContextCannotClaim() { assertThrows(IllegalStateException.class, () -> new RootReplacementAttempt<>(root).claim(child)); }
    @Test void naturalActorDoesNotMatch() { assertFalse(started().matches(new Object(), RootReplacementAttempt.VARIANTS.get(0))); }
    @Test void nestedActorMasksEnclosingRoot() { assertFalse(started().matches(child, RootReplacementAttempt.VARIANTS.get(0))); }
    @Test void unknownChildDoesNotMatch() { assertFalse(started().matches(root, "the_deep_void:unknown")); }
    @Test void differentKnownBranchCannotBorrowReservation() { assertFalse(started().matches(root, RootReplacementAttempt.VARIANTS.get(1))); }
    @Test void unknownCallsiteTypeCannotStart() { assertThrows(IllegalArgumentException.class, () -> started().beginNative("minecraft:zombie")); }
    @Test void duplicateNativeCallFails() { assertThrows(IllegalStateException.class, () -> started().beginNative(RootReplacementAttempt.VARIANTS.get(0))); }
    @Test void originalCannotBeReplacement() { assertThrows(IllegalArgumentException.class, () -> started().claim(root)); }
    @Test void duplicateClaimFails() { var a=started(); a.claim(child); assertThrows(IllegalStateException.class, () -> a.claim(child)); }
    @Test void completionWithoutClaimFails() { assertThrows(IllegalArgumentException.class, () -> started().complete(child,true)); }
    @Test void wrongCompletionIdentityFails() { var a=started(); a.claim(child); assertThrows(IllegalArgumentException.class, () -> a.complete(new Object(),true)); }
    @Test void completionTwiceFailsEvenWithSameValue() { var a=started(); a.claim(child); a.complete(child,true); assertThrows(IllegalStateException.class, () -> a.complete(child,true)); }
    @Test void nonnullNativeReturnIsNotSuccessAfterVeto() { var a=started(); a.claim(child); a.complete(child,false); a.returned(child); assertFalse(a.canCommit(true,true)); }
    @Test void nonnullReturnWithoutCompletionIsNotSuccess() { var a=started(); a.claim(child); a.returned(child); assertFalse(a.canCommit(true,true)); }
    @Test void trueCompletionWithoutNativeReturnCannotCommit() { var a=started(); a.claim(child); a.complete(child,true); assertFalse(a.canCommit(true,true)); }
    @Test void nullReturnCannotCommitInsertedCandidate() { var a=started(); a.claim(child); a.complete(child,true); a.returned(null); assertFalse(a.canCommit(true,true)); }
    @Test void differentNativeReturnCannotCommit() { var a=started(); a.claim(child); a.complete(child,true); a.returned(new Object()); assertFalse(a.canCommit(true,true)); }
    @Test void liveOriginalCannotBeFalselyCommitted() { assertFalse(inserted().canCommit(false,true)); }
    @Test void InvalidActualGeometryOrCapsCannotCommit() { assertFalse(inserted().canCommit(true,false)); }
    @Test void rejectedCommitThrows() { assertThrows(IllegalStateException.class, () -> inserted().commit(false,true)); }
    @Test void commitTwiceFails() { var a=inserted(); a.commit(true,true); assertThrows(IllegalStateException.class, () -> a.commit(true,true)); }
    @Test void duplicateNativeReturnFails() { var a=inserted(); assertThrows(IllegalStateException.class, () -> a.returned(child)); }
    @Test void closeIsIdempotentAndCannotReopen() { var a=started(); a.close(); a.close(); assertThrows(IllegalStateException.class, () -> a.claim(child)); }
    @Test void exceptionExitCannotCommit() { var a=started(); a.claim(child); a.close(); assertFalse(a.canCommit(true,true)); }
    @Test void commitThenCloseCannotCommitAgain() { var a=inserted(); a.commit(true,true); a.close(); assertFalse(a.canCommit(true,true)); }
    @Test void selectedDamnedQuotaIncludesAllActualVariantsExactlyOnce() {
        Map<String,Integer> counts = new HashMap<>(); counts.put(RootReplacementAttempt.DAMNED,2);
        for(String type:RootReplacementAttempt.VARIANTS) counts.put(type,3);
        assertEquals(14,RootReplacementAttempt.quotaCount(RootReplacementAttempt.DAMNED,id->counts.getOrDefault(id,0)));
        assertEquals(5,counts.size()); assertEquals(2,counts.get(RootReplacementAttempt.DAMNED));
    }
    @Test void otherSelectedEntryQuotaDoesNotIncludeVariants() {
        assertEquals(1,RootReplacementAttempt.quotaCount("minecraft:zombie",id->id.equals("minecraft:zombie")?1:100));
    }
    @Test void selectingVariantDoesNotGainFourIndependentQuotaSlots() {
        assertEquals(1,RootReplacementAttempt.quotaCount(RootReplacementAttempt.VARIANTS.get(0),id->1));
    }
}
