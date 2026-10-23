package com.bettercontent.bettercaveencounters;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Preserve existing remove-all-before-refund semantics, including the original native cause. */
final class RootRollback {
    private RootRollback() {}
    static void restoreAndRefund(Runnable restore, DirectorRuntime.RootCharge charge) {
        try { restore.run(); }
        finally { charge.close(); } // Called only after all owned physical removals succeeded.
    }
    static <T> void removeAll(List<T> entities, Predicate<T> alreadyRemoved, Consumer<T> discard,
                              Consumer<Throwable> freezePaidAccount) {
        Throwable failure = null;
        for (T entity : entities) {
            try { if (!alreadyRemoved.test(entity)) discard.accept(entity); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error; else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            try { freezePaidAccount.accept(failure); }
            catch (RuntimeException | Error freezeFailure) {
                if (freezeFailure != failure) failure.addSuppressed(freezeFailure);
            }
            if (failure instanceof RuntimeException error) throw error;
            throw (Error) failure;
        }
    }
}
