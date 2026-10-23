package com.bettercontent.bettercaveencounters;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RootRollbackTest {
    @Test void allEntitiesRemovedBeforeTheRealChargeRefunds() {
        var budget=new AtomicInteger(60); var removed=new ArrayList<Integer>();
        var charge=new DirectorRuntime.RootCharge(()->{ assertEquals(List.of(1,2),removed); budget.addAndGet(4); });
        try(charge) { RootRollback.removeAll(List.of(1,2),e->false,removed::add,e->fail("Unexpected failure")); }
        assertEquals(64,budget.get()); charge.close(); assertEquals(64,budget.get());
    }
    @Test void nativeRemovalFailureFreezesRealChargeWithoutUnsafeRefund() {
        var budget=new AtomicInteger(60); var attempts=new ArrayList<Integer>(); var cause=new IllegalStateException("native remover");
        var charge=new DirectorRuntime.RootCharge(()->budget.addAndGet(4));
        var actual=assertThrows(IllegalStateException.class,()->{
            try(charge) { RootRollback.removeAll(List.of(1,2),e->false,e->{attempts.add(e); if(e==1)throw cause;},
                failure->{ assertSame(cause,failure); charge.commit(); charge.close(); }); }
        });
        assertSame(cause,actual); assertEquals(List.of(1,2),attempts); assertEquals(60,budget.get());
        charge.close(); assertEquals(60,budget.get());
    }
    @Test void additionalNativeFailureIsSuppressedAndEveryRemoverRuns() {
        var first=new IllegalStateException("first"); var second=new AssertionError("second"); var attempts=new ArrayList<Integer>();
        assertSame(first,assertThrows(IllegalStateException.class,()->RootRollback.removeAll(List.of(1,2,3),e->false,e->{
            attempts.add(e); if(e==1)throw first; if(e==2)throw second;
        },failure->assertSame(first,failure))));
        assertEquals(List.of(1,2,3),attempts); assertArrayEquals(new Throwable[]{second},first.getSuppressed());
    }
    @Test void duplicateSameCauseIsNotSelfSuppressed() {
        var cause=new IllegalStateException("same");
        assertSame(cause,assertThrows(IllegalStateException.class,()->RootRollback.removeAll(List.of(1,2),e->false,e->{throw cause;},e->{})));
        assertEquals(0,cause.getSuppressed().length);
    }
    @Test void alreadyRemovedEntitiesAreNotDiscardedAgain() {
        var removed=new ArrayList<Integer>(); RootRollback.removeAll(List.of(1,2),e->e==1,removed::add,e->fail());
        assertEquals(List.of(2),removed);
    }
    @Test void nativeErrorRemainsAnErrorAndFreezes() {
        var cause=new AssertionError("native error"); var frozen=new AtomicInteger();
        assertSame(cause,assertThrows(AssertionError.class,()->RootRollback.removeAll(List.of(1),e->false,e->{throw cause;},e->frozen.incrementAndGet())));
        assertEquals(1,frozen.get());
    }
    @Test void removedPredicateFailureAlsoFreezesAndDoesNotSkipLaterEntities() {
        var cause=new IllegalStateException("native removed query"); var discarded=new ArrayList<Integer>();
        assertThrows(IllegalStateException.class,()->RootRollback.removeAll(List.of(1,2),e->{if(e==1)throw cause;return false;},discarded::add,e->assertSame(cause,e)));
        assertEquals(List.of(2),discarded);
    }
    @Test void committedRootChargeNeverRefunds() {
        var refunds=new AtomicInteger(); var charge=new DirectorRuntime.RootCharge(refunds::incrementAndGet);
        charge.commit(); charge.close(); charge.close(); assertEquals(0,refunds.get());
    }
    @Test void failedRootChargeRefundsExactlyOnce() {
        var refunds=new AtomicInteger(); var charge=new DirectorRuntime.RootCharge(refunds::incrementAndGet);
        charge.close(); charge.close(); assertEquals(1,refunds.get());
    }
    @Test void targetRestorationExceptionIsSurfacedButSafeRefundStillOccursOnce() {
        var refunds=new AtomicInteger();var charge=new DirectorRuntime.RootCharge(refunds::incrementAndGet);
        var cause=new IllegalStateException("native target restore");
        assertSame(cause,assertThrows(IllegalStateException.class,()->RootRollback.restoreAndRefund(()->{throw cause;},charge)));
        assertEquals(1,refunds.get());charge.close();assertEquals(1,refunds.get());
    }
    @Test void provenanceRestoresBeforeChargeCloses() {
        var stage=new AtomicInteger();var charge=new DirectorRuntime.RootCharge(()->assertEquals(1,stage.get()));
        RootRollback.restoreAndRefund(()->stage.incrementAndGet(),charge);assertEquals(1,stage.get());
    }
    @Test void restorationErrorRetainsNativeCauseAndRefundsOnlyAfterSafePhysicalCleanup() {
        var refunds=new AtomicInteger();var charge=new DirectorRuntime.RootCharge(refunds::incrementAndGet);
        var cause=new AssertionError("native restore error");
        assertSame(cause,assertThrows(AssertionError.class,()->RootRollback.restoreAndRefund(()->{throw cause;},charge)));
        assertEquals(1,refunds.get());
    }
    @Test void frozenSurvivorBookkeepingFailureCannotHideRemovalCauseOrRefundPaidPressure() {
        var budget=new AtomicInteger(60);var charge=new DirectorRuntime.RootCharge(()->budget.addAndGet(4));
        var cause=new IllegalStateException("native remover");var bookkeeping=new AssertionError("survivor bookkeeping");
        assertSame(cause,assertThrows(IllegalStateException.class,()->{
            try(charge) {RootRollback.removeAll(List.of(1),e->false,e->{throw cause;},failure->{
                charge.commit();charge.close();throw bookkeeping;
            });}
        }));
        assertArrayEquals(new Throwable[]{bookkeeping},cause.getSuppressed());assertEquals(60,budget.get());
        charge.close();assertEquals(60,budget.get());
    }
    @Test void closedChargeCannotCommit() {
        var charge=new DirectorRuntime.RootCharge(()->{}); charge.close(); assertThrows(IllegalStateException.class,charge::commit);
    }
}
