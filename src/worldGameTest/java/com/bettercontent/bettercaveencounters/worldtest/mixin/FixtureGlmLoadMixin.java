package com.bettercontent.bettercaveencounters.worldtest.mixin;

import com.bettercontent.bettercaveencounters.worldtest.CaveFixtureGlm;
import com.google.gson.JsonElement;
import com.google.common.collect.ImmutableMap;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import java.util.Iterator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.loot.LootModifierManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import java.util.Map;

/** Required only by the existing nonshipping owning gameTestServer RunConfig; no second decode/apply. */
@Mixin(value = LootModifierManager.class, remap = false)
abstract class FixtureGlmLoadMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("HEAD"), require = 1, allow = 1, remap = false)
    private void cave$beginOriginalCodecProof(Map<ResourceLocation, JsonElement> definitions,
            ResourceManager resources, ProfilerFiller profiler, CallbackInfo callback) {
        CaveFixtureGlm.beginOriginalCodecProof();
    }
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("RETURN"), require = 1, allow = 1, remap = false)
    private void cave$observeActualNativeReturn(Map<ResourceLocation, JsonElement> definitions,
            ResourceManager resources, ProfilerFiller profiler, CallbackInfo callback) {
        CaveFixtureGlm.atNativeApplyReturn((LootModifierManager) (Object) this, definitions, resources);
    }
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", shift = At.Shift.BEFORE),
        locals = LocalCapture.CAPTURE_FAILHARD, require = 1, allow = 1, remap = false)
    private void cave$beforeOriginalParse(Map<ResourceLocation, JsonElement> definitions,
            ResourceManager resources, ProfilerFiller profiler, CallbackInfo callback,
            ImmutableMap.Builder<ResourceLocation, IGlobalLootModifier> builder, List<ResourceLocation> ordered,
            ResourceLocation index, Iterator<ResourceLocation> iterator, ResourceLocation location, JsonElement json) {
        try { com.bettercontent.bettercaveencounters.worldtest.OriginalCodecDispatchProof.before(location, json); }
        catch (Throwable error) { com.bettercontent.bettercaveencounters.worldtest.OriginalCodecDispatchProof.reject(); }
    }
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/DataResult;resultOrPartial(Ljava/util/function/Consumer;)Ljava/util/Optional;", shift = At.Shift.AFTER),
        locals = LocalCapture.CAPTURE_FAILHARD, require = 1, allow = 1, remap = false)
    private void cave$afterOriginalParse(Map<ResourceLocation, JsonElement> definitions,
            ResourceManager resources, ProfilerFiller profiler, CallbackInfo callback,
            ImmutableMap.Builder<ResourceLocation, IGlobalLootModifier> builder, List<ResourceLocation> ordered,
            ResourceLocation index, Iterator<ResourceLocation> iterator, ResourceLocation location) {
        try { com.bettercontent.bettercaveencounters.worldtest.OriginalCodecDispatchProof.after(location); }
        catch (Throwable error) { com.bettercontent.bettercaveencounters.worldtest.OriginalCodecDispatchProof.reject(); }
    }
}
