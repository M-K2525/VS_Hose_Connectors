package com.mk2525.vsfluidlink.content;

import net.minecraft.util.StringRepresentable;

public enum LinkActivity implements StringRepresentable {
    NONE,
    SEND,
    RECEIVE;

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase();
    }

    @Override
    public String toString() {
        return getSerializedName();
    }
}
