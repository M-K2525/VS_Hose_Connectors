package com.mk2525.vsfluidlink.compat.tfmg;

import com.drmangotea.tfmg.content.electricity.base.*;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.BlockPos;

/** A passive TFMG conductor, independent of the owner's FE buffer. */
public interface TfmgNode extends IElectric {
    default ElectricWireConnectorBlockEntity owner() { return (ElectricWireConnectorBlockEntity) this; }
    default TfmgBehaviour tfmg() { return owner().getBehaviour(TfmgBehaviour.TYPE); }
    @Override default BlockPos getPos() { return owner().getBlockPos(); }
    @Override default LevelAccessor getLevelAccessor() { return owner().getLevel(); }
    @Override default ElectricBlockValues getData() { return tfmg().data; }
    @Override default void sendStuff() { owner().sendData(); }
    @Override default int getMaxVoltage() { return 100000; }
    @Override default boolean isCable() { return true; }
    @Override default float resistance() { return 0; }
    @Override default float powerGeneration() { return 0; }
    @Override default int voltageGeneration() { return 0; }
    // A virtual conductor must not act as another TFMG-to-FE converter.
    @Override default void sendEnergy() {}
    @Override default void checkForFEOutputs(java.util.List<net.minecraft.core.Direction> directions) {
        getData().energyOutputs.clear();
    }
    @Override default ElectricalNetwork getOrCreateElectricNetwork() {
        return TfmgBehaviour.MANAGER.getOrCreateNetworkFor(this);
    }
    @Override default void onPlaced() { tfmg().refresh(); }
    @Override default void onConnected() {
        if (!tfmg().active()) return;
        IElectric.super.onConnected();
        tfmg().joinPeer();
    }
}
