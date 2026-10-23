package com.bettercontent.bettercaveencounters;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Nonshipping registry-dependent assertions migrated verbatim in meaning from the JVM fixture. */
@GameTestHolder(DepthDirectorMod.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = DepthDirectorMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GeologicalDefinitionGameTests {
    private static final ResourceLocation ID = new ResourceLocation("fixture", "ivory");
    private GeologicalDefinitionGameTests() {}

    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        event.register(GeologicalDefinitionGameTests.class);
    }

    private static JsonObject valid() {
        return JsonParser.parseString("""
                {"schema_version":1,"raw_rock_tag":"fixture:raw_ivory","ordinary_admission":0.25,
                 "ordinary":[{"entity":"fixture:spider","role":"line","minimum_depth":0.35,"weight":1,"cost":4}],
                 "director":[{"entity":"fixture:spider","role":"line","minimum_depth":0.35,"weight":2,"cost":4}]}
                """).getAsJsonObject();
    }

    @GameTest(templateNamespace = DepthDirectorMod.MOD_ID, template = "director_cave", timeoutTicks = 100)
    public static void geologicalWeightsMayDifferButDepthAndCostMayNot(GameTestHelper helper) {
        GeologicalDefinition definition = GeologicalDefinition.parse(ID, valid());
        helper.assertTrue(Double.compare(.25, definition.ordinaryAdmission()) == 0, "ordinary admission must remain exactly .25");
        helper.assertTrue(definition.ordinary().get(0).sameLimits(definition.director().get(0)),
                "ordinary/director weights may differ with identical canonical limits");
        JsonObject changed = valid();
        changed.getAsJsonArray("director").get(0).getAsJsonObject().addProperty("minimum_depth", .1);
        GeologicalDefinition divergent = GeologicalDefinition.parse(ID, changed);
        helper.assertTrue(!divergent.ordinary().get(0).sameLimits(divergent.director().get(0)),
                "changed minimum depth must not have identical canonical limits");
        helper.succeed();
    }

    @GameTest(templateNamespace = DepthDirectorMod.MOD_ID, template = "director_cave", timeoutTicks = 100)
    public static void geologicalProbabilityHeaviesAndConditionsAreRejected(GameTestHelper helper) {
        JsonObject probability = valid(); probability.addProperty("ordinary_admission", 1.1);
        rejectsIllegalArgument(helper, () -> GeologicalDefinition.parse(ID, probability), "probability 1.1");
        JsonObject heavy = valid(); heavy.getAsJsonArray("ordinary").get(0).getAsJsonObject().addProperty("role", "heavy");
        rejectsIllegalArgument(helper, () -> GeologicalDefinition.parse(ID, heavy), "ordinary heavy");
        JsonObject condition = valid(); condition.getAsJsonArray("ordinary").get(0).getAsJsonObject().addProperty("condition", "rock_color_is_cold");
        rejectsIllegalArgument(helper, () -> GeologicalDefinition.parse(ID, condition), "unknown condition");
        helper.succeed();
    }

    // Assertion adapter only: unexpected exceptions/errors propagate; no bootstrap is attempted here.
    private static void rejectsIllegalArgument(GameTestHelper helper, Runnable assertion, String label) {
        boolean rejected = false;
        try { assertion.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "expected IllegalArgumentException for " + label);
    }
}
