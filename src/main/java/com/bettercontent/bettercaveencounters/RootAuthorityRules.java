package com.bettercontent.bettercaveencounters;
import java.util.UUID;

final class RootAuthorityRules {
    private RootAuthorityRules() {}
    static boolean liveOwner(boolean sameEncounter, UUID expectedFamily, UUID actualFamily,
                             DirectorPolicy.Phase phase, long telegraphUntil, int queued) {
        return sameEncounter && actualFamily != null && actualFamily.equals(expectedFamily)
                && phase == DirectorPolicy.Phase.SURGE && telegraphUntil < 0L && queued > 0;
    }
    static boolean heldSlotFits(int live, int pending, int maximum, int tick, int maxTick, int second, int maxSecond) {
        return live >= 0 && pending >= 1 && tick >= 0 && second >= 0
                && (long)live + pending <= maximum && tick <= maxTick && second <= maxSecond;
    }
}
