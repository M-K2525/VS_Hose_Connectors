package com.mk2525.vsfluidlink.compat.powergrid;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.mk2525.vsfluidlink.TestBootstrap;
import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ElectricBufferPersistenceTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.initialize();
        var config = CommentedConfig.inMemory();
        VsFluidLinkConfig.SERVER_SPEC.correct(config);
        VsFluidLinkConfig.SERVER_SPEC.setConfig(config);
        // Plain JUnit has no Forge transformer for CapabilityToken.getType().
        try (var capabilities = mockStatic(CapabilityManager.class)) {
            capabilities.when(() -> CapabilityManager.get(any())).thenAnswer(call -> mock(Capability.class));
            assertNotNull(ForgeCapabilities.ENERGY);
        }
    }

    private ElectricWireConnectorBlockEntity connector() {
        var be = new ElectricWireConnectorBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO,
                Blocks.CHEST.defaultBlockState());
        be.setLevel(mock(Level.class));
        return be;
    }

    @Test
    void legacyEnergyAndTargetSurviveSaveLoadAndClientSync() {
        var target = new BlockPos(123, 45, 678);
        var legacy = new CompoundTag();
        legacy.putInt("Energy", 12345);
        legacy.put("TargetPos", NbtUtils.writeBlockPos(target));
        var server = connector();
        server.load(legacy);
        assertEquals(12345, server.getEnergyStorage().getEnergyStored());
        assertEquals(target, server.getTargetPos());
        var saved = server.saveWithoutMetadata();
        assertTrue(saved.contains("Energy", Tag.TAG_INT));
        assertEquals(12345, saved.getInt("Energy"));
        var reloaded = connector();
        reloaded.load(saved);
        assertEquals(target, reloaded.getTargetPos());
        assertEquals(12345, reloaded.getEnergyStorage().getEnergyStored());
        var client = connector();
        client.handleUpdateTag(server.getUpdateTag());
        assertEquals(12345, client.getEnergyStorage().getEnergyStored());
        assertEquals(target, client.getTargetPos());
        client.handleUpdateTag(new CompoundTag());
        assertNull(client.getTargetPos());
    }

    @Test
    void energyCapabilityStillAcceptsEverySideAndNullWithoutPowerGrid() {
        try (var mods = mockStatic(ModList.class)) {
            mods.when(ModList::get).thenReturn(mock(ModList.class));
            var be = connector();
            var storage = be.getEnergyStorage();
            assertEquals(100000, storage.getMaxEnergyStored());
            assertSame(storage, be.getCapability(ForgeCapabilities.ENERGY, null).orElseThrow(AssertionError::new));
            for (var side : Direction.values()) {
                var capability = be.getCapability(ForgeCapabilities.ENERGY, side).orElseThrow(AssertionError::new);
                assertSame(storage, capability);
                assertEquals(100, capability.receiveEnergy(100, false));
                assertEquals(100, capability.extractEnergy(100, false));
            }
        }
    }
}
