package com.mk2525.vsfluidlink.compat.powergrid;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/** This entry point has no Power Grid types in its public signatures. */
public final class PowerGridCompat {
    private PowerGridCompat() {}

    public static ElectricWireConnectorBlockEntity create(BlockPos pos, BlockState state) {
        return ModList.get().isLoaded("powergrid")
                ? GridConnectorFactory.create(pos, state) : new ElectricWireConnectorBlockEntity(pos, state);
    }

    public static ElectricMagnetWireConnectorBlockEntity createMagnet(BlockPos pos, BlockState state) {
        return ModList.get().isLoaded("powergrid")
                ? GridConnectorFactory.createMagnet(pos, state) : new ElectricMagnetWireConnectorBlockEntity(pos, state);
    }

    public static void linkChanged(ElectricWireConnectorBlockEntity owner) {
        if (ModList.get().isLoaded("powergrid")) GridConnectorFactory.linkChanged(owner);
    }

    public static void remove(ElectricWireConnectorBlockEntity owner) {
        if (ModList.get().isLoaded("powergrid")) GridConnectorFactory.remove(owner);
    }
}
