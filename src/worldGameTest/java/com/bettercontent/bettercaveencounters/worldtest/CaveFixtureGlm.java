package com.bettercontent.bettercaveencounters.worldtest;

import com.bettercontent.bettercaveencounters.DepthDirectorMod;
import com.google.gson.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.common.loot.LootModifierManager;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Nonshipping exact own-profile policy and public native load proof; no gameplay-effect verdict. */
@Mod.EventBusSubscriber(modid = "better_cave_encounters", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CaveFixtureGlm {
    private static final String PROPERTY = "bc.cave.fixtureGlm.manifest";
    private static final ResourceLocation INDEX = new ResourceLocation("forge", "loot_modifiers/global_loot_modifiers.json");
    private static final int MAX_BYTES = 256 * 1024;
    // Only bounded scalars cross the original apply-return boundary. Never retain its receiver,
    // ResourceManager, registry codecs, worlds or entities, and never decode/apply modifiers twice.
    private static int applyReturns;
    private static boolean failed;
    private static String lastScalarProof;
    private CaveFixtureGlm() {}
    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException(reason);
    }
    private static byte[] read(InputStream input) throws Exception {
        try (input) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            require(bytes.length <= MAX_BYTES, "Oversize Cave fixture proof input");
            return bytes;
        }
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static JsonObject json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static Path manifestPath() {
        String value = System.getProperty(PROPERTY);
        require(value != null && !value.isBlank(), "Owning GLM preparation missing");
        return Path.of(value).toAbsolutePath().normalize();
    }
    private static JsonObject manifest() throws Exception {
        Path path = manifestPath();
        byte[] bytes = read(Files.newInputStream(path));
        require(sha(bytes).equals(System.getProperty(PROPERTY + ".sha256")), "Fixture manifest drift");
        JsonObject data = json(bytes);
        require(data.get("schema").getAsString().equals("bc.cave.fixture_glm_manifest.v1"), "Fixture manifest schema");
        String profile = Boolean.getBoolean("bc.geologyQualification") ? "geology" : "legacy";
        require(data.get("profile").getAsString().equals(profile), "Wrong fixture GLM profile");
        require(data.get("attempt").getAsString().equals(System.getProperty(PROPERTY + ".attempt"))
            && path.getParent().getParent().getFileName().toString().equals(data.get("attempt").getAsString()), "Fixture attempt drift");
        byte[] scopeBytes = read(CaveFixtureGlm.class.getResourceAsStream("/bc_cave_fixture_glm_scope.json"));
        require(sha(scopeBytes).equals(data.get("scope_sha256").getAsString()), "Owning scope drift");
        JsonObject scope = json(scopeBytes);
        JsonArray expected = scope.getAsJsonObject("profiles").getAsJsonObject(profile).getAsJsonArray("ordered_ids");
        require(expected.equals(data.getAsJsonObject("projection").getAsJsonArray("ordered_ids"))
            && expected.size() == (profile.equals("legacy") ? 29 : 38), "Fixed own-domain membership/order drift");
        return data;
    }
    @SubscribeEvent
    public static void find(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        try {
            JsonObject data = manifest();
            String id = data.get("pack_id").getAsString();
            Path pack = manifestPath().getParent().resolve("pack");
            event.addRepositorySource(consumer -> consumer.accept(Pack.create(id, Component.literal(id), true,
                name -> new PathPackResources(name, pack, false),
                new Pack.Info(Component.literal(id), 15, FeatureFlagSet.of()),
                PackType.SERVER_DATA, Pack.Position.TOP, true, PackSource.BUILT_IN)));
        } catch (Exception error) { throw new IllegalStateException("Cave fixture GLM pack admission failed", error); }
    }
    private static JsonObject verifyResources(ResourceManager resources, JsonObject data) throws Exception {
        String pack = data.get("pack_id").getAsString();
        var top = resources.getResource(INDEX).orElseThrow();
        byte[] bytes = read(top.open());
        require(top.sourcePackId().equals(pack) && sha(bytes).equals(data.get("index_sha256").getAsString()), "Actual effective index origin/bytes differ");
        JsonObject index = json(bytes);
        JsonArray expected = data.getAsJsonObject("projection").getAsJsonArray("ordered_ids");
        require(index.get("replace").getAsBoolean() && index.getAsJsonArray("entries").equals(expected), "Actual effective index order differs");
        // Reconstruct the actual pinned native stack algorithm, not just highest-resource lookup.
        List<String> effective = new ArrayList<>();
        var stack = resources.getResourceStack(INDEX);
        require(!stack.isEmpty() && stack.size() <= 64, "Unexpected index stack size");
        for (var resource : stack) {
            JsonObject part = json(read(resource.open()));
            if (part.get("replace").getAsBoolean()) effective.clear();
            for (var entry : part.getAsJsonArray("entries")) {
                String id = entry.getAsString();
                effective.remove(id);
                effective.add(id);
            }
        }
        require(new Gson().toJsonTree(effective).equals(expected), "Actual index-stack membership/order differs");
        JsonArray rows = new JsonArray();
        JsonArray declaredRows = data.getAsJsonObject("projection").getAsJsonArray("rows");
        require(declaredRows.size() == expected.size(), "Included resource coverage differs");
        for (int ordinal = 0; ordinal < declaredRows.size(); ordinal++) {
            var row = declaredRows.get(ordinal).getAsJsonObject();
            require(row.get("id").equals(expected.get(ordinal)), "Included resource identity/order differs");
            ResourceLocation id = new ResourceLocation(row.get("id").getAsString());
            String path = row.get("resource").getAsString();
            require(path.equals("data/" + id.getNamespace() + "/loot_modifiers/" + id.getPath() + ".json"), "Included resource path differs");
            var resource = resources.getResource(new ResourceLocation(id.getNamespace(), "loot_modifiers/" + id.getPath() + ".json")).orElseThrow();
            byte[] definition = read(resource.open());
            require(sha(definition).equals(row.get("sha256").getAsString())
                && json(definition).get("type").equals(row.get("codec")), "Effective included definition/type absent/different");
            var proof = new JsonObject();
            proof.add("id", row.get("id"));
            proof.addProperty("sourcePackId", resource.sourcePackId());
            proof.add("sha256", row.get("sha256"));
            rows.add(proof);
        }
        JsonObject proof = new JsonObject();
        proof.addProperty("profile", data.get("profile").getAsString());
        proof.addProperty("indexSourcePackId", top.sourcePackId());
        proof.add("index_sha256", data.get("index_sha256"));
        proof.add("orderedIds", expected.deepCopy());
        proof.add("effectiveDefinitions", rows);
        return proof;
    }
    /** INTERNAL fixture dispatcher: actual native receiver at original apply RETURN, read-only APIs. */
    public static synchronized void atNativeApplyReturn(LootModifierManager manager, ResourceManager resources) {
        try {
            require(!failed && ++applyReturns <= 4, "Fixture reload proof overflow/previous failure");
            JsonObject data = manifest();
            JsonObject proof = verifyResources(resources, data);
            byte[] scopeBytes = read(CaveFixtureGlm.class.getResourceAsStream("/bc_cave_fixture_glm_scope.json"));
            String loaderHash = json(scopeBytes).getAsJsonObject("original_class_witnesses")
                .getAsJsonObject(LootModifierManager.class.getName()).get("sha256").getAsString();
            require(manager.getClass() == LootModifierManager.class, "Unexpected native loader implementation");
            require(sha(read(LootModifierManager.class.getResourceAsStream("/net/minecraftforge/common/loot/LootModifierManager.class"))).equals(loaderHash), "Pinned loader traversal/ordering bytecode differs");
            // Pinned apply traverses effective IDs -> ImmutableMap.Builder -> values(), in order.
            List<?> loaded = new ArrayList<>(manager.getAllLootMods());
            JsonArray rows = data.getAsJsonObject("projection").getAsJsonArray("rows");
            require(loaded.size() == rows.size(), "Actual included decoded count incomplete/extra");
            var registry = ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS.get();
            require(registry != null, "Actual native codec registry missing");
            for (int i = 0; i < rows.size(); i++) {
                ResourceLocation key = new ResourceLocation(rows.get(i).getAsJsonObject().get("codec").getAsString());
                var codec = ((net.minecraftforge.common.loot.IGlobalLootModifier) loaded.get(i)).codec();
                require(codec != null && registry.getValue(key) == codec && key.equals(registry.getKey(codec)), "Actual native codec identity/order differs");
            }
            proof.addProperty("actualDecodedCount", loaded.size());
            proof.addProperty("actualCodecIdentitiesAndOrder", true);
            proof.addProperty("gameplayEffectsAccepted", false);
            String scalar = new Gson().toJson(proof);
            require(scalar.length() <= MAX_BYTES, "Oversize scalar fixture proof");
            lastScalarProof = scalar;
        } catch (Exception error) {
            failed = true;
            throw new IllegalStateException("Required Cave native GLM load proof failed", error);
        }
    }
    @Mod.EventBusSubscriber(modid = "better_cave_encounters", bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Verify {
        private Verify() {}
        @SubscribeEvent
        public static void starting(ServerStartingEvent event) {
            try {
                require(CaveFixtureGlm.class.getModule().isNamed()
                    && CaveFixtureGlm.class.getModule() == DepthDirectorMod.class.getModule(), "Fixture left owning named module");
                JsonObject data = manifest();
                var server = event.getServer();
                String id = data.get("pack_id").getAsString();
                var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
                require(!selected.isEmpty() && selected.get(selected.size() - 1).equals(id)
                    && Collections.frequency(selected, id) == 1, "Required fixture not selected at actual TOP");
                Pack pack = server.getPackRepository().getPack(id);
                require(pack != null && pack.isRequired() && pack.isFixedPosition() && pack.getDefaultPosition() == Pack.Position.TOP, "Fixture pack attributes differ");
                JsonObject current = verifyResources(server.getResourceManager(), data);
                require(!failed && applyReturns > 0 && applyReturns <= 4 && lastScalarProof != null, "Original apply-return proof missing/failed");
                JsonObject proof = JsonParser.parseString(lastScalarProof).getAsJsonObject();
                require(current.get("index_sha256").equals(proof.get("index_sha256"))
                    && current.get("effectiveDefinitions").equals(proof.get("effectiveDefinitions")), "Startup resources differ from actual apply return");
                proof.addProperty("schema", "bc.cave.fixture_glm_load_proof.v1");
                proof.add("attempt", data.get("attempt"));
                proof.addProperty("originalApplyReturnsProved", applyReturns);
                proof.addProperty("requiredFixedTopSelected", true);
                Path output = Path.of(System.getProperty(PROPERTY + ".proof"));
                Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(proof) + "\n", StandardOpenOption.CREATE_NEW);
            } catch (Exception error) { throw new IllegalStateException("Cave owning profile GLM startup proof failed", error); }
        }
    }
}
