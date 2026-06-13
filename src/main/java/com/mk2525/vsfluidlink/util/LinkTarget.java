package com.mk2525.vsfluidlink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public record LinkTarget(BlockPos pos, @Nullable Long spaceId) {

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        if (spaceId != null) {
            tag.putBoolean("HasSpaceId", true);
            tag.putLong("SpaceId", spaceId);
        }
        return tag;
    }

    public static LinkTarget fromTag(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        Long spaceId = tag.getBoolean("HasSpaceId") ? tag.getLong("SpaceId") : null;
        return new LinkTarget(pos, spaceId);
    }
}
