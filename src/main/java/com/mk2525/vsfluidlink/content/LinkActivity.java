package com.mk2525.vsfluidlink.content;

import net.minecraft.util.StringRepresentable;
import java.util.Locale;

public enum LinkActivity implements StringRepresentable {
    NONE,
    SEND,
    RECEIVE;

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
