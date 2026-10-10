package com.mk2525.vsfluidlink.compat.computercraft;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

public final class ComputerCraftCompat {
    private ComputerCraftCompat() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (ModList.get().isLoaded("computercraft")) {
            CCWiredConnection.registerCapabilities(event);
        }
    }

    public static @Nullable WiredConnection create(ElectricWireConnectorBlockEntity owner) {
        return ModList.get().isLoaded("computercraft") ? new CCWiredConnection(owner) : null;
    }
}
