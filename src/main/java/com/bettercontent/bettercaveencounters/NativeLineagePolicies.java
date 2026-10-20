package com.bettercontent.bettercaveencounters;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Frozen optional-native defaults also protect old/unmapped fallback profiles. No monster class linkage. */
final class NativeLineagePolicies {
    private static final Map<ResourceLocation, GeologicalDefinition.Offspring> POLICIES = read();
    private NativeLineagePolicies() {}
    static GeologicalDefinition.Offspring get(ResourceLocation entity) {
        return POLICIES.getOrDefault(entity, GeologicalDefinition.Offspring.NONE);
    }
    private static Map<ResourceLocation, GeologicalDefinition.Offspring> read() {
        try (var stream = NativeLineagePolicies.class.getResourceAsStream("/data/better_cave_encounters/director_lineage_policies/native.json")) {
            if (stream == null) throw new IllegalStateException("Missing shipped finite native lineage policies");
            var root = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema_version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported native lineage policy schema");
            Map<ResourceLocation, GeologicalDefinition.Offspring> policies = new LinkedHashMap<>();
            root.getAsJsonObject("actors").entrySet().forEach(e -> policies.put(new ResourceLocation(e.getKey()),
                    GeologicalDefinition.Offspring.parse(e.getValue().getAsJsonObject())));
            return Map.copyOf(policies);
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
}
