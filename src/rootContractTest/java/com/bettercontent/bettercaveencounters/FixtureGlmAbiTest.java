package com.bettercontent.bettercaveencounters;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.ClassReader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Compiled ABI/read-only boundary controls, not a transformed/native load observation. */
class FixtureGlmAbiTest {
    private static final String FIXTURE = "com/bettercontent/bettercaveencounters/worldtest/CaveFixtureGlm";
    private static ClassNode read(String name) throws Exception {
        try (var input = FixtureGlmAbiTest.class.getResourceAsStream('/' + name + ".class")) {
            assertNotNull(input, name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
    private static List<MethodInsnNode> calls(ClassNode node) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) result.add(call);
        }
        return result;
    }
    @Test void privateOriginalReturnHandlerHasOnePublicDescriptorDispatcher() throws Exception {
        ClassNode mixin = read("com/bettercontent/bettercaveencounters/worldtest/mixin/FixtureGlmLoadMixin");
        var handlers = mixin.methods.stream().filter(method -> method.name.equals("cave$proveOriginalReturn")).toList();
        assertEquals(1, handlers.size());
        var handler = handlers.get(0);
        assertTrue((handler.access & Opcodes.ACC_PRIVATE) != 0);
        assertEquals(0, handler.access & Opcodes.ACC_STATIC);
        var inject = handler.visibleAnnotations.stream().filter(annotation ->
            annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < inject.values.size(); i += 2) values.put((String) inject.values.get(i), inject.values.get(i + 1));
        assertEquals(1, values.get("require"));
        assertEquals(Boolean.FALSE, values.get("remap"));
        assertEquals(List.of("apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V"), values.get("method"));
        var at = (AnnotationNode) ((List<?>) values.get("at")).get(0);
        assertEquals("RETURN", at.values.get(at.values.indexOf("value") + 1));
        assertEquals(1, calls(mixin).stream().filter(call -> call.owner.equals(FIXTURE)
            && call.name.equals("atNativeApplyReturn")
            && call.desc.equals("(Lnet/minecraftforge/common/loot/LootModifierManager;Lnet/minecraft/server/packs/resources/ResourceManager;)V")).count());
        ClassNode fixture = read(FIXTURE);
        assertTrue((fixture.access & Opcodes.ACC_PUBLIC) != 0);
        var dispatch = fixture.methods.stream().filter(method -> method.name.equals("atNativeApplyReturn")).findFirst().orElseThrow();
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, dispatch.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
    }
    @Test void fixtureReadsActualPublicCollectionNeverPrivateMapOrSecondDecodeApply() throws Exception {
        var calls = calls(read(FIXTURE));
        assertEquals(1, calls.stream().filter(call -> call.owner.equals("net/minecraftforge/common/loot/LootModifierManager")
            && call.name.equals("getAllLootMods")).count());
        assertFalse(calls.stream().anyMatch(call -> call.owner.startsWith("java/lang/reflect/")
            || call.owner.equals("net/minecraftforge/common/ForgeInternalHandler")
            || call.owner.equals("com/mojang/serialization/Codec")
            || (call.owner.equals("net/minecraftforge/common/loot/IGlobalLootModifier") && call.name.equals("apply"))));
        assertTrue(read(FIXTURE).fields.stream().allMatch(field -> !field.desc.contains("LootModifierManager")
            && !field.desc.contains("ResourceManager") && !field.desc.contains("ServerLevel")
            && !field.desc.contains("Entity") && !field.desc.contains("Codec")));
    }
    @Test void exactNonshippingScopeAndRequiredConfigResourcesMatchContract() throws Exception {
        try (var input = FixtureGlmAbiTest.class.getResourceAsStream("/bc_cave_fixture_glm_scope.json")) {
            assertNotNull(input);
            var scope = com.google.gson.JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(29, scope.getAsJsonObject("profiles").getAsJsonObject("legacy").getAsJsonArray("ordered_ids").size());
            assertEquals(38, scope.getAsJsonObject("profiles").getAsJsonObject("geology").getAsJsonArray("ordered_ids").size());
            assertEquals(9, scope.getAsJsonObject("outer_codecs").size());
            assertEquals(27, scope.getAsJsonObject("typed_registration_owners").size());
        }
        try (var input = FixtureGlmAbiTest.class.getResourceAsStream("/bc_cave_fixture_glm.mixins.json")) {
            assertNotNull(input);
            var config = com.google.gson.JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.get("required").getAsBoolean());
            assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
        }
    }
}
