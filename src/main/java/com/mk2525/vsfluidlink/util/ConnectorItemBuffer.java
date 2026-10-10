package com.mk2525.vsfluidlink.util;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import java.util.function.IntSupplier;

public class ConnectorItemBuffer extends ItemStackHandler {
    private final IntSupplier configuredSlots;
    private int insertionSlots;

    public ConnectorItemBuffer() {
        this(VsFluidLinkConfig::itemSlots);
    }

    public ConnectorItemBuffer(IntSupplier configuredSlots) {
        super(configuredSlots.getAsInt());
        this.configuredSlots = configuredSlots;
        insertionSlots = getSlots();
    }

    public void applyConfiguredSize() {
        insertionSlots = configuredSlots.getAsInt();
        int retainedSlots = insertionSlots;
        // Keep occupied overflow slots extractable when the configured size shrinks.
        for (int slot = insertionSlots; slot < stacks.size(); slot++) {
            if (!stacks.get(slot).isEmpty()) {
                retainedSlots = slot + 1;
            }
        }
        NonNullList<ItemStack> resized = NonNullList.withSize(retainedSlots, ItemStack.EMPTY);
        for (int slot = 0; slot < Math.min(stacks.size(), retainedSlots); slot++) {
            resized.set(slot, stacks.get(slot));
        }
        stacks = resized;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        super.deserializeNBT(tag);
        applyConfiguredSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot < insertionSlots && super.isItemValid(slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot < insertionSlots ? super.getSlotLimit(slot) : 0;
    }

    public void transferTo(ItemStackHandler target) {
        for (int slot = 0; slot < getSlots(); slot++) {
            ItemStack stack = getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(target, stack, false);
            if (remaining.getCount() != stack.getCount()) {
                setStackInSlot(slot, remaining);
            }
        }
    }
}
