package com.bettercontent.bettercaveencounters.mixin;

import com.bettercontent.bettercaveencounters.CaveNativeRootBridge;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.mcreator.thedeepvoid.procedures.DamnedOnInitialEntitySpawnProcedure", remap = false)
public abstract class DamnedInitialRootMixin {
    @WrapOperation(
            method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;)Lnet/minecraft/world/entity/Entity;", remap = true),
            require = 4, expect = 4, allow = 4)
    private static Entity betterCave$initialRoot(EntityType<?> type, ServerLevel level, BlockPos position,
            MobSpawnType reason, Operation<Entity> original, LevelAccessor world, double x, double y, double z, Entity parent) {
        return CaveNativeRootBridge.nativeSpawn(type, level, position, reason, parent, original);
    }
}
