package com.mk2525.vsfluidlink;

import com.mk2525.vsfluidlink.util.ConnectorItemBuffer;
import com.mk2525.vsfluidlink.content.HoseConnector.HoseConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class ConnectorBufferTest {
    @Test
    public void fluidAndEnergyPersistence() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider registries = HolderLookup.Provider.create(Stream.of(BuiltInRegistries.FLUID.asLookup()));
        TestFluidConnector fluid = new TestFluidConnector();
        check(fluid.getTank().getCapacity() == 1000, "fluid constructor uses default capacity");
        CompoundTag tank = new CompoundTag();
        tank.put("Fluid", new FluidStack(Fluids.WATER, 6000).save(registries));
        CompoundTag fluidTag = new CompoundTag();
        fluidTag.put("Tank", tank);
        fluid.loadAdditional(fluidTag, registries);
        check(fluid.getTank().getFluidAmount() == 1000, "oversized saved fluid is clamped");

        TestEnergyConnector energy = new TestEnergyConnector();
        check(energy.getEnergyStorage().getMaxEnergyStored() == 100000, "energy constructor uses default capacity");
        CompoundTag energyTag = new CompoundTag();
        energyTag.putInt("Energy", 50000);
        energy.loadAdditional(energyTag, registries);
        check(energy.getEnergyStorage().getEnergyStored() == 50000, "saved energy above old 10K limit is retained");
        check(energy.getEnergyStorage().receiveEnergy(50000, true) == 10000, "transfer rate unchanged");
        check(energy.getEnergyStorage().extractEnergy(50000, true) == 10000, "extraction rate unchanged");
        energyTag.putInt("Energy", 300000);
        energy.loadAdditional(energyTag, registries);
        check(energy.getEnergyStorage().getEnergyStored() == 100000, "oversized saved energy is clamped");
    }

    private static final class TestFluidConnector extends HoseConnectorBlockEntity {
        private TestFluidConnector() { super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
        @Override
        public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) { super.loadAdditional(tag, provider); }
    }

    private static final class TestEnergyConnector extends ElectricWireConnectorBlockEntity {
        private TestEnergyConnector() { super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
        @Override
        public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) { super.loadAdditional(tag, provider); }
    }

    @Test
    public void inventoryPersistenceAndTransfer() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider registries = HolderLookup.Provider.create(Stream.of(BuiltInRegistries.ITEM.asLookup()));

        check(VsFluidLinkConfig.fluidCapacity() == 1000, "default fluid capacity");
        check(VsFluidLinkConfig.itemSlots() == 1, "default item slots");
        check(VsFluidLinkConfig.energyCapacity() == 100000, "default energy capacity");

        AtomicInteger slots = new AtomicInteger(1);
        ConnectorItemBuffer source = new ConnectorItemBuffer(slots::get);
        source.setStackInSlot(0, new ItemStack(Items.STONE, 40));
        CompoundTag saved = source.serializeNBT(registries);
        slots.set(3);
        source.deserializeNBT(registries, saved);
        check(source.getSlots() == 3, "saved Size must not override configured slot count");
        check(source.getStackInSlot(0).getCount() == 40, "growth retains existing items");
        source.setStackInSlot(1, new ItemStack(Items.DIRT, 32));
        source.setStackInSlot(2, new ItemStack(Items.STONE, 30));
        ConnectorItemBuffer target = new ConnectorItemBuffer(() -> 3);
        target.setStackInSlot(0, new ItemStack(Items.STONE, 50));
        source.transferTo(target);
        check(count(source) == 0 && count(target) == 152, "all slots transfer without loss or duplication");
        check(target.getStackInSlot(0).getCount() == 64, "existing stacks are filled first");

        source.setStackInSlot(2, new ItemStack(Items.DIRT, 12));
        slots.set(1);
        source.deserializeNBT(registries, source.serializeNBT(registries));
        check(source.getSlots() == 3 && count(source) == 12, "shrinking preserves occupied overflow");
        check(source.insertItem(2, new ItemStack(Items.DIRT), false).getCount() == 1,
                "overflow slots reject new items");
        saved = source.serializeNBT(registries);
        source.deserializeNBT(registries, saved);
        check(count(source) == 12, "overflow survives another save and reload");
        check(source.extractItem(2, 12, false).getCount() == 12, "overflow remains extractable");
        source.deserializeNBT(registries, source.serializeNBT(registries));
        check(source.getSlots() == 1, "empty overflow is removed on reload");

        ConnectorItemBuffer full = new ConnectorItemBuffer(() -> 1);
        full.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        source.setStackInSlot(0, new ItemStack(Items.STONE, 16));
        source.transferTo(full);
        check(count(source) == 16 && count(full) == 64, "full target retains source items");
        full.setStackInSlot(0, new ItemStack(Items.STONE, 60));
        source.transferTo(full);
        check(count(source) == 12 && count(full) == 64, "partial transfer conserves items");
        System.out.println("Connector buffer tests passed.");
    }

    private static int count(ConnectorItemBuffer buffer) {
        int count = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            count += buffer.getStackInSlot(slot).getCount();
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
