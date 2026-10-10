package com.mk2525.vsfluidlink;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.network.NetworkHooks;

import static org.mockito.Mockito.mockStatic;

public final class TestBootstrap {
    private TestBootstrap() {}

    public static void initialize() {
        SharedConstants.tryDetectVersion();
        // Unit tests have no ModLauncher mapping service or Forge login channels.
        try (var network = mockStatic(NetworkHooks.class)) {
            Bootstrap.bootStrap();
        }
    }
}
