package com.bettercontent.bettercaveencounters;

import com.bettercontent.bettercompatfixes.api.CausalMobSpawns;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

final class SpawnLocator {
    static final String PROVENANCE_TAG = "better_cave_encounters_spawned";
    static final String PROVENANCE_NBT = "DepthDirectorSpawned";
    static final String TARGET_NBT = "DepthDirectorTarget";
    static final TagKey<EntityType<?>> DENIED = TagKey.create(Registries.ENTITY_TYPE,
            new ResourceLocation(DepthDirectorMod.MOD_ID, "denied"));
    static final UUID APPROACH_RANGE_MODIFIER = UUID.fromString("6628878d-4b58-4431-b877-baa7027270bc");

    private SpawnLocator() {}

    static boolean hasApproach(ServerLevel level, List<ServerPlayer> players, RandomSource random) {
        return approach(level, players, random).isPresent();
    }

    static Optional<BlockPos> approach(ServerLevel level, List<ServerPlayer> players, RandomSource random) {
        return approach(level, players, random, -1);
    }

    static Optional<BlockPos> approach(ServerLevel level, List<ServerPlayer> players, RandomSource random, int sector) {
        if (players.isEmpty()) return Optional.empty();
        Mob probe = EntityType.ZOMBIE.create(level);
        if (probe == null) return Optional.empty();
        ensureApproachRange(probe);
        Candidate candidate = candidate(level, players, players.get(random.nextInt(players.size())).position(), random, sector, probe);
        probe.discard();
        return candidate == null ? Optional.empty() : Optional.of(candidate.position);
    }

    static SpawnResult spawn(ServerLevel level, List<ServerPlayer> players, EcologyRegistry.Blend blend,
                             double depth, RandomSource random, int sector, boolean allowHeavy, int maximumCost) {
        return spawn(level, players, blend, depth, random, sector, allowHeavy, maximumCost, ignored -> true);
    }

    static SpawnResult spawn(ServerLevel level, List<ServerPlayer> players, EcologyRegistry.Blend blend,
                             double depth, RandomSource random, int sector, boolean allowHeavy, int maximumCost,
                             Predicate<EcologyDefinition.Entry> selectable) {
        if (players.isEmpty()) return SpawnResult.failed();
        // Resolve the corridor first. Choosing from the anchor's ecology before locating
        // the actual floor would reintroduce encounter-wide/nonlocal roster selection.
        return approach(level, players, random, sector)
                .map(position -> spawnAt(level, players, blend, depth, random, position, allowHeavy, maximumCost, selectable))
                .orElseGet(SpawnResult::failed);
    }

    static SpawnResult spawnAt(ServerLevel level, List<ServerPlayer> players, EcologyRegistry.Blend blend,
                               double depth, RandomSource random, BlockPos position, boolean allowHeavy,
                               int maximumCost) {
        return spawnAt(level, players, blend, depth, random, position, allowHeavy, maximumCost, ignored -> true);
    }

    static SpawnResult spawnAt(ServerLevel level, List<ServerPlayer> players, EcologyRegistry.Blend blend,
                               double depth, RandomSource random, BlockPos position, boolean allowHeavy,
                               int maximumCost, Predicate<EcologyDefinition.Entry> selectable) {
        return spawnAt(level, players, blend, depth, random, position, allowHeavy, maximumCost, selectable,
                DirectorLineages.rootAuthority());
    }

    static SpawnResult spawnAt(ServerLevel level, List<ServerPlayer> players, EcologyRegistry.Blend blend,
                               double depth, RandomSource random, BlockPos position, boolean allowHeavy,
                               int maximumCost, Predicate<EcologyDefinition.Entry> selectable, RootAuthority authority) {
        if (players.isEmpty()) return SpawnResult.failed();
        boolean mapped = GeologicalRegistry.INSTANCE.sample(level, position).mapped();
        for (int attempt = 0; attempt < 8; attempt++) {
            Selection selection;
            if (mapped) {
                GeologicalRegistry.Choice choice = GeologicalRegistry.INSTANCE.chooseDirector(level, position,
                        random, allowHeavy, maximumCost, selectable);
                if (choice == null) return SpawnResult.failed(); // Recognized geology never imports fallback actors.
                EcologyDefinition.Entry entry = choice.entry().roster();
                selection = new Selection(ForgeRegistries.ENTITY_TYPES.getValue(entry.entity()), entry.entity(),
                        entry.cost(), entry.role(), choice.habitat().toString());
            } else selection = blend == null ? nativeSelection(level, position, random)
                    : authoredSelection(blend, depth, random, allowHeavy, maximumCost, selectable);
            if (selection == null || selection.type == null || selection.cost > maximumCost || selection.type.is(DENIED))
                continue;
            Mob mob = selection.type.create(level) instanceof Mob created ? created : null;
            if (mob == null) continue;
            GeologicalDefinition.Offspring policy = DirectorLineages.policy(mob);
            if (selection.cost + policy.credits() > maximumCost) { mob.discard(); continue; }
            ensureApproachRange(mob);
            Candidate candidate = validateCandidate(level, players, position, mob).orElse(null);
            if (candidate == null) { mob.discard(); continue; }
            SpawnResult result = finishSpawn(level, candidate, selection, policy, mob, random, players, authority);
            if (result.spawned() || result.terminal()) return result;
        }
        return SpawnResult.failed();
    }

    private static SpawnResult finishSpawn(ServerLevel level, Candidate candidate, Selection selection,
                                           GeologicalDefinition.Offspring policy, Mob mob, RandomSource random,
                                           List<ServerPlayer> players, RootAuthority authority) {
        mob.moveTo(candidate.position.getX() + 0.5, candidate.position.getY(), candidate.position.getZ() + 0.5,
                random.nextFloat() * 360.0F, 0.0F);
        if (!eventSpawnPositionAllowed(mob, level) || !level.noCollision(mob)
                || !level.getWorldBorder().isWithinBounds(mob.getBoundingBox())
                || !DirectorRuntime.INSTANCE.reserveAdmission(level.getServer())) {
            mob.discard();
            return SpawnResult.failed();
        }
        try (DirectorLineages.RootAdmission admission = DirectorLineages.beginRoot(mob, selection.role, policy, selection.cost, authority)) {
            if (admission == null) { mob.discard(); return SpawnResult.failed(); }
            bindCombatTarget(mob, candidate.target);
            mob.getPersistentData().putString("DepthDirectorSpawnHabitat", selection.habitat);
            admission.prepareReplacement(
                    actual -> RootReplacementBody.provisionalGeometry(level, players, candidate.target, actual),
                    actual -> RootReplacementBody.finalAdmission(level, players, candidate.target, actual)
                            && eventSpawnPositionAllowed(actual, level),
                    actual -> {
                        ensureApproachRange(actual);
                        bindCombatTarget(actual, candidate.target);
                        actual.getPersistentData().putString("DepthDirectorSpawnHabitat", selection.habitat);
                    });
            return CausalMobSpawns.as(mob, () -> {
                CaveNativeRootBridge.finalizeRoot(admission, () -> {
                    ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(candidate.position),
                            MobSpawnType.EVENT, null, null);
                    return null;
                });
                if (admission.replacementObserved()) {
                    Mob actual = admission.commitReplacement();
                    if (actual == null) return SpawnResult.terminalFailure();
                    return new SpawnResult(true, actual, ForgeRegistries.ENTITY_TYPES.getKey(actual.getType()),
                            selection.cost + policy.credits(), selection.role, false, selection.entity, true, mob.getUUID());
                }
                if (mob.isSpawnCancelled() || !admission.validFinalizedSize() || validateCandidate(level, players, candidate.position, mob).isEmpty()
                        || !eventSpawnPositionAllowed(mob, level)
                        || !level.getWorldBorder().isWithinBounds(mob.getBoundingBox()) || !level.addFreshEntity(mob)) {
                    mob.discard();
                    return SpawnResult.failed();
                }
                bindCombatTarget(mob, candidate.target);
                admission.commit();
                return new SpawnResult(true, mob, selection.entity, selection.cost + policy.credits(), selection.role);
            });
        }
    }

    static void restoreDirectorMob(Mob mob) {
        if (!isDirectorMob(mob)) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        // Older Director mobs carried the persisted scoreboard marker before the NBT marker existed.
        // Migrate them when their owning chunk loads so subsequent reloads keep the durable marker.
        mob.getPersistentData().putBoolean(PROVENANCE_NBT, true);
        mob.setNoAi(false);
        mob.setInvulnerable(false);
        if (!mob.getPersistentData().hasUUID(TARGET_NBT)) {
            mob.setTarget(null);
            return;
        }
        ServerPlayer target = level.getServer().getPlayerList()
                .getPlayer(mob.getPersistentData().getUUID(TARGET_NBT));
        if (target != null && target.isAlive() && target.level() == mob.level()) {
            mob.setTarget(target);
        } else {
            mob.setTarget(null);
        }
    }

    static boolean isDirectorMob(Mob mob) {
        return mob.getPersistentData().getBoolean(PROVENANCE_NBT) || mob.getTags().contains(PROVENANCE_TAG);
    }

    private static void bindCombatTarget(Mob mob, ServerPlayer target) {
        mob.setNoAi(false);
        mob.setInvulnerable(false);
        mob.getPersistentData().putUUID(TARGET_NBT, target.getUUID());
        mob.setTarget(target);
    }

    static void ensureApproachRange(Mob mob) {
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange == null) return;
        followRange.removeModifier(APPROACH_RANGE_MODIFIER);
        double required = DirectorConfig.SPAWN_MAXIMUM_RADIUS.get() + 8.0;
        double addition = required - followRange.getValue();
        if (addition > 0.0) {
            followRange.addPermanentModifier(new AttributeModifier(APPROACH_RANGE_MODIFIER,
                    "Better Cave Encounters approach range", addition, AttributeModifier.Operation.ADDITION));
        }
    }

    static boolean eventSpawnPositionAllowed(Mob mob, ServerLevel level) {
        MobSpawnEvent.PositionCheck event = new MobSpawnEvent.PositionCheck(mob, level, MobSpawnType.EVENT, null);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.getResult() == Event.Result.DENY) return false;
        return event.getResult() == Event.Result.ALLOW || mob.checkSpawnObstruction(level);
    }

    private static Selection authoredSelection(EcologyRegistry.Blend blend, double depth, RandomSource random,
                                               boolean allowHeavy, int maximumCost,
                                               Predicate<EcologyDefinition.Entry> selectable) {
        EcologyDefinition ecology = blend.choose(random);
        EcologyDefinition.Entry entry = ecology.pick(random, depth, allowHeavy, entity -> {
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(entity)) return false;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entity);
            return type != null && !type.is(DENIED) && ecology.roster().stream()
                    .anyMatch(candidate -> candidate.entity().equals(entity) && authoredCategoryAllowed(type, candidate));
        }, candidate -> candidate.cost() + DirectorLineages.reservation(candidate.entity()) <= maximumCost && selectable.test(candidate));
        if (entry == null) return null;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entry.entity());
        return type != null && authoredCategoryAllowed(type, entry)
                ? new Selection(type, entry.entity(), entry.cost(), entry.role()) : null;
    }

    private static boolean authoredCategoryAllowed(EntityType<?> type, EcologyDefinition.Entry entry) {
        return type.getCategory() == MobCategory.MONSTER
                || (entry != null && entry.role() == EcologyDefinition.Role.HEAVY
                    && type.getCategory() == MobCategory.CREATURE);
    }

    private static Selection nativeSelection(ServerLevel level, BlockPos position, RandomSource random) {
        Optional<MobSpawnSettings.SpawnerData> selected = level.getBiome(position).value().getMobSettings()
                .getMobs(MobCategory.MONSTER).getRandom(random);
        if (selected.isEmpty()) return null;
        EntityType<?> type = selected.get().type;
        if (type.is(DENIED)) return null;
        Mob preview = type.create(level) instanceof Mob created ? created : null;
        if (preview == null) return null;
        double health = preview.getMaxHealth();
        double armor = preview.getArmorValue();
        preview.discard();
        if (health > 80.0) return null;
        int cost = Math.max(1, Math.min(8, (int) Math.ceil(health / 20.0) + (int) Math.floor(armor / 10.0)));
        return new Selection(type, ForgeRegistries.ENTITY_TYPES.getKey(type), cost, EcologyDefinition.Role.LINE, "native");
    }

    private static Candidate candidate(ServerLevel level, List<ServerPlayer> players, Vec3 anchor,
                                       RandomSource random, int sector, Mob mob) {
        int minimum = DirectorConfig.SPAWN_MINIMUM_RADIUS.get();
        int maximum = Math.max(minimum, DirectorConfig.SPAWN_MAXIMUM_RADIUS.get());
        for (int attempt = 0; attempt < 48; attempt++) {
            BlockPos sample = samplePosition(anchor, random, sector, minimum, maximum);
            int x = sample.getX();
            int z = sample.getZ();
            int startY = sample.getY();
            for (int offset = 0; offset < 32; offset++) {
                BlockPos position = new BlockPos(x, startY - offset, z);
                if (!level.hasChunkAt(position)) break;
                Candidate candidate = validateCandidate(level, players, position, mob).orElse(null);
                if (candidate != null) return candidate;
                if (isFloor(level, position)) break;
            }
        }
        return null;
    }

    static BlockPos samplePosition(Vec3 anchor, RandomSource random, int sector, int minimum, int maximum) {
        int safeMaximum = Math.max(minimum, maximum);
        double angle = sector >= 0
                ? (sector + random.nextDouble()) * Math.PI / 4.0
                : random.nextDouble() * Math.PI * 2.0;
        double radius = minimum + random.nextDouble() * (safeMaximum - minimum);
        return new BlockPos((int) Math.floor(anchor.x + Math.cos(angle) * radius),
                (int) Math.floor(anchor.y) + random.nextInt(25) - 8,
                (int) Math.floor(anchor.z + Math.sin(angle) * radius));
    }

    static Optional<Candidate> validateCandidate(ServerLevel level, List<ServerPlayer> players,
                                                 BlockPos position, Mob mob) {
        return inspectCandidate(level, players, position, mob).candidate();
    }

    static CandidateValidation inspectCandidate(ServerLevel level, List<ServerPlayer> players,
                                                BlockPos position, Mob mob) {
        if (!level.hasChunkAt(position) || !isFloor(level, position)) {
            return CandidateValidation.rejected(Rejection.OCCUPIED);
        }
        int localLight = Math.max(level.getBrightness(LightLayer.BLOCK, position),
                level.getBrightness(LightLayer.SKY, position));
        if (localLight > DirectorConfig.BLOCK_LIGHT_LIMIT.get() || level.canSeeSky(position)) {
            return CandidateValidation.rejected(Rejection.LIT);
        }
        if (visibleToAny(level, position, players)) {
            return CandidateValidation.rejected(Rejection.VISIBLE);
        }
        ServerPlayer target = players.stream().filter(player -> player.isAlive())
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(Vec3.atCenterOf(position))))
                .orElse(null);
        if (target == null) return CandidateValidation.rejected(Rejection.NO_TARGET);
        mob.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.0F, 0.0F);
        if (!level.noCollision(mob)) return CandidateValidation.rejected(Rejection.COLLISION);
        mob.setOnGround(true);
        Path path = mob.getNavigation().createPath(target.blockPosition(), 0);
        if (path == null || !path.canReach()) return CandidateValidation.rejected(Rejection.UNREACHABLE);
        return CandidateValidation.accepted(new Candidate(position.immutable(), target));
    }

    static boolean isFloor(ServerLevel level, BlockPos position) {
        return level.getBlockState(position).getCollisionShape(level, position).isEmpty()
                && level.getFluidState(position).isEmpty()
                && level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()
                && level.getFluidState(position.above()).isEmpty()
                && level.getBlockState(position.below()).isFaceSturdy(level, position.below(), net.minecraft.core.Direction.UP);
    }

    static boolean visibleToAny(ServerLevel level, BlockPos position, List<ServerPlayer> players) {
        Vec3 destination = Vec3.atCenterOf(position).add(0.0, 0.5, 0.0);
        for (ServerPlayer player : players) {
            if (player.distanceToSqr(destination) > 128.0 * 128.0) continue;
            HitResult result = level.clip(new ClipContext(player.getEyePosition(), destination,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (result.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    record Candidate(BlockPos position, ServerPlayer target) {}
    enum Rejection { NONE, OCCUPIED, LIT, VISIBLE, NO_TARGET, COLLISION, UNREACHABLE }
    record CandidateValidation(Optional<Candidate> candidate, Rejection rejection) {
        static CandidateValidation accepted(Candidate candidate) {
            return new CandidateValidation(Optional.of(candidate), Rejection.NONE);
        }
        static CandidateValidation rejected(Rejection rejection) {
            return new CandidateValidation(Optional.empty(), rejection);
        }
    }
    private record Selection(EntityType<?> type, ResourceLocation entity, int cost, EcologyDefinition.Role role, String habitat) {
        Selection(EntityType<?> type, ResourceLocation entity, int cost, EcologyDefinition.Role role) {
            this(type, entity, cost, role, "legacy_noise");
        }
    }
    record SpawnResult(boolean spawned, Mob mob, ResourceLocation entity, int cost, EcologyDefinition.Role role,
                       boolean terminal, ResourceLocation selectedEntity, boolean nativeReplacement, UUID paidRoot) {
        SpawnResult(boolean spawned, Mob mob, ResourceLocation entity, int cost, EcologyDefinition.Role role) {
            this(spawned, mob, entity, cost, role, false, entity, false, mob == null ? null : mob.getUUID());
        }
        static SpawnResult failed() { return new SpawnResult(false, null, null, 0, null); }
        static SpawnResult terminalFailure() { return new SpawnResult(false, null, null, 0, null, true, null, false, null); }
    }
}
