package com.bettercontent.bettercaveencounters.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** Optional native target, with the approved original procedure ABI version bound explicitly. */
public final class CaveMixinPlugin implements IMixinConfigPlugin {
    static boolean selected(String version) {
        if (version == null) return false;
        if (!version.equals("1.98.1")) throw new IllegalStateException("Revalidate Deep Void initial-root procedure ABI before using this version");
        return true;
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!target.equals("net.mcreator.thedeepvoid.procedures.DamnedOnInitialEntitySpawnProcedure")
                || !mixin.equals("com.bettercontent.bettercaveencounters.mixin.DamnedInitialRootMixin"))
            throw new IllegalStateException("Unexpected Cave native initial-root target");
        var mods=FMLLoader.getLoadingModList();
        if (mods == null) throw new IllegalStateException("Loading mod inventory unavailable");
        var file=mods.getModFileById("the_deep_void");
        if (file == null) return selected(null);
        String version=file.getMods().stream().filter(mod->mod.getModId().equals("the_deep_void"))
                .map(mod->mod.getVersion().toString()).findFirst().orElseThrow();
        return selected(version);
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
