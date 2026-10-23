package com.bettercontent.bettercaveencounters;

/** NONSHIPPING, one original catalogue entry's finite maximumCost. No resets or global authority. */
final class CatalogueRootAccount implements RootAuthority, AutoCloseable {
    private final int maximumCost;
    private int remaining, spent;
    private boolean closed;
    CatalogueRootAccount(int maximumCost) {
        if (maximumCost < 1 || maximumCost > 64) throw new IllegalArgumentException("Catalogue cost outside original bound");
        this.maximumCost = maximumCost;
        remaining = maximumCost;
    }
    @Override public DirectorRuntime.RootCharge reserve(int cost) {
        if (closed) throw new IllegalStateException("Closed catalogue root account");
        if (cost <= 0) throw new IllegalArgumentException("Nonpositive catalogue charge");
        if (cost > remaining) return null;
        remaining -= cost;
        spent += cost;
        return new DirectorRuntime.RootCharge(() -> { remaining += cost; spent -= cost; });
    }
    @Override public boolean allowsInitialReplacement() { return stillValid(); }
    @Override public boolean stillValid() { return !closed && remaining >= 0 && spent >= 0 && remaining + spent == maximumCost; }
    @Override public void close() { closed = true; }
    int remaining() { return remaining; }
    int spent() { return spent; }
}
