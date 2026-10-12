package com.mk2525.vsfluidlink;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.mk2525.vsfluidlink.util.LinkSelection;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinkSelectionRecoveryTest {
    private static final BlockPos OLD = BlockPos.ZERO;
    private static final BlockPos FIRST = new BlockPos(1, 0, 0);
    private static final BlockPos SECOND = new BlockPos(2, 0, 0);

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
    @BeforeEach void loadConfig() throws Exception {
        var config = CommentedConfig.inMemory();
        VsFluidLinkConfig.SERVER_SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        VsFluidLinkConfig.SERVER_SPEC.acceptConfig((IConfigSpec.ILoadedConfig)
                constructor.newInstance(config, null, null));
    }
    @AfterEach void unload() { VsFluidLinkConfig.SERVER_SPEC.acceptConfig(null); }

    @ParameterizedTest
    @CsvSource({
        "HoseConnector.HoseConnectorBlock, HoseConnector.HoseConnectorBlockEntity, hose_connector",
        "ElectricWireConnector.ElectricWireConnectorBlock, ElectricWireConnector.ElectricWireConnectorBlockEntity, electric_wire_connector",
        "ItemHoseConnecotor.ItemHoseConnectorBlock, ItemHoseConnecotor.ItemHoseConnectorBlockEntity, item_hose_connector",
        "ChainConnector.ChainConnectorBlock, ChainConnector.ChainConnectorBlockEntity, chain_connector"
    })
    void missingSelectedEndpointIsReplacedAndNextClickLinksBothEnds(String blockName, String entityName, String channel) throws Exception {
        var level = mock(Level.class);
        var player = mock(Player.class);
        when(player.level()).thenReturn(level);
        when(player.getUUID()).thenReturn(java.util.UUID.randomUUID());
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(ItemStack.EMPTY);
        var entityType = Class.forName("com.mk2525.vsfluidlink.content." + entityName).asSubclass(BlockEntity.class);
        var first = mock(entityType);
        var second = mock(entityType);
        when(first.getBlockPos()).thenReturn(FIRST);
        when(second.getBlockPos()).thenReturn(SECOND);
        when(first.getLevel()).thenReturn(level);
        when(second.getLevel()).thenReturn(level);
        when(level.getBlockEntity(FIRST)).thenReturn(first);
        when(level.getBlockEntity(SECOND)).thenReturn(second);
        var blockType = Class.forName("com.mk2525.vsfluidlink.content." + blockName);
        var block = mock(blockType, CALLS_REAL_METHODS);
        var use = blockType.getMethod("use", BlockState.class, Level.class, BlockPos.class, Player.class,
                InteractionHand.class, BlockHitResult.class);
        try (var spatial = mockStatic(VSLinkUtil.class)) {
            spatial.when(() -> VSLinkUtil.getSpatialId(eq(level), any(BlockPos.class))).thenReturn(null);
            spatial.when(() -> VSLinkUtil.getWorldPos(eq(level), any(BlockPos.class)))
                    .thenAnswer(call -> Vec3.atCenterOf(call.getArgument(1)));
            spatial.when(() -> VSLinkUtil.isSameEndpoint(first, FIRST, null)).thenReturn(true);
            LinkSelection.set(player, channel, OLD);
            assertEquals(InteractionResult.SUCCESS, use.invoke(block, null, level, FIRST, player, InteractionHand.MAIN_HAND, null));
            assertEquals(FIRST, LinkSelection.get(player, channel), "Dead selection must not swallow the first click");
            assertEquals(InteractionResult.SUCCESS, use.invoke(block, null, level, SECOND, player, InteractionHand.MAIN_HAND, null));
            entityType.getMethod("setTarget", BlockPos.class, Long.class).invoke(verify(first), SECOND, null);
            entityType.getMethod("setTarget", BlockPos.class, Long.class).invoke(verify(second), FIRST, null);
            assertNull(LinkSelection.get(player, channel));
        } finally {
            LinkSelection.clear(player, channel);
        }
    }

    @Test void selectionDoesNotSurviveMovingToAnotherWorldInstance() {
        var player = mock(Player.class);
        when(player.getUUID()).thenReturn(java.util.UUID.randomUUID());
        when(player.level()).thenReturn(mock(Level.class));
        try (var spatial = mockStatic(VSLinkUtil.class)) {
            LinkSelection.set(player, "test", FIRST);
            when(player.level()).thenReturn(mock(Level.class));
            assertNull(LinkSelection.get(player, "test"));
        } finally {
            LinkSelection.clear(player, "test");
        }
    }
}
