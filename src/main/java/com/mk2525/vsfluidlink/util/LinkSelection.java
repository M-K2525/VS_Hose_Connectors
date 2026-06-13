package com.mk2525.vsfluidlink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LinkSelection {
    private static final Map<Key, Selection> SELECTED_POSITIONS = new ConcurrentHashMap<>();

    private LinkSelection() {
    }

    public static BlockPos get(Player player, String channel) {
        LinkTarget target = getTarget(player, channel);
        return target != null ? target.pos() : null;
    }

    public static void set(Player player, String channel, BlockPos pos) {
        SELECTED_POSITIONS.put(new Key(player.getUUID(), channel), new Selection(
            new LinkTarget(pos.immutable(), VSLinkUtil.getSpatialId(player.level(), pos)),
            null
        ));
    }

    public static void set(Player player, String channel, BlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        SELECTED_POSITIONS.put(new Key(player.getUUID(), channel), new Selection(
            new LinkTarget(pos.immutable(), VSLinkUtil.getSpatialId(blockEntity.getLevel(), pos)),
            new WeakReference<>(blockEntity)
        ));
    }

    public static @Nullable LinkTarget getTarget(Player player, String channel) {
        Selection selection = SELECTED_POSITIONS.get(new Key(player.getUUID(), channel));
        return selection != null ? selection.target() : null;
    }

    public static <T extends BlockEntity> @Nullable T resolve(Player player, String channel, Class<T> expectedType) {
        Selection selection = SELECTED_POSITIONS.get(new Key(player.getUUID(), channel));
        if (selection == null) {
            return null;
        }

        BlockEntity cached = selection.blockEntityRef != null ? selection.blockEntityRef.get() : null;
        if (expectedType.isInstance(cached)
            && !cached.isRemoved()
            && VSLinkUtil.isSameEndpoint(cached, selection.target.pos(), selection.target.spaceId())) {
            return expectedType.cast(cached);
        }

        return VSLinkUtil.resolveBlockEntity(player.level(), selection.target.pos(), selection.target.spaceId(), expectedType);
    }

    public static void clear(Player player, String channel) {
        SELECTED_POSITIONS.remove(new Key(player.getUUID(), channel));
    }

    private record Key(UUID playerId, String channel) {
    }

    private record Selection(LinkTarget target, @Nullable WeakReference<BlockEntity> blockEntityRef) {
    }
}
