package com.mk2525.vsfluidlink.mixin;

import com.mk2525.vsfluidlink.compat.tfmg.TfmgSpoolEndpoints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.drmangotea.tfmg.content.machinery.misc.winding_machine.SpoolItem", remap = false)
public abstract class TfmgSpoolItemMixin {
    @Redirect(method = "useOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/context/UseOnContext;getClickedPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos vsfluidlink$clickedEndpoint(UseOnContext context) {
        return TfmgSpoolEndpoints.resolve(context.getLevel(), context.getClickedPos());
    }

    @Redirect(method = "useOn", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object vsfluidlink$selectedEndpoint(ItemStack stack, DataComponentType<BlockPos> component,
                                              Object fallback, UseOnContext context) {
        BlockPos selected = stack.get(component);
        return selected == null ? fallback : TfmgSpoolEndpoints.resolve(context.getLevel(), selected);
    }

    // Both endpoints are already resolved; suppress CE's asymmetric, unchecked walks.
    @Redirect(method = "useOn", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity vsfluidlink$skipClickedWalk(Level level, BlockPos pos) {
        return null;
    }

    @Redirect(method = "useOn", at = @At(value = "INVOKE", ordinal = 3,
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity vsfluidlink$skipSelectedWalk(Level level, BlockPos pos) {
        return null;
    }
}
