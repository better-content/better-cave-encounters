package com.bettercontent.bettercaveencounters;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RootChargeTest {
    @Test void failedRootRefundsItsPrepaidChargeExactlyOnce() {
        AtomicInteger refunds = new AtomicInteger();
        DirectorRuntime.RootCharge charge = new DirectorRuntime.RootCharge(refunds::incrementAndGet);
        charge.close(); charge.close();
        assertEquals(1, refunds.get());
        assertThrows(IllegalStateException.class, charge::commit);
    }
    @Test void committedRootNeverRefundsUnusedLineageCreditsIntoAnotherWave() {
        AtomicInteger refunds = new AtomicInteger();
        try (DirectorRuntime.RootCharge charge = new DirectorRuntime.RootCharge(refunds::incrementAndGet)) { charge.commit(); }
        assertEquals(0, refunds.get());
    }
    @Test void exceptionDuringFinalizationStillUnwindsTheReservation() {
        AtomicInteger refunds = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> {
            try (DirectorRuntime.RootCharge charge = new DirectorRuntime.RootCharge(refunds::incrementAndGet)) { throw new IllegalStateException("native finalization failed"); }
        });
        assertEquals(1, refunds.get());
    }
}
