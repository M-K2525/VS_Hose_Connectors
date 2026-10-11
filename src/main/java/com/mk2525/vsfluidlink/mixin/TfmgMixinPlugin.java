package com.mk2525.vsfluidlink.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Do not load optional TFMG classes, or patch unverified spool implementations. */
public final class TfmgMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var mods = LoadingModList.get();
        return mods != null && mods.getMods().stream().anyMatch(mod ->
                mod.getModId().equals("tfmg") && mod.getVersion().toString().equals("1.3.2a-community"));
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
