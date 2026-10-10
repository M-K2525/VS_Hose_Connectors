package com.mk2525.vsfluidlink.compat.tfmg;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

final class TfmgElectricConnector extends ElectricWireConnectorBlockEntity implements TfmgNode {
    TfmgElectricConnector(BlockPos pos, BlockState state) { super(pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new TfmgBehaviour(this));
    }
}
