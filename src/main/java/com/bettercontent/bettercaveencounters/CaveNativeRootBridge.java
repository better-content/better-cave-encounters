package com.bettercontent.bettercaveencounters;

import com.bettercontent.bettercompatfixes.api.event.MobOffspringAdmissionEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import java.util.function.Supplier;

/** INTERNAL cross-module Mixin ABI only. No public account, token, authority or permit constructor. */
public final class CaveNativeRootBridge {
    private static final RootScope<Frame> ACTIVE = new RootScope<>();
    private CaveNativeRootBridge() {}
    private static final class Frame {
        final DirectorLineages.RootAdmission admission;
        boolean nativeCall;
        Frame(DirectorLineages.RootAdmission admission) { this.admission = admission; }
    }

    static <T> T finalizeRoot(DirectorLineages.RootAdmission admission, Supplier<T> action) {
        return ACTIVE.call(new Frame(admission), action);
    }

    /** Invoked only by the private handler at the exact four native procedure callsites. */
    public static Entity nativeSpawn(EntityType<?> type, ServerLevel level, BlockPos position,
                                     MobSpawnType reason, Entity originalRoot, Operation<Entity> original) {
        Frame frame = ACTIVE.current();
        boolean owned = frame != null && frame.admission.allowsNativeCall(type, level, position, reason, originalRoot);
        if (owned) { frame.admission.beginNative(type); frame.nativeCall = true; }
        try {
            Entity result = original.call(type, level, position, reason);
            if (owned) frame.admission.nativeReturned(result);
            return result; // Preserve native nonnull/failed-insert semantics and subsequent native yaw RNG.
        } finally { if (owned) frame.nativeCall = false; }
    }

    static boolean admit(MobOffspringAdmissionEvent event) {
        Frame frame = ACTIVE.current();
        return frame != null && frame.nativeCall && frame.admission.admitReplacement(event);
    }

    static boolean provisional(Mob mob) {
        Frame frame = ACTIVE.current();
        return frame != null && frame.admission.provisional(mob);
    }
}
