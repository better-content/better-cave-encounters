package com.bettercontent.bettercaveencounters;

import com.bettercontent.bettercaveencounters.worldtest.DepthDirectorWorldGameTests;
import com.bettercontent.bettercaveencounters.worldtest.DepthDirectorWorldTestMod;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Real native actors and the real natural spawning loop. No command-spawn ambient substitute. */
@GameTestHolder("better_cave_encounters_geology_tests")
@PrefixGameTestTemplate(false)
public final class GeologicalWorldGameTests {
    private GeologicalWorldGameTests() {}

    @GameTest(templateNamespace = DepthDirectorWorldTestMod.MOD_ID, template = "director_cave", timeoutTicks = 240_000)
    public static void pinnedGeologyCatalogueLineagesAndNaturalPopulations(GameTestHelper helper) {
        Proof proof = new Proof(helper);
        MinecraftForge.EVENT_BUS.register(proof);
        helper.onEachTick(proof::tick);
    }

    public static final class Proof {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos center, candidate, sampleProbe;
        private final JsonObject inputs;
        private final List<ResourceLocation> catalogue = new ArrayList<>();
        private final Map<String, Object> observations = new LinkedHashMap<>();
        private final List<Mob> naturalPending = new ArrayList<>();
        private final Set<ResourceLocation> naturalSeen = new HashSet<>();
        private final Map<BlockPos, BlockState> originalSamples = new LinkedHashMap<>();
        private final Map<UUID, Mob> offspring = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> explosionBlocks = new HashMap<>();
        private final List<GeologicalDefinition> habitats;
        private ServerPlayer player;
        private Mob current, commander, killVictim;
        private ResourceLocation currentId;
        private Vec3 startingPosition;
        private CompoundTag unloadedEntity;
        private UUID family;
        private int phase, index, step, attempts, naturalIndex;
        private long deadline, caseStarted;
        private boolean moved, projectile, grapple, dug, exploded, unloadVerified;
        private int nativeAttacks;
        private boolean finished, vetoNaturalCandidates;
        private int potentialVetoes;
        private long admittedTick = Long.MIN_VALUE;
        private int admissionsThisTick;
        private long admittedSecond = Long.MIN_VALUE;
        private int admissionsThisSecond;

        Proof(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel();
            BlockPos horizontal = helper.absolutePos(new BlockPos(-256, 0, -256));
            center = new BlockPos(horizontal.getX(), -56, horizontal.getZ());
            candidate = DepthDirectorWorldGameTests.roomPosition(center, 0, 42);
            sampleProbe = center.offset(0, 0, 64);
            inputs = resource();
            habitats = GeologicalRegistry.INSTANCE.definitions().values().stream()
                    .sorted(Comparator.comparing(d -> d.id().toString())).toList();
            helper.assertTrue(habitats.size() == 10, "exactly ten pack geological definitions must load");
            Set<ResourceLocation> ids = new LinkedHashSet<>();
            inputs.getAsJsonArray("additions").forEach(e -> ids.add(new ResourceLocation(e.getAsString())));
            helper.assertTrue(ids.size() == 20, "all twenty additions must be declared");
            inputs.getAsJsonObject("reproductive_actors").keySet().forEach(id -> ids.add(new ResourceLocation(id)));
            ids.add(new ResourceLocation("iceandfire:deathworm"));
            catalogue.addAll(ids);
            observations.put("scope", "native source fixture, not full-pack same-artifact acceptance");
            observations.put("targets", catalogue.stream().map(Object::toString).toList());
            observations.put("actors", new LinkedHashMap<String, Object>());
        }

        public void tick() {
            if (finished) return;
            try { advance(); }
            catch (RuntimeException | Error failure) {
                observations.put("failure", failure.toString()); write();
                MinecraftForge.EVENT_BUS.unregister(this); finished = true; throw failure;
            }
        }

        private void advance() {
            long now = level.getGameTime();
            if (player != null) {
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                // Isolate native mechanics from scheduled waves; the original verifyWorld
                // fixture separately exercises the unmodified full pacing/pressure loop.
                DirectorSavedData.get(level.getServer()).track(player.getUUID()).cooldownUntil(now + 20_000);
            }
            checkActualNaturalInsertions();
            if (phase == 0) {
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
                DepthDirectorWorldGameTests.buildArena(level, center);
                // A physical overhead layer makes local controlled depth independent of a flat world's heightmap.
                for (int x = -72; x <= 72; x++) for (int z = -72; z <= 72; z++)
                    level.setBlock(new BlockPos(center.getX() + x, 72, center.getZ() + z), Blocks.STONE.defaultBlockState(), 2);
                registryContract();
                List<BlockPos> samples = stencil(sampleProbe);
                for (BlockPos p : samples) { originalSamples.put(p, level.getBlockState(p)); level.setBlock(p, rock(habitats.get(0)), 2); }
                GeologicalRegistry.INSTANCE.clearCache();
                helper.assertTrue(GeologicalRegistry.INSTANCE.sample(level, sampleProbe).mappedSamples() == 125, "pure stencil has 125 raw votes");
                for (int n = 0; n < samples.size(); n++) level.setBlock(samples.get(n), rock(habitats.get(n < 50 ? 0 : n < 100 ? 1 : 2)), 2);
                helper.assertTrue(GeologicalRegistry.INSTANCE.sample(level, sampleProbe).votes().size() == 1, "same-tick cache must be stable");
                phase = 1; deadline = now + 1; return;
            }
            if (phase == 1 && now >= deadline) {
                GeologicalRegistry.Sample mixed = GeologicalRegistry.INSTANCE.sample(level, sampleProbe);
                helper.assertTrue(mixed.votes().size() == 3 && mixed.mappedSamples() == 125, "three habitats must survive sampling");
                helper.assertTrue(new HashSet<>(mixed.votes().values()).equals(Set.of(25, 50)), "mixed rock votes must be proportional");
                List<BlockPos> samples = stencil(sampleProbe);
                for (int n = 7; n < samples.size(); n++) level.setBlock(samples.get(n), Blocks.AIR.defaultBlockState(), 2);
                phase = 2; deadline = now + 1; return;
            }
            if (phase == 2 && now >= deadline) {
                GeologicalRegistry.Sample thin = GeologicalRegistry.INSTANCE.sample(level, sampleProbe);
                helper.assertTrue(!thin.mapped() && thin.mappedSamples() == 7, "seven mapped samples must use fallback");
                originalSamples.forEach((p, state) -> level.setBlock(p, state, 2));
                reloadContract();
                player = registeredPlayer(); phase = 3; deadline = now + 40; return;
            }
            if (phase == 3 && now >= deadline) { startCase(); phase = 4; return; }
            if (phase == 4) { actorTick(now); return; }
            if (phase == 5) { naturalTick(now); return; }
            if (phase == 6) { lootContract(); complete(); }
        }

        private void registryContract() {
            Set<Block> seen = new HashSet<>();
            for (GeologicalDefinition habitat : habitats) {
                for (Block block : ForgeRegistries.BLOCKS.getValues()) if (block.defaultBlockState().is(habitat.rocks()))
                    helper.assertTrue(seen.add(block), "raw habitat overlap at " + ForgeRegistries.BLOCKS.getKey(block));
                for (GeologicalDefinition.Entry e : habitat.director()) {
                    EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(e.roster().entity());
                    helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(e.roster().entity()) && type != null
                            && e.roster().entity().equals(ForgeRegistries.ENTITY_TYPES.getKey(type)) && !type.is(SpawnLocator.DENIED),
                            "unresolved/denied target " + e.roster().entity());
                    helper.assertTrue(type != null && (type.getCategory() == MobCategory.MONSTER
                            || e.roster().role() == EcologyDefinition.Role.HEAVY && type.getCategory() == MobCategory.CREATURE),
                            "unexpected native category for " + e.roster().entity());
                }
            }
            helper.assertTrue(seen.size() == 16, "native schist is one axis-valued block; expect sixteen raw block IDs");
            BlockPos outside = new BlockPos(29_000_000, -56, 29_000_000);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            helper.assertTrue(!GeologicalRegistry.INSTANCE.sample(level, outside).mapped(), "unloaded geology must be fallback");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "sampling must not load chunks");
        }

        private void reloadContract() {
            Map<ResourceLocation, JsonElement> valid = new LinkedHashMap<>();
            try (var paths = Files.list(Path.of("kubejs/data/better_cave_encounters/geological_ecologies"))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList())
                    valid.put(new ResourceLocation("better_cave_encounters", path.getFileName().toString().replace(".json", "")), JsonParser.parseString(Files.readString(path)));
            } catch (IOException failure) { throw new IllegalStateException(failure); }
            Map<ResourceLocation, GeologicalDefinition> before = GeologicalRegistry.INSTANCE.definitions();
            JsonObject invalid = valid.values().iterator().next().getAsJsonObject().deepCopy(); invalid.addProperty("schema_version", 99);
            GeologicalRegistry.INSTANCE.apply(Map.of(new ResourceLocation("fixture", "invalid"), invalid), null, null);
            GeologicalRegistry.INSTANCE.publishBoundTags();
            helper.assertTrue(GeologicalRegistry.INSTANCE.definitions() == before, "invalid reload must retain the exact valid snapshot");
            GeologicalRegistry.INSTANCE.apply(valid, null, null); GeologicalRegistry.INSTANCE.publishBoundTags();
            helper.assertTrue(GeologicalRegistry.INSTANCE.definitions().size() == 10 && GeologicalRegistry.INSTANCE.reloadFailure().equals("none"), "valid reload must recover");
            observations.put("sampling_and_reload", "observed");
        }

        private void startCase() {
            currentId = catalogue.get(index);
            GeologicalDefinition habitat = habitats.stream().filter(h -> h.director().stream().anyMatch(e -> e.roster().entity().equals(currentId))).findFirst().orElseThrow();
            DepthDirectorWorldGameTests.buildArena(level, center);
            player.stopRiding();
            player.teleportTo(level, center.getX() + .5, center.getY(), center.getZ() + .5, 0, 0);
            paint(habitat);
            if (index == 0 && attempts == 0) ordinaryScopeContract();
            DirectorRuntime.INSTANCE.reset();
            family = UUID.randomUUID(); offspring.clear();
            EcologyRegistry.Blend deliberatelyUnrelatedTiming = EcologyRegistry.INSTANCE.blend(level.getSeed(), center.getCenter());
            SpawnLocator.SpawnResult result = DirectorLineages.ownedBy(new UUID(0, 0), family, () ->
                    SpawnLocator.spawnAt(level, List.of(player), deliberatelyUnrelatedTiming, 0, RandomSource.create(0xBADC0DEL + index + attempts),
                            candidate, true, 128, e -> e.entity().equals(currentId)));
            helper.assertTrue(result.spawned(), "warned-position native admission failed for " + currentId + ": " + SpawnLocator.diagnose(level, List.of(player), currentId, candidate));
            current = result.mob(); countAdmission(); startingPosition = current.position(); caseStarted = level.getGameTime();
            GeologicalDefinition.Entry entry = habitat.director().stream().filter(e -> e.roster().entity().equals(currentId)).findFirst().orElseThrow();
            helper.assertTrue(result.cost() == entry.roster().cost() + entry.offspring().credits(), "base plus prepaid reservation must be charged once");
            helper.assertTrue(current.getTarget() == player && SpawnLocator.isDirectorMob(current), "Director targeting/provenance missing for " + currentId);
            helper.assertTrue(level.noCollision(current) && level.getWorldBorder().isWithinBounds(current.getBoundingBox()), "finalized hitbox must fit");
            helper.assertTrue(current.getPersistentData().getString("DepthDirectorSpawnHabitat").equals(habitat.id().toString()), "local geology/depth must override unrelated timing blend and shallow encounter depth");
            if (entry.roster().role() == EcologyDefinition.Role.HEAVY)
                helper.assertTrue(DirectorLineages.hasHeavy(level.getServer(), family), "family heavy must be recorded at insertion");
            explosionBlocks.clear(); moved = projectile = grapple = dug = exploded = unloadVerified = false; nativeAttacks = 0;
            if (currentId.toString().equals("iceandfire:troll"))
                for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) for (int y = -4; y <= 3; y++) {
                    BlockPos p = center.offset(x, y, z); explosionBlocks.put(p, level.getBlockState(p));
                }
            if (current instanceof Slime slime) slime.setSize(4, true); // Exercise the declared permitted-size envelope, not a native chance change.
            step = 0;
        }

        private void ordinaryScopeContract() {
            helper.assertTrue(GeologicalRegistry.INSTANCE.sample(level, candidate).mapped(), "scope probes need mapped raw geology");
            for (EntityType<?> type : List.of(EntityType.BAT, EntityType.COW, EntityType.SQUID)) {
                Mob probe = (Mob) type.create(level);
                probe.moveTo(candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5, 0, 0);
                var check = new net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck(probe, level, MobSpawnType.NATURAL, null);
                com.bettercontent.bettercompatfixes.api.NaturalSpawnContext.during(() -> { GeologicalSpawns.position(check); return null; });
                helper.assertTrue(check.getResult() == net.minecraftforge.eventbus.api.Event.Result.DEFAULT, "geology must not touch non-MONSTER category " + type);
                probe.discard();
            }
            Mob magma = EntityType.MAGMA_CUBE.create(level);
            magma.moveTo(candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5, 0, 0);
            var external = new net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck(magma, level, MobSpawnType.NATURAL, null);
            GeologicalSpawns.position(external);
            helper.assertTrue(external.getResult() == net.minecraftforge.eventbus.api.Event.Result.DEFAULT, "external NATURAL spawners, including In Control lava Magma Cubes, are not geological attempts");
            var nativeAttempt = new net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck(magma, level, MobSpawnType.NATURAL, null);
            com.bettercontent.bettercompatfixes.api.NaturalSpawnContext.during(() -> { GeologicalSpawns.position(nativeAttempt); return null; });
            helper.assertTrue(nativeAttempt.getResult() == net.minecraftforge.eventbus.api.Event.Result.DENY, "same unlisted monster must be rejected inside the native geological attempt");
            magma.discard();
            observations.put("ordinary_handler_negative_controls", "non-MONSTER and external NATURAL preserved; not an actual In Control spawning verdict");
        }

        private void actorTick(long now) {
            if (current != null && current.isAlive()) {
                moved |= current.position().distanceToSqr(startingPosition) > 1;
                grapple |= player.getVehicle() == current;
                if (currentId.toString().equals("alexscaves:corrodent")) dug |= nativeBoolean(current, "isDigging");
            }
            if (step == 0 && !unloadVerified && now - caseStarted >= 20) {
                CompoundTag snapshot = DirectorLineages.get(level.getServer()).save(new CompoundTag());
                helper.assertTrue(snapshot.equals(DirectorLineages.load(snapshot).save(new CompoundTag())), "lineage save round trip changed credits or members");
                unloadedEntity = new CompoundTag();
                helper.assertTrue(current.save(unloadedEntity), "live native actor must save before unload");
                current.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                step = 1; deadline = now + 2; return;
            }
            if (step == 0 && now - caseStarted >= 360) {
                helper.assertTrue(moved || projectile || nativeAttacks > 0, "native AI did not navigate/attack for " + currentId);
                if (currentId.toString().equals("goety:bone_spider")) helper.assertTrue(projectile, "Bone Spider must actually fire a native projectile");
                if (currentId.toString().equals("twilightforest:pinch_beetle")) helper.assertTrue(grapple, "Pinch Beetle must actually grapple the dummy player");
                if (currentId.toString().equals("alexscaves:corrodent")) helper.assertTrue(dug, "Corrodent native digging must be observed");
                if (currentId.toString().equals("iceandfire:troll")) {
                    boolean changed = explosionBlocks.entrySet().stream().anyMatch(e -> !level.getBlockState(e.getKey()).equals(e.getValue()));
                    helper.assertTrue(changed, "native troll terrain damage must occur with pack griefing enabled");
                    exploded |= changed; // Native custom explosions need not post vanilla ExplosionEvent.
                }
                if (currentId.toString().equals("iceandfire:deathworm")) {
                    var terrain = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation("better_compat_fixes", "death_worm_burrowable"));
                    helper.assertTrue(level.getBlockState(candidate.below()).is(terrain), "Unearthed floor must use existing Compat burrow terrain tag");
                    helper.assertTrue(current.getY() < candidate.getY() || current.getBoundingBox().minY < candidate.getY() - .1, "native Death Worm must actually enter its raw-rock substrate");
                }
                if (currentId.toString().startsWith("iceandfire:dread_")) {
                    beginDreadKill(); step = 3; deadline = now + 80; return;
                }
                if (current.isAlive()) current.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                step = 2; deadline = now + 260; return;
            }
            if (step == 1 && now >= deadline) {
                Entity restored = EntityType.loadEntityRecursive(unloadedEntity, level, e -> e);
                helper.assertTrue(restored instanceof Mob, "native entity save/load must retain Mob type");
                current = (Mob) restored;
                helper.assertTrue(level.addFreshEntity(current), "unloaded marked mob must reinsert without a new root charge");
                helper.assertTrue(SpawnLocator.isDirectorMob(current) && current.getTarget() == player, "reload must restore pursuit binding");
                if (currentId.toString().equals("iceandfire:troll") || currentId.toString().equals("born_in_chaos_v1:fallen_chaos_knight"))
                    helper.assertTrue(DirectorLineages.hasHeavy(level.getServer(), family), "heavy identity lost across unload/reload");
                unloadVerified = true; step = 0; return;
            }
            if (step == 3 && now >= deadline) {
                helper.assertTrue(!killVictim.isAlive(), "actual native Dread attack must kill its victim");
                helper.assertTrue(!offspring.isEmpty(), "commander-backed Dread kill must use the causal conversion hook");
                helper.assertTrue(!SpawnLocator.isDirectorMob(commander) && !SpawnLocator.isDirectorMob(killVictim), "unowned commander/victim must remain unmarked");
                current.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                step = 2; deadline = now + 260; return;
            }
            if (step == 2 && now >= deadline) {
                JsonObject reproductive = inputs.getAsJsonObject("reproductive_actors").getAsJsonObject(currentId.toString());
                if (reproductive != null && offspring.isEmpty() && !currentId.toString().startsWith("iceandfire:dread_")) {
                    helper.assertTrue(++attempts < 32, "native reproductive path never produced a child for " + currentId);
                    discardFixtureMobs(); startCase(); return; // Native chance is unchanged; bounded independent trials.
                }
                if (current instanceof Slime || currentId.toString().equals("tconstruct:ender_slime")) {
                    Mob split = offspring.values().stream().filter(m -> m.isAlive() && m instanceof Slime s && s.getSize() > 1).findFirst().orElse(null);
                    if (split != null) { split.hurt(level.damageSources().genericKill(), Float.MAX_VALUE); deadline = now + 40; return; }
                }
                if (reproductive != null) helper.assertTrue(offspring.size() <= reproductive.get("credits").getAsInt(), "native descendants exceeded prepaid envelope");
                actorResults().put(currentId.toString(), Map.of("native_movement", moved, "native_attacks", nativeAttacks,
                        "native_projectile", projectile, "grapple", grapple, "digging", dug, "terrain_attack", exploded,
                        "descendants", offspring.size(), "native_trials", attempts + 1, "unload_reload", unloadVerified));
                write(); discardFixtureMobs(); attempts = 0; index++;
                if (index < catalogue.size()) { startCase(); return; }
                phase = 5; naturalIndex = 0; beginNatural();
            }
        }

        private void beginDreadKill() {
            EntityType<?> lichType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("iceandfire:dread_lich"));
            Entity lich = lichType == null ? null : lichType.create(level);
            helper.assertTrue(lich instanceof Mob, "pinned native Dread commander is missing");
            commander = (Mob) lich; commander.moveTo(current.position()); commander.setNoAi(true);
            helper.assertTrue(level.addFreshEntity(commander), "unmarked commander fixture insertion failed");
            try { current.getClass().getMethod("setCommanderId", UUID.class).invoke(current, commander.getUUID()); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned Dread commander API changed", failure); }
            var victim = EntityType.ZOMBIE.create(level);
            helper.assertTrue(victim != null, "native kill victim missing"); victim.moveTo(current.position()); victim.setHealth(1); victim.setNoAi(true);
            helper.assertTrue(level.addFreshEntity(victim), "unowned victim fixture insertion failed");
            killVictim = victim; commander.setTarget(victim); current.setTarget(victim);
            current.doHurtTarget(victim); // Some native attacks apply their damage on a later animation tick.
        }

        private void beginNatural() {
            discardFixtureMobs(); naturalSeen.clear(); paint(habitats.get(naturalIndex));
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, level.getServer());
            level.getChunkSource().setSpawnSettings(true, true);
            deadline = level.getGameTime() + 6_000;
            vetoNaturalCandidates = naturalIndex == 0; potentialVetoes = 0;
        }
        private void naturalTick(long now) {
            if (vetoNaturalCandidates) {
                helper.assertTrue(naturalSeen.isEmpty(), "cancelled potential lists must admit no geological natural actors");
                helper.assertTrue(now < deadline, "real native spawning never reached the independent potential veto");
                return;
            }
            if (naturalIndex == 0) {
                helper.assertTrue(potentialVetoes >= 32, "independent cancellation must be exercised before ordinary admission");
                observations.put("independent_potential_cancellation", potentialVetoes);
            }
            GeologicalDefinition habitat = habitats.get(naturalIndex);
            Set<ResourceLocation> required = new HashSet<>();
            habitat.ordinary().stream().filter(e -> e.eligible(level, candidate, GeologicalRegistry.depth(level, candidate)))
                    .forEach(e -> required.add(e.roster().entity()));
            if (naturalSeen.containsAll(required)) {
                observations.put("natural:" + habitat.id(), naturalSeen.stream().map(Object::toString).sorted().toList());
                write(); naturalIndex++;
                if (naturalIndex < habitats.size()) beginNatural();
                else { level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer()); phase = 6; }
                return;
            }
            helper.assertTrue(now < deadline, "real natural spawning failed to expose every ordinary member for " + habitat.id() + ": observed=" + naturalSeen + " required=" + required);
            // Clear completed native samples, not caps. New arrivals still traverse the real
            // server spawning loop and its per-player/global SpawnState checks.
            if (now % 100 == 0) for (Mob mob : level.getEntitiesOfClass(Mob.class, bounds()))
                if (!SpawnLocator.isDirectorMob(mob) && mob.getSpawnType() == MobSpawnType.NATURAL) mob.discard();
        }
        private void checkActualNaturalInsertions() {
            for (Mob mob : List.copyOf(naturalPending)) {
                if (level.getEntity(mob.getUUID()) == mob) {
                    helper.assertTrue(!SpawnLocator.isDirectorMob(mob), "native ordinary population was marked by proximity");
                    naturalSeen.add(ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()));
                }
                naturalPending.remove(mob);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void independentPotentialVeto(net.minecraftforge.event.level.LevelEvent.PotentialSpawns event) {
            if (phase != 5 || !vetoNaturalCandidates || event.getLevel() != level
                    || com.bettercontent.bettercompatfixes.api.NaturalSpawnContext.stage()
                        != com.bettercontent.bettercompatfixes.api.NaturalSpawnContext.Stage.PLACEMENT
                    || event.getMobCategory() != MobCategory.MONSTER
                    || !GeologicalRegistry.INSTANCE.sample(level, event.getPos()).mapped()) return;
            event.setCanceled(true);
            if (++potentialVetoes >= 32) vetoNaturalCandidates = false;
        }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void offspring(com.bettercontent.bettercompatfixes.api.event.MobOffspringAdmissionEvent event) {
            if (currentId == null || !SpawnLocator.isDirectorMob(event.parent())) return;
            event.onCompletion(inserted -> {
                if (inserted) { countAdmission(); offspring.put(event.child().getUUID(), event.child()); }
            });
        }
        @SubscribeEvent(priority = EventPriority.HIGH) public void joined(EntityJoinLevelEvent event) {
            if (event.isCanceled() || event.getLevel() != level) return;
            if (event.getEntity() instanceof Projectile shot && current != null && shot.getOwner() == current) projectile = true;
            if (!(event.getEntity() instanceof Mob mob)) return;
            if (phase != 5 || mob.getType().getCategory() != MobCategory.MONSTER
                    || mob.getSpawnType() != MobSpawnType.NATURAL
                    || !mob.getPersistentData().contains("DepthDirectorGeologicalAmbient")
                    || !GeologicalRegistry.INSTANCE.sample(level, mob.blockPosition()).mapped()) return;
            helper.assertTrue(!vetoNaturalCandidates, "cancelled revalidation list must prevent reaching natural insertion");
            helper.assertTrue(GeologicalRegistry.INSTANCE.ordinaryMember(level, mob.blockPosition(), mob.getType()), "native spawn escaped exact local membership");
            NaturalSpawner.SpawnState state = level.getChunkSource().getLastSpawnState();
            helper.assertTrue(state != null, "natural actor must originate in the real server spawn loop");
            int cap = MobCategory.MONSTER.getMaxInstancesPerChunk() * state.getSpawnableChunkCount() / (17 * 17);
            helper.assertTrue(state.getMobCategoryCounts().getInt(MobCategory.MONSTER) < cap, "native monster admission must respect the real global cap");
            naturalPending.add(mob);
        }
        @SubscribeEvent public void finalized(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
            if (phase == 5 && event.getSpawnType() == MobSpawnType.NATURAL
                    && event.getEntity().getPersistentData().contains("DepthDirectorGeologicalAmbient"))
                helper.assertTrue(!com.bettercontent.bettercompatfixes.api.NaturalSpawnContext.active(),
                        "native initialization/offspring must not inherit habitat-substitution authority");
        }
        @SubscribeEvent public void hurt(LivingHurtEvent event) {
            if (current != null && event.getSource().getEntity() == current) nativeAttacks++;
        }
        @SubscribeEvent public void terrain(ExplosionEvent.Detonate event) {
            if (currentId != null && currentId.toString().equals("iceandfire:troll")) {
                exploded = true; event.getAffectedBlocks().forEach(p -> explosionBlocks.putIfAbsent(p.immutable(), level.getBlockState(p)));
            }
        }
        private void countAdmission() {
            long tick = level.getGameTime(), second = Math.floorDiv(tick, 20);
            if (admittedTick != tick) { admittedTick = tick; admissionsThisTick = 0; }
            if (admittedSecond != second) { admittedSecond = second; admissionsThisSecond = 0; }
            helper.assertTrue(++admissionsThisTick <= DirectorConfig.MAX_SPAWNS_PER_TICK.get(), "descendants exceeded per-tick cap");
            helper.assertTrue(++admissionsThisSecond <= DirectorConfig.MAX_SPAWNS_PER_SECOND.get(), "descendants exceeded per-second cap");
            helper.assertTrue(DirectorRuntime.INSTANCE.activeDirectorMobCount() <= DirectorConfig.GLOBAL_DIRECTOR_CAP.get(), "descendants exceeded active cap");
        }

        private void lootContract() {
            Mob context = EntityType.ZOMBIE.create(level);
            LootParams parameters = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, context)
                    .withParameter(LootContextParams.ORIGIN, center.getCenter())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                    .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).create(LootContextParamSets.ENTITY);
            var sentinel = level.getServer().getLootData().getLootTable(new ResourceLocation(DepthDirectorWorldTestMod.MOD_ID, "policy_sentinel"));
            Set<ResourceLocation> dropped = new HashSet<>(); sentinel.getRandomItems(parameters).forEach(s -> dropped.add(ForgeRegistries.ITEMS.getKey(s.getItem())));
            helper.assertTrue(dropped.contains(new ResourceLocation("cataclysm:ancient_metal_ingot")) && dropped.contains(new ResourceLocation("cataclysm:black_steel_ingot")), "native material exports must survive the actual global loot policy");
            helper.assertTrue(!dropped.contains(new ResourceLocation("minecraft:netherite_ingot")) && !dropped.contains(new ResourceLocation("minecraft:elytra")), "actual KubeJS/LootJS policy must remove sentinel forbidden loot");
            Map<String, String> rewards = Map.of("aptrgangr", "strange_key", "coralssus", "coral_chunk", "ignited_revenant", "burning_ashes", "the_prowler", "redstone", "ender_golem", "void_core", "kobolediator", "ancient_metal_ingot");
            rewards.forEach((actor, reward) -> {
                var table = level.getServer().getLootData().getLootTable(new ResourceLocation("cataclysm", "entities/" + actor));
                ResourceLocation expected = new ResourceLocation(reward.equals("redstone") ? "minecraft" : "cataclysm", reward);
                boolean found = false;
                for (int n = 0; n < 2_048 && !found; n++) found = table.getRandomItems(parameters).stream().anyMatch(s -> ForgeRegistries.ITEMS.getKey(s.getItem()).equals(expected));
                helper.assertTrue(found, "native Cataclysm reward path disappeared: " + actor + "/" + expected);
            });
            observations.put("loot_policy_and_six_reward_paths", "observed");
        }

        private void paint(GeologicalDefinition habitat) {
            BlockState rock = rock(habitat);
            for (int x = -72; x <= 72; x++) for (int z = -72; z <= 72; z++) for (int y = -4; y <= -1; y++)
                level.setBlock(center.offset(x, y, z), rock, 2);
            GeologicalRegistry.INSTANCE.clearCache();
        }
        private BlockState rock(GeologicalDefinition habitat) {
            return ForgeRegistries.BLOCKS.getValues().stream().filter(b -> b.defaultBlockState().is(habitat.rocks())).findFirst().orElseThrow().defaultBlockState();
        }
        private ServerPlayer registeredPlayer() {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "geology-proof");
            ServerPlayer p = new ServerPlayer(level.getServer(), level, profile);
            p.setPos(center.getX() + .5, center.getY(), center.getZ() + .5);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, p);
            p.teleportTo(level, center.getX() + .5, center.getY(), center.getZ() + .5, 0, 0);
            p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000); p.setHealth(1_000);
            return p;
        }
        private void discardFixtureMobs() {
            player.stopRiding();
            for (Mob mob : level.getEntitiesOfClass(Mob.class, bounds())) mob.discard();
            if (commander != null && !commander.isRemoved()) commander.discard();
            commander = null; current = null;
        }
        private AABB bounds() { return new AABB(center).inflate(90, 30, 90); }
        @SuppressWarnings("unchecked") private Map<String, Object> actorResults() { return (Map<String, Object>) observations.get("actors"); }
        private void complete() {
            helper.assertTrue(actorResults().keySet().containsAll(catalogue.stream().map(Object::toString).toList()), "no failed target may be omitted");
            observations.put("native_fixture", "passed"); write(); discardFixtureMobs();
            level.getServer().getPlayerList().remove(player);
            MinecraftForge.EVENT_BUS.unregister(this); finished = true; helper.succeed();
        }
        private void write() {
            try { Files.writeString(Path.of("geological-observations.json"), new GsonBuilder().setPrettyPrinting().create().toJson(observations)); }
            catch (IOException failure) { throw new IllegalStateException(failure); }
        }
        private static boolean nativeBoolean(Mob mob, String name) {
            try { return (boolean) mob.getClass().getMethod(name).invoke(mob); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native API changed: " + name, failure); }
        }
        private static List<BlockPos> stencil(BlockPos origin) {
            List<BlockPos> result = new ArrayList<>();
            for (int x : GeologyWeights.OFFSETS) for (int y : GeologyWeights.OFFSETS) for (int z : GeologyWeights.OFFSETS) result.add(origin.offset(x, y, z));
            return result;
        }
        private static JsonObject resource() {
            try (var stream = GeologicalWorldGameTests.class.getResourceAsStream("/geological-qualification.json")) {
                if (stream == null) throw new IllegalStateException("Missing authored geological qualification inputs");
                return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException failure) { throw new IllegalStateException(failure); }
        }
    }
}
