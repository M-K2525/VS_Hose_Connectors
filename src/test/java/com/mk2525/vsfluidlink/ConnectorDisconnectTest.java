package com.mk2525.vsfluidlink;

import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlock;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.HoseConnector.HoseConnectorBlock;
import com.mk2525.vsfluidlink.content.HoseConnector.HoseConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.MagnetHoseConnector.MagnetHoseConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ItemHoseConnecotor.ItemHoseConnectorBlock;
import com.mk2525.vsfluidlink.content.ItemHoseConnecotor.ItemHoseConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlock;
import com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlockEntity;
import com.mk2525.vsfluidlink.util.LinkSelection;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class ConnectorDisconnectTest {
    private static final BlockPos SELF = new BlockPos(1, 2, 3);
    private static final BlockPos TARGET = new BlockPos(10, 20, 30);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @ParameterizedTest
    @CsvSource({"electric", "fluid"})
    void magnetDisconnectPreservesTargetSpace(String kind) throws Exception {
        if (kind.equals("electric")) {
            checkDisconnect(ElectricMagnetWireConnectorBlockEntity.class, null, 123L, true);
        } else {
            checkDisconnect(MagnetHoseConnectorBlockEntity.class, null, 123L, true);
        }
    }

    @Test
    void unloadedPeerDoesNotPreventLocalDisconnect() throws Exception {
        checkDisconnect(ElectricMagnetWireConnectorBlockEntity.class, null, 123L, false);
    }

    @ParameterizedTest
    @CsvSource({"electric", "fluid", "item", "chain"})
    void wrenchDisconnectPreservesTargetSpace(String kind) throws Exception {
        switch (kind) {
            case "electric" -> checkDisconnect(ElectricWireConnectorBlockEntity.class, ElectricWireConnectorBlock.class, 123L, true);
            case "fluid" -> checkDisconnect(HoseConnectorBlockEntity.class, HoseConnectorBlock.class, 123L, true);
            case "item" -> checkDisconnect(ItemHoseConnectorBlockEntity.class, ItemHoseConnectorBlock.class, 123L, true);
            case "chain" -> checkDisconnect(ChainConnectorBlockEntity.class, ChainConnectorBlock.class, 123L, true);
            default -> throw new IllegalArgumentException(kind);
        }
    }

    @Test
    void worldEndpointStillDisconnects() throws Exception {
        checkDisconnect(ElectricMagnetWireConnectorBlockEntity.class, null, null, true);
    }

    private static <T extends BlockEntity> void checkDisconnect(Class<T> entityType, Class<?> blockType,
            Long spaceId, boolean loaded) throws Exception {
        var level = mock(Level.class);
        var source = mock(entityType);
        var peer = mock(entityType);
        var pos = new AtomicReference<>(TARGET);
        var space = new AtomicReference<>(spaceId);
        when(level.isLoaded(TARGET)).thenReturn(loaded);
        when(level.getBlockEntity(SELF)).thenReturn(source);
        // Model the real setter: clearing the position also clears the sub-level identity.
        when((BlockPos) entityType.getMethod("getTargetPos").invoke(source)).thenAnswer(call -> pos.get());
        when((Long) entityType.getMethod("getTargetSpaceId").invoke(source)).thenAnswer(call -> space.get());
        var clearing = doAnswer(call -> { pos.set(null); space.set(null); return null; }).when(source);
        entityType.getMethod("setTargetPos", BlockPos.class).invoke(clearing, new Object[]{null});

        try (var spatial = mockStatic(VSLinkUtil.class); var selection = mockStatic(LinkSelection.class);
                var config = mockStatic(VsFluidLinkConfig.class, CALLS_REAL_METHODS)) {
            config.when(VsFluidLinkConfig::wrenchRightClickAction)
                    .thenReturn(VsFluidLinkConfig.WrenchRightClickAction.CONNECT);
            spatial.when(() -> VSLinkUtil.resolveBlockEntity(level, TARGET, spaceId, entityType)).thenReturn(peer);
            if (blockType == null) {
                var method = entityType.getDeclaredMethod("disconnect", Level.class, BlockPos.class, entityType);
                method.setAccessible(true);
                method.invoke(null, level, SELF, source);
            } else {
                var block = mock(blockType, CALLS_REAL_METHODS);
                var player = mock(Player.class);
                var stack = mock(ItemStack.class);
                var wrench = mock(Item.class);
                when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
                when(stack.getItem()).thenReturn(wrench);
                when(wrench.getDescriptionId()).thenReturn("test:wrench");
                blockType.getMethod("use", BlockState.class, Level.class, BlockPos.class, Player.class,
                        InteractionHand.class, BlockHitResult.class).invoke(block, null, level, SELF, player,
                                InteractionHand.MAIN_HAND, null);
            }
            assertNull(pos.get());
            assertNull(space.get());
            if (loaded) {
                spatial.verify(() -> VSLinkUtil.resolveBlockEntity(level, TARGET, spaceId, entityType));
                entityType.getMethod("setTarget", BlockPos.class, Long.class).invoke(verify(peer), null, null);
            } else {
                spatial.verifyNoInteractions();
                verifyNoInteractions(peer);
            }
        }
    }
}
