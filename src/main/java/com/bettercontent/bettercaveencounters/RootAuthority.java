package com.bettercontent.bettercaveencounters;

/** Package-private original-root prepayment seam. No public budget or fixture authorization API. */
interface RootAuthority {
    DirectorRuntime.RootCharge reserve(int cost);
    boolean allowsInitialReplacement();
    boolean stillValid();
}
