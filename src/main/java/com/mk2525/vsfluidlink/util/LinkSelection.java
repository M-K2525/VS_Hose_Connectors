package com.mk2525.vsfluidlink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LinkSelection {
    private static final Map<Key, BlockPos> SELECTED_POSITIONS = new ConcurrentHashMap<>();

    private LinkSelection() {
    }

    public static BlockPos get(Player player, String channel) {
        return SELECTED_POSITIONS.get(new Key(player.getUUID(), channel));
    }

    public static void set(Player player, String channel, BlockPos pos) {
        SELECTED_POSITIONS.put(new Key(player.getUUID(), channel), pos.immutable());
    }

    public static void clear(Player player, String channel) {
        SELECTED_POSITIONS.remove(new Key(player.getUUID(), channel));
    }

    private record Key(UUID playerId, String channel) {
    }
}
