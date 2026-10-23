package com.bettercontent.bettercaveencounters;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

final class RootReplacementBody {
    private RootReplacementBody() {}
    /** Provisional actual-body geometry; native/Forge insertion hooks still run normally. */
    static boolean provisionalGeometry(ServerLevel level, List<ServerPlayer> players, ServerPlayer expectedTarget, Mob mob) {
        return spatialTarget(level, players, expectedTarget, mob) != null;
    }
    /** Existing Cave root-role grounding, then unchanged native path predicate. Not readonly navigation.
     * Called only after true insertion/return eligibility and original procedure completion, before commit/tick.
     */
    static boolean finalAdmission(ServerLevel level, List<ServerPlayer> players, ServerPlayer expectedTarget, Mob mob) {
        ServerPlayer target = spatialTarget(level, players, expectedTarget, mob);
        if (target == null) return false;
        mob.setOnGround(true);
        Path path = mob.getNavigation().createPath(target.blockPosition(), 0);
        return path != null && path.canReach();
    }
    private static ServerPlayer spatialTarget(ServerLevel level, List<ServerPlayer> players, ServerPlayer expectedTarget, Mob mob) {
        BlockPos position = mob.blockPosition();
        if (!level.hasChunkAt(position) || !SpawnLocator.isFloor(level, position)) return null;
        int light = Math.max(level.getBrightness(LightLayer.BLOCK, position), level.getBrightness(LightLayer.SKY, position));
        if (light > DirectorConfig.BLOCK_LIGHT_LIMIT.get() || level.canSeeSky(position) || SpawnLocator.visibleToAny(level, position, players)) return null;
        ServerPlayer nearest = players.stream().filter(ServerPlayer::isAlive)
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(Vec3.atCenterOf(position)))).orElse(null);
        if (nearest == null || nearest != expectedTarget || !level.noCollision(mob)
                || !level.getWorldBorder().isWithinBounds(mob.getBoundingBox())) return null;
        return nearest;
    }
    /** Typed NBT snapshot is restricted to the four existing Cave-owned initialization keys. */
    static final class OwnedDataSnapshot {
        private static final List<String> KEYS = List.of("DepthDirectorSpawned", "DepthDirectorTarget",
                "DepthDirectorLineage", "DepthDirectorSpawnHabitat");
        private final Map<String, Tag> values = new HashMap<>();
        OwnedDataSnapshot(CompoundTag data) {
            for (String key : KEYS) {
                Tag value = data.get(key);
                if (value != null) values.put(key, value.copy());
            }
        }
        void restore(CompoundTag data) {
            for (String key : KEYS) {
                if (values.containsKey(key)) data.put(key, values.get(key).copy());
                else data.remove(key);
            }
        }
    }
    /** Only Cave-owned initialization fields; no inventory or unrelated mod payload snapshots. */
    static final class MemberSnapshot {
        private final Mob mob;
        private final OwnedDataSnapshot data;
        private final boolean provenanceTag, noAi, invulnerable, onGround;
        private final LivingEntity target;
        private final AttributeModifier approach;
        MemberSnapshot(Mob mob) {
            this.mob = mob;
            data = new OwnedDataSnapshot(mob.getPersistentData());
            provenanceTag = mob.getTags().contains(SpawnLocator.PROVENANCE_TAG);
            noAi = mob.isNoAi(); invulnerable = mob.isInvulnerable(); target = mob.getTarget(); onGround = mob.onGround();
            AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
            approach = range == null ? null : range.getModifier(SpawnLocator.APPROACH_RANGE_MODIFIER);
        }
        void restore() {
            data.restore(mob.getPersistentData());
            if (provenanceTag) mob.addTag(SpawnLocator.PROVENANCE_TAG); else mob.removeTag(SpawnLocator.PROVENANCE_TAG);
            AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (range != null) {
                range.removeModifier(SpawnLocator.APPROACH_RANGE_MODIFIER);
                if (approach != null) range.addPermanentModifier(approach);
            }
            mob.setNoAi(noAi); mob.setInvulnerable(invulnerable); mob.setTarget(target); mob.setOnGround(onGround);
        }
    }
}
