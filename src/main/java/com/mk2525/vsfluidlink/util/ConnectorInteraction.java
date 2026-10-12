package com.mk2525.vsfluidlink.util;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.world.item.ItemStack;

public final class ConnectorInteraction {
    private ConnectorInteraction() {}

    public static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof WrenchItem
                || stack.getItem().getDescriptionId().contains("wrench"));
    }

    public static boolean canLink(ItemStack stack, boolean sneaking) {
        if (sneaking) return false;
        return isWrench(stack)
                ? VsFluidLinkConfig.wrenchRightClickAction() == VsFluidLinkConfig.WrenchRightClickAction.CONNECT
                : VsFluidLinkConfig.connectWithoutWrench();
    }
}
