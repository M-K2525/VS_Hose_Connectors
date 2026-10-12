package com.mk2525.vsfluidlink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LinkSelection {
    private static final Map<Key, Selection> SELECTED = new ConcurrentHashMap<>();

    private record Key(UUID playerId, String channel) {}
    private record Selection(BlockPos pos, WeakReference<Level> level) {}

    public static BlockPos get(Player player, String channel) {
        Key key = new Key(player.getUUID(), channel);
        Selection selection = SELECTED.get(key);
        if (selection == null) return null;
        if (selection.level().get() != player.level()) {
            SELECTED.remove(key, selection);
            return null;
        }
        return selection.pos();
    }

    public static BlockPos getValid(Player player, String channel, Class<? extends BlockEntity> type) {
        BlockPos pos = get(player, channel);
        if (pos != null && (!player.level().isLoaded(pos) || !type.isInstance(player.level().getBlockEntity(pos)))) {
            clear(player, channel);
            return null;
        }
        return pos;
    }

    public static void set(Player player, String channel, BlockPos pos) {
        SELECTED.put(new Key(player.getUUID(), channel), new Selection(pos.immutable(), new WeakReference<>(player.level())));
    }

    public static void clear(Player player, String channel) {
        SELECTED.remove(new Key(player.getUUID(), channel));
    }
}
