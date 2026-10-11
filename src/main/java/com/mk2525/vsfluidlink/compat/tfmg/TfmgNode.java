package com.mk2525.vsfluidlink.compat.tfmg;

import com.drmangotea.tfmg.content.electricity.base.*;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import net.minecraft.world.level.LevelAccessor;

/** A passive native 1.0.2f conductor, independent of the owner's FE buffer. */
public interface TfmgNode extends IElectric {
    default ElectricWireConnectorBlockEntity owner() { return (ElectricWireConnectorBlockEntity) this; }
    default TfmgBehaviour tfmg() { return owner().getBehaviour(TfmgBehaviour.TYPE); }
    @Override default long getPos() { return owner().getBlockPos().asLong(); }
    @Override default LevelAccessor getLevelAccessor() { return owner().getLevel(); }
    @Override default ElectricBlockValues getData() { return tfmg().data; }
    @Override default boolean destroyed() { return getData().destroyed; }
    @Override default void sendStuff() { owner().sendData(); }
    @Override default int getMaxVoltage() { return 100000; }
    @Override default float resistance() { return 0; }
    @Override default int powerGeneration() { return 0; }
    @Override default int voltageGeneration() { return 0; }
    @Override default int frequencyGeneration() { return 0; }
    @Override default int getNetworkResistance() { return getData().networkResistance; }
    @Override default void setNetworkResistance(int resistance) { getData().networkResistance = resistance; }
    @Override default void setVoltage(int voltage) { getData().voltage = voltage; }
    @Override default void setFrequency(int frequency) { getData().frequency = frequency; }
    @Override default void setNetwork(long network) { getData().electricalNetworkId = network; }
    @Override default void updateNextTick() { getData().updateNextTick = true; }
    @Override default void updateNetwork() { getOrCreateElectricNetwork().updateNetwork(); }
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
