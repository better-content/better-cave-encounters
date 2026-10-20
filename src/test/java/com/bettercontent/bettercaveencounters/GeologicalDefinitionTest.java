package com.bettercontent.bettercaveencounters;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GeologicalDefinitionTest {
    private static final ResourceLocation ID = new ResourceLocation("fixture", "ivory");
    private static JsonObject valid() {
        return JsonParser.parseString("""
                {"schema_version":1,"raw_rock_tag":"fixture:raw_ivory","ordinary_admission":0.25,
                 "ordinary":[{"entity":"fixture:spider","role":"line","minimum_depth":0.35,"weight":1,"cost":4}],
                 "director":[{"entity":"fixture:spider","role":"line","minimum_depth":0.35,"weight":2,"cost":4}]}
                """).getAsJsonObject();
    }
    @Test void ordinaryAndDirectorWeightsMayDifferButCanonicalDepthAndCostMayNot() {
        GeologicalDefinition definition = GeologicalDefinition.parse(ID, valid());
        assertEquals(.25, definition.ordinaryAdmission());
        assertTrue(definition.ordinary().get(0).sameLimits(definition.director().get(0)));
        JsonObject changed = valid();
        changed.getAsJsonArray("director").get(0).getAsJsonObject().addProperty("minimum_depth", .1);
        GeologicalDefinition divergent = GeologicalDefinition.parse(ID, changed);
        assertFalse(divergent.ordinary().get(0).sameLimits(divergent.director().get(0)));
    }
    @Test void badSchemaConditionsProbabilityAndOrdinaryHeaviesAreRejected() {
        JsonObject schema = valid(); schema.addProperty("schema_version", 2);
        assertThrows(IllegalArgumentException.class, () -> GeologicalDefinition.parse(ID, schema));
        JsonObject probability = valid(); probability.addProperty("ordinary_admission", 1.1);
        assertThrows(IllegalArgumentException.class, () -> GeologicalDefinition.parse(ID, probability));
        JsonObject heavy = valid(); heavy.getAsJsonArray("ordinary").get(0).getAsJsonObject().addProperty("role", "heavy");
        assertThrows(IllegalArgumentException.class, () -> GeologicalDefinition.parse(ID, heavy));
        JsonObject condition = valid(); condition.getAsJsonArray("ordinary").get(0).getAsJsonObject().addProperty("condition", "rock_color_is_cold");
        assertThrows(IllegalArgumentException.class, () -> GeologicalDefinition.parse(ID, condition));
        JsonObject typo = valid(); typo.addProperty("historical_geology", true);
        assertThrows(IllegalArgumentException.class, () -> GeologicalDefinition.parse(ID, typo));
    }
    @Test void finiteSlimeAndCorpsePoliciesRejectUnfundedOrUnboundedDefinitions() {
        ResourceLocation slime = new ResourceLocation("fixture", "slime");
        assertEquals(20, new GeologicalDefinition.Offspring(20, 4, Map.of(slime, 20)).credits());
        assertThrows(IllegalArgumentException.class, () -> new GeologicalDefinition.Offspring(4, 4, Map.of(slime, 4)));
        assertThrows(IllegalArgumentException.class, () -> new GeologicalDefinition.Offspring(0, 0, Map.of(slime, 1)));
        assertThrows(IllegalArgumentException.class, () -> new GeologicalDefinition.Offspring(257, 0, Map.of(slime, 257)));
    }
}
