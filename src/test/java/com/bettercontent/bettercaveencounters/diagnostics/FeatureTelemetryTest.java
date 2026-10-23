package com.bettercontent.bettercaveencounters.diagnostics;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class FeatureTelemetryTest {
    @Test public void nativeOutcomeIsNotMisreportedOrExpandedIntoPrivatePayload() {
        List<String> records = new ArrayList<>();
        PlaytestLog log = new PlaytestLog("better_cave_encounters", new PlaytestLog.Sink() {
            public boolean enabled(String category, String level) { return true; }
            public void write(String category, String level, String message) { records.add(message); }
        }, () -> 0L, new Properties());
        FeatureTelemetry.suspended(log, PlaytestLog.Context.server("", 42),
                java.util.UUID.fromString("7ac1c622-f1a4-4986-9404-70bf2345061c"),
                java.util.UUID.fromString("341f78c8-47eb-4c02-a7ea-46a0c9081785"), "route_unavailable");
        if (records.size() != 1 || !records.get(0).contains("result=suspended reason=route_unavailable")
                || !records.get(0).contains("encounter=7ac1c622-f1a4-4986-9404-70bf2345061c")
                || !records.get(0).contains("family=341f78c8-47eb-4c02-a7ea-46a0c9081785")
                || !records.get(0).contains("tick=42") || records.get(0).contains("pos=")
                || records.get(0).contains("player=") || records.get(0).contains("payload=")) {
            throw new AssertionError(records.toString());
        }
    }
    public static void main(String[] args) { new FeatureTelemetryTest().nativeOutcomeIsNotMisreportedOrExpandedIntoPrivatePayload(); }
}
