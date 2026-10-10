package com.mk2525.vsfluidlink.compat.computercraft;

/** Lifecycle boundary which does not reference any optional CC: Tweaked types. */
public interface WiredConnection {
    void tick();

    void linkChanged();

    void remove();
}
