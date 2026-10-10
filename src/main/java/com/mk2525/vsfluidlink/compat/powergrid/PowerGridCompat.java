package com.mk2525.vsfluidlink.compat.powergrid;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/** This entry point has no Power Grid types in its public signatures. */
public final class PowerGridCompat {
    private PowerGridCompat() {}

    public static ElectricWireConnectorBlockEntity create(BlockPos pos, BlockState state) {
        if (com.mk2525.vsfluidlink.compat.tfmg.TfmgCompat.enabled())
            return com.mk2525.vsfluidlink.compat.tfmg.TfmgCompat.create(pos, state);
        return ModList.get().isLoaded("powergrid")
                ? GridConnectorFactory.create(pos, state) : new ElectricWireConnectorBlockEntity(pos, state);
    }

    public static ElectricMagnetWireConnectorBlockEntity createMagnet(BlockPos pos, BlockState state) {
        if (com.mk2525.vsfluidlink.compat.tfmg.TfmgCompat.enabled())
            return com.mk2525.vsfluidlink.compat.tfmg.TfmgCompat.createMagnet(pos, state);
        return ModList.get().isLoaded("powergrid")
                ? GridConnectorFactory.createMagnet(pos, state) : new ElectricMagnetWireConnectorBlockEntity(pos, state);
    }

    public static void linkChanged(ElectricWireConnectorBlockEntity owner) {
        if (ModList.get().isLoaded("powergrid")) GridConnectorFactory.linkChanged(owner);
    }

    public static void remove(ElectricWireConnectorBlockEntity owner) {
        if (ModList.get().isLoaded("powergrid")) GridConnectorFactory.remove(owner);
    }

    public static ElectricWireConnectorBlockEntity createTfmg(BlockPos pos, BlockState state) {
        return GridTfmgConnectorFactory.create(pos, state);
    }

    public static ElectricMagnetWireConnectorBlockEntity createTfmgMagnet(BlockPos pos, BlockState state) {
        return GridTfmgConnectorFactory.createMagnet(pos, state);
    }
}
