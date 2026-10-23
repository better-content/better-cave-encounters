package com.bettercontent.bettercaveencounters.worldtest.mixin;

import com.bettercontent.bettercaveencounters.worldtest.CaveFixtureGlm;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.loot.LootModifierManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

/** Required only by the two nonshipping owning profile RunConfigs; no second decode/apply. */
@Mixin(value = LootModifierManager.class, remap = false)
abstract class FixtureGlmLoadMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("RETURN"), require = 1, remap = false)
    private void cave$proveOriginalReturn(Map<ResourceLocation, JsonElement> definitions,
            ResourceManager resources, ProfilerFiller profiler, CallbackInfo callback) {
        CaveFixtureGlm.atNativeApplyReturn((LootModifierManager) (Object) this, resources);
    }
}
