package com.bettercontent.bettercaveencounters;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record GeologicalDefinition(ResourceLocation id, TagKey<Block> rocks, double ordinaryAdmission,
                            List<Entry> ordinary, List<Entry> director) {
    GeologicalDefinition {
        ordinary = List.copyOf(ordinary);
        director = List.copyOf(director);
        if (!Double.isFinite(ordinaryAdmission) || ordinaryAdmission < 0 || ordinaryAdmission > 1)
            throw new IllegalArgumentException("Invalid ordinary admission probability: " + id);
        if (ordinary.isEmpty() || director.isEmpty()) throw new IllegalArgumentException("Empty geological roster: " + id);
        if (ordinary.stream().anyMatch(e -> e.roster().role() == EcologyDefinition.Role.HEAVY))
            throw new IllegalArgumentException("Heavy in ordinary roster: " + id);
    }

    static GeologicalDefinition parse(ResourceLocation id, JsonObject root) {
        fields(root, java.util.Set.of("schema_version", "raw_rock_tag", "ordinary_admission", "ordinary", "director"));
        if (root.get("schema_version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported geological schema");
        return new GeologicalDefinition(id, TagKey.create(Registries.BLOCK,
                new ResourceLocation(root.get("raw_rock_tag").getAsString())),
                root.get("ordinary_admission").getAsDouble(), entries(root, "ordinary"), entries(root, "director"));
    }

    private static List<Entry> entries(JsonObject root, String field) {
        List<Entry> result = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray(field)) {
            JsonObject e = element.getAsJsonObject();
            fields(e, java.util.Set.of("entity", "role", "minimum_depth", "weight", "cost", "maximum_per_packet",
                    "maximum_per_encounter", "condition", "spawn_gamerule", "offspring"));
            EcologyDefinition.Entry entry = new EcologyDefinition.Entry(new ResourceLocation(e.get("entity").getAsString()),
                    EcologyDefinition.Role.valueOf(e.get("role").getAsString().toUpperCase(java.util.Locale.ROOT)),
                    e.has("minimum_depth") ? e.get("minimum_depth").getAsDouble() : 0,
                    e.has("weight") ? e.get("weight").getAsInt() : 0,
                    e.has("cost") ? e.get("cost").getAsInt() : 0,
                    e.has("maximum_per_packet") ? e.get("maximum_per_packet").getAsInt() : 0,
                    e.has("maximum_per_encounter") ? e.get("maximum_per_encounter").getAsInt() : 0);
            Condition condition = e.has("condition") ? Condition.valueOf(e.get("condition").getAsString()
                    .toUpperCase(java.util.Locale.ROOT)) : Condition.NONE;
            String gamerule = e.has("spawn_gamerule") ? e.get("spawn_gamerule").getAsString() : "";
            result.add(new Entry(entry, condition, gamerule, Offspring.parse(e.getAsJsonObject("offspring"))));
        }
        return result;
    }

    private static void fields(JsonObject object, java.util.Set<String> allowed) {
        for (String key : object.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown geological field: " + key);
    }

    record Entry(EcologyDefinition.Entry roster, Condition condition, String spawnGamerule, Offspring offspring) {
        Entry {
            if (!Double.isFinite(roster.minimumDepth())) throw new IllegalArgumentException("Non-finite depth");
        }
        boolean eligible(ServerLevel level, BlockPos position, double depth) {
            return roster.minimumDepth() <= depth && condition.matches(level, position)
                    && (spawnGamerule.isEmpty() || booleanGamerule(level, spawnGamerule));
        }
        boolean sameLimits(Entry other) {
            return roster.role() == other.roster.role() && roster.minimumDepth() == other.roster.minimumDepth()
                    && condition == other.condition && roster.cost() == other.roster.cost()
                    && roster.maximumPerPacket() == other.roster.maximumPerPacket()
                    && roster.maximumPerEncounter() == other.roster.maximumPerEncounter()
                    && offspring.equals(other.offspring);
        }
    }

    enum Condition {
        NONE, COLD, WATER_NEARBY;
        boolean matches(ServerLevel level, BlockPos position) {
            if (this == NONE) return true;
            if (this == COLD) return level.getBiome(position).value().coldEnoughToSnow(position);
            for (BlockPos sample : BlockPos.betweenClosed(position.offset(-4, -4, -4), position.offset(4, 4, 4))) {
                if (sample.distSqr(position) <= 16.0 && level.hasChunkAt(sample)
                        && level.getFluidState(sample).is(FluidTags.WATER)) return true;
            }
            return false;
        }
    }

    record Offspring(int credits, int maximumSlimeSize, Map<ResourceLocation, Integer> children) {
        static final Offspring NONE = new Offspring(0, 0, Map.of());
        Offspring {
            children = Map.copyOf(children);
            if (credits < 0 || credits > 256 || children.values().stream().anyMatch(n -> n < 1 || n > credits))
                throw new IllegalArgumentException("Invalid offspring envelope");
            if ((credits == 0) != children.isEmpty()) throw new IllegalArgumentException("Missing offspring envelope/types");
            if (maximumSlimeSize != 0 && (maximumSlimeSize > 16 || credits < GeologyWeights.slimeEnvelope(maximumSlimeSize)))
                throw new IllegalArgumentException("Underfunded slime envelope");
        }
        static Offspring parse(JsonObject root) {
            if (root == null) return NONE;
            fields(root, java.util.Set.of("credits", "maximum_slime_size", "children"));
            Map<ResourceLocation, Integer> children = new LinkedHashMap<>();
            root.getAsJsonObject("children").entrySet().forEach(e -> children.put(new ResourceLocation(e.getKey()), e.getValue().getAsInt()));
            return new Offspring(root.get("credits").getAsInt(),
                    root.has("maximum_slime_size") ? root.get("maximum_slime_size").getAsInt() : 0, children);
        }
    }

    private static boolean booleanGamerule(ServerLevel level, String name) {
        boolean[] found = {false};
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                if (key.getId().equals(name)) found[0] = level.getGameRules().getBoolean(key);
            }
        });
        return found[0]; // Unknown authored gamerules fail closed.
    }
}
