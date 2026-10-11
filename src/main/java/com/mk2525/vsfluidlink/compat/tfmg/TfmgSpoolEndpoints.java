package com.mk2525.vsfluidlink.compat.tfmg;

import com.drmangotea.tfmg.content.electricity.connection.cables.CableConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Resolves each native cable terminal independently, on both logical sides. */
public final class TfmgSpoolEndpoints {
    private TfmgSpoolEndpoints() {}

    public static BlockPos resolve(Level level, BlockPos origin) {
        if (!level.isLoaded(origin)
                || !(level.getBlockEntity(origin) instanceof CableConnectorBlockEntity)) return origin;
        var state = level.getBlockState(origin);
        if (!state.hasProperty(BlockStateProperties.FACING)) return origin;
        var facing = state.getValue(BlockStateProperties.FACING);
        var pos = origin;
        for (int i = 0; i < 64; i++) {
            var next = pos.relative(facing);
            if (!level.isLoaded(next)
                    || !(level.getBlockEntity(next) instanceof CableConnectorBlockEntity)) break;
            var nextState = level.getBlockState(next);
            if (!nextState.hasProperty(BlockStateProperties.FACING)
                    || nextState.getValue(BlockStateProperties.FACING) != facing) break;
            pos = next;
        }
        return pos;
    }
}
