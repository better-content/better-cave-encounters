package com.bettercontent.bettercaveencounters.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Optional native target; check the actual procedure ABI before the required Mixin is applied. */
public final class CaveMixinPlugin implements IMixinConfigPlugin {
    private static final String TARGET = "net.mcreator.thedeepvoid.procedures.DamnedOnInitialEntitySpawnProcedure";
    private static final String MIXIN = "com.bettercontent.bettercaveencounters.mixin.DamnedInitialRootMixin";
    private static final String METHOD = "(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V";
    private static final String SPAWN = "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;)Lnet/minecraft/world/entity/Entity;";
    private static final List<String> VARIANTS = List.of("CROSS_EYES", "BIG_EYE", "MULTIPLE_EYES", "FOUR_EYES");

    static boolean selected(String version) {
        if (version == null) return false; // Optional mod absent.
        if (version.isBlank()) throw new IllegalStateException("Deep Void has no valid mod identity");
        return true; // Presence only. Actual ABI is inspected in preApply, not inferred from a version label.
    }

    private static void requireTarget(String target, String mixin) {
        if (!TARGET.equals(target) || !MIXIN.equals(mixin))
            throw new IllegalStateException("Unexpected Cave native initial-root target");
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        requireTarget(target, mixin);
        var mods = FMLLoader.getLoadingModList();
        if (mods == null) throw new IllegalStateException("Loading mod inventory unavailable");
        var file = mods.getModFileById("the_deep_void");
        if (file == null) return selected(null);
        String version = file.getMods().stream().filter(mod -> mod.getModId().equals("the_deep_void"))
                .map(mod -> mod.getVersion().toString()).findFirst().orElseThrow();
        return selected(version);
    }

    /** Reject changed control flow as well as changed signatures, without initializing vendor classes. */
    static void validateProcedure(ClassNode node) {
        if (!TARGET.replace('.', '/').equals(node.name)) throw new IllegalStateException("Wrong Deep Void procedure class");
        List<MethodNode> matches = node.methods.stream().filter(method -> method.name.equals("execute") && method.desc.equals(METHOD)
                && (method.access & Opcodes.ACC_STATIC) != 0).toList();
        if (matches.size() != 1) throw new IllegalStateException("Deep Void initial-root execute signature changed");
        MethodNode method = matches.get(0);
        List<String> variants = new ArrayList<>();
        List<Integer> random = new ArrayList<>(), spawn = new ArrayList<>(), discard = new ArrayList<>();
        List<LabelNode> exits = new ArrayList<>();
        boolean guarded = false;
        int ordinal = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                    && type.desc.equals("net/mcreator/thedeepvoid/entity/DamnedEntity")) guarded = true;
            if (instruction instanceof FieldInsnNode field && field.owner.equals("net/mcreator/thedeepvoid/init/TheDeepVoidModEntities")
                    && VARIANTS.contains(field.name)) {
                if (field.getOpcode() != Opcodes.GETSTATIC || !field.desc.equals("Lnet/minecraftforge/registries/RegistryObject;"))
                    throw new IllegalStateException("Deep Void variant registry field changed");
                variants.add(field.name);
            }
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("java/lang/Math") && call.name.equals("random")) {
                    if (call.getOpcode() != Opcodes.INVOKESTATIC || !call.desc.equals("()D"))
                        throw new IllegalStateException("Deep Void native random call changed");
                    random.add(ordinal);
                }
                if (call.owner.equals("net/minecraft/world/entity/EntityType") &&
                        (call.name.equals("m_262496_") || call.name.equals("spawn"))) {
                    if (call.getOpcode() != Opcodes.INVOKEVIRTUAL || !call.desc.equals(SPAWN))
                        throw new IllegalStateException("Deep Void native spawn signature changed");
                    spawn.add(ordinal);
                }
                if (call.owner.equals("net/minecraft/world/entity/Entity") &&
                        (call.name.equals("m_146870_") || call.name.equals("discard"))) {
                    if (call.getOpcode() != Opcodes.INVOKEVIRTUAL || !call.desc.equals("()V"))
                        throw new IllegalStateException("Deep Void original discard signature changed");
                    discard.add(ordinal);
                    AbstractInsnNode next = instruction.getNext();
                    while (next != null && next.getOpcode() < 0) next = next.getNext();
                    if (!(next instanceof JumpInsnNode jump) || next.getOpcode() != Opcodes.GOTO)
                        throw new IllegalStateException("Deep Void conversion can fall through");
                    exits.add(jump.label);
                }
            }
            ordinal++;
        }
        if (!guarded || !variants.equals(VARIANTS) || random.size() != 4 || spawn.size() != 4 || discard.size() != 4)
            throw new IllegalStateException("Deep Void four-branch initial-root procedure ABI changed");
        for (int index = 0; index < 4; index++) {
            if (!(random.get(index) < spawn.get(index) && spawn.get(index) < discard.get(index)) || exits.get(index) != exits.get(0)
                    || (index < 3 && discard.get(index) >= random.get(index + 1)))
                throw new IllegalStateException("Deep Void native branch order or exclusive exit changed");
        }
    }

    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        requireTarget(target, mixin);
        validateProcedure(node);
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
