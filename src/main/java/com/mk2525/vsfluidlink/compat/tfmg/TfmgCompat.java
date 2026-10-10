package com.mk2525.vsfluidlink.compat.tfmg;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/** No TFMG types cross the optional-mod boundary. */
public final class TfmgCompat {
    private TfmgCompat() {}

    public static boolean enabled() {
        return ModList.get().getModContainerById("tfmg")
                // Maven's numeric getters do not parse CE's "1.3.2a-community" qualifier.
                .map(mod -> mod.getModInfo().getVersion().toString())
                .map(version -> version.matches("1\\.3\\.\\d+[a-z]*-community"))
                .orElse(false);
    }

    public static ElectricWireConnectorBlockEntity create(BlockPos pos, BlockState state) {
        return TfmgConnectorFactory.create(pos, state);
    }

    public static ElectricMagnetWireConnectorBlockEntity createMagnet(BlockPos pos, BlockState state) {
        return TfmgConnectorFactory.createMagnet(pos, state);
    }

    public static void linkChanged(ElectricWireConnectorBlockEntity owner) {
        if (enabled()) TfmgConnectorFactory.linkChanged(owner);
    }

    public static boolean usesNativePort(ElectricWireConnectorBlockEntity owner, Direction side) {
        return enabled() && TfmgConnectorFactory.usesNativePort(owner, side);
    }
}
