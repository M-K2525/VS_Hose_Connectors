package com.mk2525.vsfluidlink.compat.powergrid;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/** One connector pair carries one conductor; use another pair for the return path. */
interface GridTerminal extends IElectric, IElectricEntity {
    ITerminalPlacement TERMINAL = new TerminalBoundingBox(
            Component.translatable("vsfluidlink.powergrid.terminal"), 0, 0, 0, 16, 16, 16);

    @Override
    default int terminalCount() { return 1; }

    @Override
    default ITerminalPlacement terminal(BlockState state, int index) {
        return index == 0 ? TERMINAL : null;
    }

    @Override
    default void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(1);
    }

    static void linkChanged(ElectricWireConnectorBlockEntity owner) {
        if (owner.getBehaviour(ElectricBehaviour.TYPE) instanceof GridTerminalBehaviour behaviour) {
            behaviour.linkChanged();
        }
    }

    static void remove(ElectricWireConnectorBlockEntity owner) {
        if (owner.getBehaviour(ElectricBehaviour.TYPE) instanceof GridTerminalBehaviour behaviour) {
            behaviour.remove();
        }
    }
}
