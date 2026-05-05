package com.mk2525.vsfluidlink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public class LinkSelection {
    private static final String ROOT_KEY = "vsfluidlink_link_selection";

    public static BlockPos get(Player player, String channel) {
        CompoundTag root = player.getPersistentData().getCompound(ROOT_KEY);
        if (!root.contains(channel)) {
            return null;
        }

        CompoundTag tag = root.getCompound(channel);
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    public static void set(Player player, String channel, BlockPos pos) {
        CompoundTag root = player.getPersistentData().getCompound(ROOT_KEY);
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        root.put(channel, tag);
        player.getPersistentData().put(ROOT_KEY, root);
    }

    public static void clear(Player player, String channel) {
        CompoundTag root = player.getPersistentData().getCompound(ROOT_KEY);
        root.remove(channel);
        player.getPersistentData().put(ROOT_KEY, root);
    }
}
