package com.bettercontent.bettercaveencounters;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import java.util.List;
import java.util.UUID;

/** NONSHIPPING same-module/package adapter. No authority implementation is shipped or globally installed. */
public final class CatalogueRootAccess {
    private CatalogueRootAccess() {}
    public static void verifyOwningModule(Module owner) {
        if (!owner.isNamed() || !DepthDirectorMod.MOD_ID.equals(owner.getName())
                || CatalogueRootAccess.class.getModule() != owner || CatalogueRootAccount.class.getModule() != owner)
            throw new IllegalStateException("Paid catalogue authority must remain in the owning nonshipping fixture module");
    }
    public record Outcome(boolean spawned, Mob mob, ResourceLocation entity, ResourceLocation selectedEntity,
                          UUID paidRoot, boolean nativeReplacement, boolean paidAndSameFamily) {}
    public static Outcome spawnAt(ServerLevel level, List<ServerPlayer> players, EcologyRegistry.Blend blend,
                                  double depth, RandomSource random, BlockPos position, boolean allowHeavy, int maximumCost) {
        try (var account = new CatalogueRootAccount(maximumCost)) {
            SpawnLocator.SpawnResult result = SpawnLocator.spawnAt(level, players, blend, depth, random, position,
                    allowHeavy, maximumCost, ignored -> true, account);
            if (result.spawned()) DirectorRuntime.INSTANCE.registerMob(result.mob());
            boolean paid = result.spawned() && result.cost() > 0 && account.spent() == result.cost()
                    && account.remaining() == maximumCost - result.cost()
                    && result.mob().getPersistentData().hasUUID("DepthDirectorLineage")
                    && result.mob().getPersistentData().getUUID("DepthDirectorLineage").equals(result.paidRoot());
            return new Outcome(result.spawned(), result.mob(), result.entity(), result.selectedEntity(),
                    result.paidRoot(), result.nativeReplacement(), paid);
        }
    }
}
