package com.bettercontent.bettercaveencounters.diagnostics;

/** Pure observation of the owning feature's existing outcome, with no native reads. */
public final class FeatureTelemetry {
    private FeatureTelemetry() {}
    public static void suspended(PlaytestLog log, PlaytestLog.Context context, java.util.UUID encounter, java.util.UUID family, String reason) {
        log.debug("director", "director.phase", context, "suspended", reason, () -> PlaytestLog.fields("encounter", encounter, "family", family));
    }
}
