package com.bettercontent.bettercaveencounters;

import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Transient initial-root protocol. Insertion completion, never a nonnull factory return, is authority. */
final class RootReplacementAttempt<T> {
    static final String DAMNED = "the_deep_void:damned";
    static final List<String> VARIANTS = List.of("the_deep_void:cross_eyes", "the_deep_void:big_eye",
            "the_deep_void:multiple_eyes", "the_deep_void:four_eyes");
    private final T original;
    private String expected;
    private T candidate, returned;
    private boolean completed, inserted, returnedNormally, committed, closed;

    RootReplacementAttempt(T original) { this.original = Objects.requireNonNull(original); }

    void beginNative(String expected) {
        requireOpen();
        if (!VARIANTS.contains(expected)) throw new IllegalArgumentException("Not a native initial variant");
        if (observed()) throw new IllegalStateException("Native replacement attempted twice");
        this.expected = expected;
    }

    boolean observed() { return expected != null; }

    /** Wrong actors/types stay with existing admission policy, never inherit this reservation. */
    boolean matches(T parent, String actualType) {
        return !closed && !committed && observed() && parent == original && expected.equals(actualType);
    }

    void claim(T child) {
        requireOpen();
        if (!observed()) throw new IllegalStateException("No native initial callsite");
        if (candidate != null) throw new IllegalStateException("Replacement candidate claimed twice");
        if (child == original) throw new IllegalArgumentException("Original is not its replacement");
        candidate = Objects.requireNonNull(child);
    }

    void complete(T child, boolean inserted) {
        requireOpen();
        if (child != candidate || candidate == null) throw new IllegalArgumentException("Wrong completion candidate");
        if (completed) throw new IllegalStateException("Replacement completion delivered twice");
        completed = true;
        this.inserted = inserted;
    }

    /** Must record the untouched native return, including nonnull after an insertion veto. */
    void returned(T value) {
        requireOpen();
        if (!observed() || returnedNormally) throw new IllegalStateException("Unexpected native return");
        returnedNormally = true;
        returned = value;
    }

    boolean canCommit(boolean originalRemoved, boolean actualCandidateValid) {
        return !closed && !committed && returnedNormally && completed && inserted && candidate != null
                && candidate == returned && originalRemoved && actualCandidateValid;
    }

    void commit(boolean originalRemoved, boolean actualCandidateValid) {
        if (!canCommit(originalRemoved, actualCandidateValid)) throw new IllegalStateException("Unresolved native replacement");
        committed = true;
    }

    T candidate() { return candidate; }
    T nativeReturn() { return returned; }
    boolean committed() { return committed; }
    void close() { closed = true; }

    private void requireOpen() {
        if (closed || committed) throw new IllegalStateException("Closed replacement attempt");
    }

    /** Preserve the selected Damned entry's original quota while recording each actual root only once. */
    static int quotaCount(String selected, ToIntFunction<String> counts) {
        int count = counts.applyAsInt(selected);
        if (DAMNED.equals(selected))
            for (String variant : VARIANTS) count = Math.addExact(count, counts.applyAsInt(variant));
        return count;
    }
}
