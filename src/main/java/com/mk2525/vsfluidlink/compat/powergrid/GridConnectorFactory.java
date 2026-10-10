package com.mk2525.vsfluidlink.compat.powergrid;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded only after the optional-mod check; prevents eager verification of PG subclasses. */
final class GridConnectorFactory {
    private GridConnectorFactory() {}

    static ElectricWireConnectorBlockEntity create(BlockPos pos, BlockState state) {
        return new GridElectricConnector(pos, state);
    }

    static ElectricMagnetWireConnectorBlockEntity createMagnet(BlockPos pos, BlockState state) {
        return new GridMagnetConnector(pos, state);
    }

    static void linkChanged(ElectricWireConnectorBlockEntity owner) { GridTerminal.linkChanged(owner); }

    static void remove(ElectricWireConnectorBlockEntity owner) { GridTerminal.remove(owner); }
}
