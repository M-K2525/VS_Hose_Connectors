package com.mk2525.vsfluidlink.compat.computercraft;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public interface WiredConnection {
    void tick();
    void linkChanged();
    void remove();
    <T> LazyOptional<T> getCapability(Capability<T> capability);
}
