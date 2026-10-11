package com.mk2525.vsfluidlink.compat.tfmg;

import com.mk2525.vsfluidlink.compat.powergrid.PowerGridCompat;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

final class TfmgConnectorFactory {
    static ElectricWireConnectorBlockEntity create(BlockPos pos, BlockState state) {
        return ModList.get().isLoaded("powergrid") ? PowerGridCompat.createTfmg(pos, state)
                : new TfmgElectricConnector(pos, state);
    }

    static ElectricMagnetWireConnectorBlockEntity createMagnet(BlockPos pos, BlockState state) {
        return ModList.get().isLoaded("powergrid") ? PowerGridCompat.createTfmgMagnet(pos, state)
                : new TfmgMagnetConnector(pos, state);
    }

    static void linkChanged(ElectricWireConnectorBlockEntity owner) {
        var behaviour = owner.getBehaviour(TfmgBehaviour.TYPE);
        if (behaviour != null) behaviour.refresh();
    }

    static boolean usesNativePort(ElectricWireConnectorBlockEntity owner, Direction side) {
        return owner instanceof TfmgNode && TfmgBehaviour.usesNativePort(owner, side);
    }
}
