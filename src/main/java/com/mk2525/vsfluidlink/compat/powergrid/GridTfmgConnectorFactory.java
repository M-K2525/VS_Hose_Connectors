package com.mk2525.vsfluidlink.compat.powergrid;

import com.mk2525.vsfluidlink.compat.tfmg.TfmgBehaviour;
import com.mk2525.vsfluidlink.compat.tfmg.TfmgNode;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

/** Loaded only when BOTH optional electricity mods are present. */
final class GridTfmgConnectorFactory {
    static ElectricWireConnectorBlockEntity create(BlockPos pos, BlockState state) {
        return new Electric(pos, state);
    }
    static ElectricMagnetWireConnectorBlockEntity createMagnet(BlockPos pos, BlockState state) {
        return new Magnet(pos, state);
    }
    private static final class Electric extends ElectricWireConnectorBlockEntity implements GridTerminal, TfmgNode {
        Electric(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public float resistance() { return 0; }
        @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
            behaviours.add(new GridTerminalBehaviour(this));
            behaviours.add(new TfmgBehaviour(this));
        }
    }
    private static final class Magnet extends ElectricMagnetWireConnectorBlockEntity implements GridTerminal, TfmgNode {
        Magnet(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public float resistance() { return 0; }
        @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
            behaviours.add(new GridTerminalBehaviour(this));
            behaviours.add(new TfmgBehaviour(this));
        }
    }
}
