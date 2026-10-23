package com.bettercontent.bettercaveencounters.worldtest;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Observe original public calls only. Never invoke a loaded modifier's possibly nonmemoized codec(). */
public final class OriginalCodecDispatchProof {
    private static final CodecDispatchLedger LEDGER = new CodecDispatchLedger();
    private OriginalCodecDispatchProof() {}
    private static volatile boolean broken;
    public static void reject() { broken = true; }
    public static boolean active() { return LEDGER.active(); }
    public static String row() { return LEDGER.row(); }
    private static String digest(Object input) throws Exception {
        if (!(input instanceof JsonElement)) throw new IllegalArgumentException("Non JSON root");
        byte[] bytes = input.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 256 * 1024) throw new IllegalArgumentException("Root input overflow");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    public static void begin(List<String> ids) { try { LEDGER.begin(ids); } catch (Throwable error) { reject(); } }
    public static void before(ResourceLocation row, JsonElement input) {
        try { LEDGER.before(row.toString(), input.getAsJsonObject().get("type").getAsString(), digest(input)); }
        catch (Throwable error) { reject(); }
    }
    public static void registered(Object receiver, DynamicOps<?> ops, Object input, DataResult<?> result) {
        if (!active()) return; // before registry/bootstrap resolution
        try {
            var registry = ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS.get();
            if (receiver != registry.getCodec()) return; // unrelated registries are not witnesses
            var key = new ResourceLocation(LEDGER.codec());
            Object decoded = result == null ? null : result.result().orElse(null);
            Object selected = decoded instanceof Pair<?, ?> pair ? pair.getFirst() : null;
            var expected = registry.getValue(key);
            boolean identity = selected != null && selected == expected && key.equals(registry.getKey(expected))
                && ops == JsonOps.INSTANCE && input instanceof JsonPrimitive p && p.isString()
                && key.toString().equals(p.getAsString());
            LEDGER.selected(identity, result != null && result.error().isEmpty() && decoded != null,
                identity ? key.toString() : null);
        } catch (Throwable error) { reject(); }
    }
    public static void body(Object receiver, DynamicOps<?> ops, Object input, DataResult<?> result) {
        if (!active()) return;
        try {
            if (receiver != IGlobalLootModifier.DIRECT_CODEC) return;
            Object body = result == null ? null : result.result().orElse(null);
            LEDGER.returned(ops == JsonOps.INSTANCE,
                result != null && result.error().isEmpty() && body instanceof IGlobalLootModifier,
                digest(input), body == null ? null : body.getClass().getName());
        } catch (Throwable error) { reject(); }
    }
    public static void after(ResourceLocation row) { try { LEDGER.after(row.toString()); } catch (Throwable error) { reject(); } }
    public static void requireLoaded(int ordinal, ResourceLocation id, ResourceLocation codec,
            JsonElement canonicalInput, IGlobalLootModifier loaded) throws Exception {
        if (broken) throw new IllegalStateException("Original codec observer failed");
        LEDGER.requireLoaded(ordinal, id.toString(), codec.toString(), digest(canonicalInput), loaded.getClass().getName());
    }
    public static void end() { LEDGER.end(); }
}
