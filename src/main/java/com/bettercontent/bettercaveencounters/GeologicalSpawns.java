package com.bettercontent.bettercaveencounters;

import com.bettercontent.bettercompatfixes.api.event.GeologicalAmbientEligibilityEvent;
import com.bettercontent.bettercompatfixes.api.NaturalSpawnContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Native natural-spawn integration. Never creates a periodic spawn loop or raises a cap. */
public final class GeologicalSpawns {
    private static final String RECEIPT = "DepthDirectorGeologicalAmbient";
    private GeologicalSpawns() {}

    @SubscribeEvent public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD)
            GeologicalRegistry.INSTANCE.publishBoundTags();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void potential(LevelEvent.PotentialSpawns event) {
        if (!DirectorConfig.ENABLED.get() || !NaturalSpawnContext.active() || event.getMobCategory() != MobCategory.MONSTER
                || !(event.getLevel() instanceof ServerLevel level)
                || !GeologicalRegistry.INSTANCE.sample(level, event.getPos()).mapped()) return;
        for (var old : java.util.List.copyOf(event.getSpawnerDataList())) event.removeSpawnerData(old);
        GeologicalRegistry.INSTANCE.ordinary(level, event.getPos()).forEach(event::addSpawnerData);
    }

    @SubscribeEvent public static void eligibility(GeologicalAmbientEligibilityEvent event) {
        if (DirectorConfig.ENABLED.get() && NaturalSpawnContext.active() && event.spawnType() == MobSpawnType.NATURAL
                && event.level() instanceof ServerLevel level && event.getResult() != Event.Result.DENY
                && GeologicalRegistry.INSTANCE.ordinaryMember(level, event.position(), event.type()))
            event.setResult(Event.Result.ALLOW);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void placement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!DirectorConfig.ENABLED.get() || !NaturalSpawnContext.active() || event.getSpawnType() != MobSpawnType.NATURAL
                || event.getEntityType().getCategory() != MobCategory.MONSTER
                || !(event.getLevel() instanceof ServerLevel level)
                || !GeologicalRegistry.INSTANCE.sample(level, event.getPos()).mapped()
                || event.getResult() == Event.Result.DENY) return;
        event.setResult(GeologicalRegistry.INSTANCE.ordinaryMember(level, event.getPos(), event.getEntityType())
                && standardRules(level, event.getPos(), event.getEntityType()) ? Event.Result.ALLOW : Event.Result.DENY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void position(MobSpawnEvent.PositionCheck event) {
        if (!DirectorConfig.ENABLED.get() || !NaturalSpawnContext.active() || event.getSpawnType() != MobSpawnType.NATURAL
                || event.getEntity().getType().getCategory() != MobCategory.MONSTER
                || !(event.getLevel() instanceof ServerLevel level) || event.getResult() == Event.Result.DENY) return;
        BlockPos position = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        GeologicalRegistry.Sample sample = GeologicalRegistry.INSTANCE.sample(level, position);
        Mob mob = event.getEntity();
        if (!sample.mapped()) {
            mob.getPersistentData().remove(RECEIPT);
            return;
        }
        if (!GeologicalRegistry.INSTANCE.ordinaryMember(level, position, mob.getType()) || !physical(level, mob)) {
            event.setResult(Event.Result.DENY);
            return;
        }
        CompoundTag receipt = mob.getPersistentData().getCompound(RECEIPT);
        // PositionCheck can be reposted by integrations. One roll for this mob/position/tick.
        if (!receipt.contains("Tick") || receipt.getLong("Tick") != level.getGameTime()
                || receipt.getLong("Position") != position.asLong()) {
            receipt = new CompoundTag();
            receipt.putLong("Tick", level.getGameTime());
            receipt.putLong("Position", position.asLong());
            double draw = level.random.nextDouble();
            receipt.putDouble("Draw", draw);
            receipt.putBoolean("Admitted", draw < sample.ordinaryAdmission());
            mob.getPersistentData().put(RECEIPT, receipt);
        }
        // Habitat substitution is confined to potential-list membership and placement.
        // Never force PositionCheck ALLOW: native instance rules and independent vetoes remain.
        if (!receipt.getBoolean("Admitted")) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finalized(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Mob mob) || SpawnLocator.isDirectorMob(mob)
                || !mob.getPersistentData().contains(RECEIPT)) return;
        CompoundTag receipt = mob.getPersistentData().getCompound(RECEIPT);
        GeologicalRegistry.Sample actual = GeologicalRegistry.INSTANCE.sample(level, mob.blockPosition());
        if (!receipt.contains("Draw") || receipt.getDouble("Draw") >= actual.ordinaryAdmission()
                || !GeologicalRegistry.INSTANCE.ordinaryMember(level, mob.blockPosition(), mob.getType()) || !physical(level, mob))
            event.setCanceled(true);
        // Do not reroll density after native initialization changes the hitbox.
        mob.getPersistentData().remove(RECEIPT);
    }

    private static boolean physical(ServerLevel level, Mob mob) {
        return level.getWorldBorder().isWithinBounds(mob.getBoundingBox()) && level.noCollision(mob)
                && mob.checkSpawnObstruction(level) && level.getFluidState(mob.blockPosition()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static boolean standardRules(ServerLevel level, BlockPos position, EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER
                && Monster.checkMonsterSpawnRules((EntityType<? extends Monster>) type,
                    level, MobSpawnType.NATURAL, position, level.random);
    }
}
