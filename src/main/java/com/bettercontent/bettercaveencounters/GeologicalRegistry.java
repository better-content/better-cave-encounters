package com.bettercontent.bettercaveencounters;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Separate from legacy noise/pacing. Publication waits for the newly bound block tags. */
final class GeologicalRegistry extends SimpleJsonResourceReloadListener {
    static final GeologicalRegistry INSTANCE = new GeologicalRegistry();
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile Map<ResourceLocation, GeologicalDefinition> pending;
    private final Map<ResourceKey<Level>, TickCache> caches = new LinkedHashMap<>();
    private volatile String reloadFailure = "none";

    private GeologicalRegistry() { super(new Gson(), "geological_ecologies"); }

    @Override protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        try {
            Map<ResourceLocation, GeologicalDefinition> parsed = new LinkedHashMap<>();
            resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                    parsed.put(e.getKey(), GeologicalDefinition.parse(e.getKey(), e.getValue().getAsJsonObject())));
            validatePolicies(parsed);
            pending = Collections.unmodifiableMap(parsed);
            if (parsed.isEmpty()) publishBoundTags();
        } catch (RuntimeException failure) {
            pending = null;
            reloadFailure = failure.toString();
            DepthDirectorMod.LOGGER.error("Geological reload rejected; retaining last valid snapshot", failure);
        }
    }

    private static Map<ResourceLocation, GeologicalDefinition.Entry> validatePolicies(Map<ResourceLocation, GeologicalDefinition> definitions) {
        Map<ResourceLocation, GeologicalDefinition.Entry> canonical = new LinkedHashMap<>();
        definitions.values().forEach(definition -> {
            for (List<GeologicalDefinition.Entry> roster : List.of(definition.ordinary(), definition.director())) {
                java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
                roster.forEach(entry -> {
                    ResourceLocation id = entry.roster().entity();
                    if (!seen.add(id)) throw new IllegalArgumentException("Duplicate entity in " + definition.id() + ": " + id);
                    GeologicalDefinition.Offspring nativePolicy = NativeLineagePolicies.get(id);
                    if (!nativePolicy.equals(GeologicalDefinition.Offspring.NONE) && !nativePolicy.equals(entry.offspring()))
                        throw new IllegalArgumentException("Pinned native offspring envelope differs for " + id);
                    GeologicalDefinition.Entry previous = canonical.putIfAbsent(id, entry);
                    if (previous != null && !previous.sameLimits(entry))
                        throw new IllegalArgumentException("Inconsistent shared species limits/lineage: " + id);
                });
            }
        });
        return canonical;
    }

    synchronized void publishBoundTags() {
        Map<ResourceLocation, GeologicalDefinition> definitions = pending;
        if (definitions == null) return;
        pending = null;
        try {
            Map<Block, GeologicalDefinition> byBlock = new LinkedHashMap<>();
            for (GeologicalDefinition definition : definitions.values()) {
                int count = 0;
                for (Block block : ForgeRegistries.BLOCKS.getValues()) {
                    if (!block.defaultBlockState().is(definition.rocks())) continue;
                    GeologicalDefinition previous = byBlock.putIfAbsent(block, definition);
                    if (previous != null) throw new IllegalArgumentException("Overlapping raw rock tags: "
                            + definition.id() + "/" + previous.id() + " at " + ForgeRegistries.BLOCKS.getKey(block));
                    count++;
                }
                if (count == 0) throw new IllegalArgumentException("Empty/unresolved raw rock tag: " + definition.rocks().location());
            }
            Map<ResourceLocation, GeologicalDefinition.Entry> canonical = validatePolicies(definitions);
            canonical.forEach((id, entry) -> {
                if (!ForgeRegistries.ENTITY_TYPES.containsKey(id))
                    DepthDirectorMod.LOGGER.warn("Optional geological entity absent: {}", id);
                else if (ForgeRegistries.ENTITY_TYPES.getValue(id).is(SpawnLocator.DENIED))
                    throw new IllegalArgumentException("Director-denied entity in geological roster: " + id);
            });
            snapshot = new Snapshot(definitions, Map.copyOf(byBlock), Map.copyOf(canonical));
            reloadFailure = "none";
            clearCache();
            DepthDirectorMod.LOGGER.info("Published {} geological habitats, {} raw rocks, {} species",
                    definitions.size(), byBlock.size(), canonical.size());
        } catch (RuntimeException failure) {
            reloadFailure = failure.toString();
            DepthDirectorMod.LOGGER.error("Geological tags rejected; retaining last valid snapshot", failure);
        }
    }

    synchronized void clearCache() { caches.clear(); }
    String reloadFailure() { return reloadFailure; }
    Map<ResourceLocation, GeologicalDefinition> definitions() { return snapshot.definitions(); }

    GeologicalDefinition.Offspring offspring(ResourceLocation entity) {
        GeologicalDefinition.Entry entry = snapshot.canonical().get(entity);
        return entry == null ? NativeLineagePolicies.get(entity) : entry.offspring();
    }

    Sample sample(ServerLevel level, BlockPos position) {
        if (!controlled(level, position)) return new Sample(Map.of(), 0, "outside_controlled_overworld");
        Snapshot current = snapshot;
        if (current.definitions().isEmpty()) return new Sample(Map.of(), 0, "no_geological_definitions");
        TickCache cache = caches.computeIfAbsent(level.dimension(), ignored -> new TickCache());
        if (cache.tick != level.getGameTime()) { cache.samples.clear(); cache.tick = level.getGameTime(); }
        return cache.samples.computeIfAbsent(position.immutable(), ignored -> {
            Map<GeologicalDefinition, Integer> votes = new LinkedHashMap<>();
            for (int x : GeologyWeights.OFFSETS) for (int y : GeologyWeights.OFFSETS) for (int z : GeologyWeights.OFFSETS) {
                BlockPos sample = position.offset(x, y, z);
                if (sample.getY() < level.getMinBuildHeight() || sample.getY() >= level.getMaxBuildHeight() || !level.hasChunkAt(sample)) continue;
                GeologicalDefinition definition = current.blocks().get(level.getBlockState(sample).getBlock());
                if (definition != null) votes.merge(definition, 1, Integer::sum);
            }
            int count = votes.values().stream().mapToInt(Integer::intValue).sum();
            return new Sample(Collections.unmodifiableMap(votes), count,
                    count < GeologyWeights.MINIMUM_SAMPLES ? "insufficient_mapped_rock" : "none");
        });
    }

    static boolean controlled(ServerLevel level, BlockPos position) {
        return level.dimension() == Level.OVERWORLD && level.hasChunkAt(position)
                && DepthMath.isControlled(position.getY(), DirectorRuntime.controlCeiling(level, position));
    }

    static double depth(ServerLevel level, BlockPos position) {
        return DepthMath.depthFactor(position.getY(), DirectorRuntime.controlCeiling(level, position), level.getMinBuildHeight());
    }

    Choice chooseDirector(ServerLevel level, BlockPos position, RandomSource random,
                          boolean allowHeavy, int maximumCost, Predicate<EcologyDefinition.Entry> selectable) {
        Sample sample = sample(level, position);
        if (!sample.mapped()) return null;
        double depth = depth(level, position); // Encounter depth still drives pacing, never a mapped candidate's species gate.
        Map<GeologicalDefinition, List<GeologicalDefinition.Entry>> eligible = new LinkedHashMap<>();
        sample.votes().forEach((habitat, votes) -> {
            List<GeologicalDefinition.Entry> entries = habitat.director().stream()
                    .filter(e -> usable(e, false)).filter(e -> e.eligible(level, position, depth))
                    .filter(e -> allowHeavy || e.roster().role() != EcologyDefinition.Role.HEAVY)
                    .filter(e -> e.roster().cost() + e.offspring().credits() <= maximumCost)
                    .filter(e -> selectable.test(e.roster())).toList();
            if (!entries.isEmpty()) eligible.put(habitat, entries);
        });
        if (eligible.isEmpty()) return null;
        int roll = random.nextInt(eligible.keySet().stream().mapToInt(sample.votes()::get).sum());
        GeologicalDefinition selected = null;
        for (GeologicalDefinition habitat : eligible.keySet()) {
            roll -= sample.votes().get(habitat);
            if (roll < 0) { selected = habitat; break; }
        }
        List<GeologicalDefinition.Entry> entries = eligible.get(selected);
        roll = random.nextInt(entries.stream().mapToInt(e -> e.roster().selectionWeight()).sum());
        for (GeologicalDefinition.Entry entry : entries) {
            roll -= entry.roster().selectionWeight();
            if (roll < 0) return new Choice(selected.id(), entry);
        }
        throw new IllegalStateException("Geological draw lost probability mass");
    }

    List<MobSpawnSettings.SpawnerData> ordinary(ServerLevel level, BlockPos position) {
        Sample sample = sample(level, position);
        if (!sample.mapped()) return List.of();
        double depth = depth(level, position);
        Map<ResourceLocation, Double> probabilities = GeologyWeights.combine(sample.votes(), habitat -> {
            Map<ResourceLocation, Integer> weights = new LinkedHashMap<>();
            habitat.ordinary().stream().filter(e -> usable(e, true)).filter(e -> e.eligible(level, position, depth))
                    .forEach(e -> weights.put(e.roster().entity(), e.roster().selectionWeight()));
            return weights;
        });
        List<MobSpawnSettings.SpawnerData> result = new ArrayList<>();
        GeologyWeights.integerWeights(probabilities).forEach((id, weight) -> {
            if (weight > 0) result.add(new MobSpawnSettings.SpawnerData(ForgeRegistries.ENTITY_TYPES.getValue(id), weight, 1, 1));
        });
        return result;
    }

    boolean ordinaryMember(ServerLevel level, BlockPos position, EntityType<?> type) {
        Sample sample = sample(level, position);
        if (!sample.mapped() || type.getCategory() != MobCategory.MONSTER || type.is(SpawnLocator.DENIED)) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        double depth = depth(level, position);
        return sample.votes().keySet().stream().flatMap(h -> h.ordinary().stream())
                .anyMatch(e -> e.roster().entity().equals(id) && e.eligible(level, position, depth) && usable(e, true));
    }

    private static boolean usable(GeologicalDefinition.Entry entry, boolean ordinary) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entry.roster().entity())) return false;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entry.roster().entity());
        return type != null && !type.is(SpawnLocator.DENIED) && (type.getCategory() == MobCategory.MONSTER
                || (!ordinary && entry.roster().role() == EcologyDefinition.Role.HEAVY && type.getCategory() == MobCategory.CREATURE));
    }

    record Choice(ResourceLocation habitat, GeologicalDefinition.Entry entry) {}
    record Sample(Map<GeologicalDefinition, Integer> votes, int mappedSamples, String fallback) {
        boolean mapped() { return fallback.equals("none"); }
        double ordinaryAdmission() {
            return mappedSamples == 0 ? 0 : votes.entrySet().stream()
                    .mapToDouble(e -> e.getKey().ordinaryAdmission() * e.getValue() / mappedSamples).sum();
        }
        String label() {
            return votes.entrySet().stream().map(e -> e.getKey().id() + ":" + e.getValue())
                    .collect(java.util.stream.Collectors.joining(","));
        }
    }
    private record Snapshot(Map<ResourceLocation, GeologicalDefinition> definitions,
                            Map<Block, GeologicalDefinition> blocks,
                            Map<ResourceLocation, GeologicalDefinition.Entry> canonical) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of());
    }
    private static final class TickCache {
        long tick = Long.MIN_VALUE;
        final Map<BlockPos, Sample> samples = new LinkedHashMap<>();
    }
}
