package com.bettercontent.bettercaveencounters.worldtest.mixin;
import com.bettercontent.bettercaveencounters.worldtest.OriginalCodecDispatchProof;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Observe both original public decode returns; no access to the private implementation type. */
@Mixin(targets = "net.minecraftforge.registries.ForgeRegistry$RegistryCodec", remap = false)
abstract class RegistryCodecDecodeMixin {
 @Inject(method = "decode(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", at = @At("RETURN"), require = 2, allow = 2, remap = false)
 private void cave$registered(DynamicOps<?> ops, Object input, CallbackInfoReturnable<DataResult<?>> callback) {
  try { OriginalCodecDispatchProof.registered(this, ops, input, callback.getReturnValue()); }
  catch (Throwable error) { OriginalCodecDispatchProof.reject(); }
 }
}
