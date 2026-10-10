package com.mk2525.vsfluidlink.compat.computercraft;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class ComputerCraftCompat {
    private ComputerCraftCompat() {}

    public static @Nullable WiredConnection create(ElectricWireConnectorBlockEntity owner) {
        return ModList.get().isLoaded("computercraft") ? new CCWiredConnection(owner) : null;
    }
}
