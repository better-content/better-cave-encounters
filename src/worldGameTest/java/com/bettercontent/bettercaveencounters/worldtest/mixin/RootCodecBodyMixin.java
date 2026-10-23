package com.bettercontent.bettercaveencounters.worldtest.mixin;
import com.bettercontent.bettercaveencounters.worldtest.OriginalCodecDispatchProof;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifierManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
/** Native original extraction once, outside the nonthrowing metadata observer. */
@Mixin(value = LootModifierManager.class, remap = false)
abstract class RootCodecBodyMixin {
 @Redirect(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
  at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/DataResult;resultOrPartial(Ljava/util/function/Consumer;)Ljava/util/Optional;"), require = 1, allow = 1, remap = false)
 private <A> Optional<A> cave$body(DataResult<A> original, Consumer<String> onError, Map<ResourceLocation, JsonElement> definitions, ResourceManager resources, ProfilerFiller profiler) {
  try {
   String row = OriginalCodecDispatchProof.row();
   if (row != null) OriginalCodecDispatchProof.body(IGlobalLootModifier.DIRECT_CODEC, JsonOps.INSTANCE, definitions.get(new ResourceLocation(row)), original);
  } catch (Throwable error) { OriginalCodecDispatchProof.reject(); }
  return original.resultOrPartial(onError);
 }
}
