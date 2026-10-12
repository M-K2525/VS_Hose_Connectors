package com.mk2525.vsfluidlink;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.mk2525.vsfluidlink.util.ConnectorInteraction;
import com.mk2525.vsfluidlink.util.ConnectorOrientation;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectorInteractionTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
    @AfterEach void unload() { VsFluidLinkConfig.SERVER_SPEC.acceptConfig(null); }

    private void load(boolean ordinary, VsFluidLinkConfig.WrenchRightClickAction action) throws Exception {
        var config = CommentedConfig.inMemory();
        VsFluidLinkConfig.SERVER_SPEC.correct(config);
        config.set("general.connectWithoutWrench", ordinary);
        config.set("general.wrenchRightClickAction", action.name());
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        VsFluidLinkConfig.SERVER_SPEC.acceptConfig((IConfigSpec.ILoadedConfig)
                constructor.newInstance(config, null, null));
    }

    private ItemStack stack(String name) {
        var stack = mock(ItemStack.class);
        var item = mock(Item.class);
        when(stack.getItem()).thenReturn(item);
        when(item.getDescriptionId()).thenReturn(name);
        return stack;
    }

    @Test void defaultsAllowOrdinaryItemsButReserveWrenchForRotation() {
        VsFluidLinkConfig.SERVER_SPEC.acceptConfig(null);
        assertTrue(VsFluidLinkConfig.connectWithoutWrench());
        assertEquals(VsFluidLinkConfig.WrenchRightClickAction.ROTATE, VsFluidLinkConfig.wrenchRightClickAction());
        assertTrue(ConnectorInteraction.canLink(ItemStack.EMPTY, false));
        assertTrue(ConnectorInteraction.canLink(stack("test:item"), false));
        assertFalse(ConnectorInteraction.canLink(stack("test:wrench"), false));
        assertFalse(ConnectorInteraction.canLink(ItemStack.EMPTY, true));
    }

    @Test void togglesIndependentlyControlOrdinaryAndWrenchLinking() throws Exception {
        for (var action : VsFluidLinkConfig.WrenchRightClickAction.values()) {
            for (boolean ordinary : new boolean[]{false, true}) {
                load(ordinary, action);
                assertEquals(ordinary, ConnectorInteraction.canLink(ItemStack.EMPTY, false));
                assertEquals(ordinary, ConnectorInteraction.canLink(stack("test:item"), false));
                assertEquals(action == VsFluidLinkConfig.WrenchRightClickAction.CONNECT,
                        ConnectorInteraction.canLink(stack("test:wrench"), false));
                assertFalse(ConnectorInteraction.canLink(stack("test:wrench"), true));
            }
        }
    }

    private UseOnContext context(Level level) {
        var context = mock(UseOnContext.class);
        var player = mock(Player.class);
        when(player.mayBuild()).thenReturn(true);
        when(player.isCreative()).thenReturn(true);
        when(player.level()).thenReturn(level);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        var inventory = mock(net.minecraft.world.entity.player.Inventory.class);
        when(inventory.getItem(anyInt())).thenReturn(ItemStack.EMPTY);
        when(player.getInventory()).thenReturn(inventory);
        when(context.getPlayer()).thenReturn(player);
        when(context.getLevel()).thenReturn(level);
        when(context.getClickedPos()).thenReturn(BlockPos.ZERO);
        when(context.getClickedFace()).thenReturn(Direction.UP);
        return context;
    }

    @Test void rotateModeSneakWrenchUsesCreatePickupInsteadOfRotation() throws Exception {
        load(true, VsFluidLinkConfig.WrenchRightClickAction.ROTATE);
        var level = mock(ServerLevel.class);
        var state = mock(BlockState.class);
        var context = context(level);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(state);
        try (var sounds = mockStatic(IWrenchable.class); var kinetics = mockStatic(KineticBlockEntity.class)) {
            assertEquals(InteractionResult.SUCCESS, ConnectorOrientation.onSneakWrenched(state, context));
            verify(level).destroyBlock(BlockPos.ZERO, false);
            kinetics.verifyNoInteractions();
        }
    }

    @Test void wrenchModesSelectRotationOnDifferentGestures() throws Exception {
        var level = mock(Level.class);
        var state = mock(BlockState.class);
        var rotated = mock(BlockState.class);
        var context = context(level);
        try (var orientation = mockStatic(ConnectorOrientation.class, CALLS_REAL_METHODS);
                var sounds = mockStatic(IWrenchable.class); var kinetics = mockStatic(KineticBlockEntity.class)) {
            orientation.when(() -> ConnectorOrientation.rotateAroundFace(state, Direction.UP)).thenReturn(rotated);
            load(true, VsFluidLinkConfig.WrenchRightClickAction.ROTATE);
            assertEquals(InteractionResult.SUCCESS, ConnectorOrientation.onWrenched(state, context));
            kinetics.verify(() -> KineticBlockEntity.switchToBlockState(level, BlockPos.ZERO, rotated));
            kinetics.clearInvocations();
            load(true, VsFluidLinkConfig.WrenchRightClickAction.CONNECT);
            assertEquals(InteractionResult.PASS, ConnectorOrientation.onWrenched(state, context));
            kinetics.verifyNoInteractions();
            assertEquals(InteractionResult.SUCCESS, ConnectorOrientation.onSneakWrenched(state, context));
            kinetics.verify(() -> KineticBlockEntity.switchToBlockState(level, BlockPos.ZERO, rotated));
            verify(level, never()).destroyBlock(any(), anyBoolean());
        }
    }

    private static final String[] MANUAL_BLOCKS = {
        "HoseConnector.HoseConnectorBlock", "ElectricWireConnector.ElectricWireConnectorBlock",
        "ItemHoseConnecotor.ItemHoseConnectorBlock", "ChainConnector.ChainConnectorBlock"
    };

    @Test void emptyHandActuallySelectsEndpointsOnAllManualConnectors() throws Exception {
        load(true, VsFluidLinkConfig.WrenchRightClickAction.ROTATE);
        var level = mock(Level.class);
        var player = mock(Player.class);
        var endpoint = mock(net.minecraft.world.level.block.entity.BlockEntity.class);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(endpoint);
        when(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)).thenReturn(ItemStack.EMPTY);
        try (var selection = mockStatic(com.mk2525.vsfluidlink.util.LinkSelection.class)) {
            for (String name : MANUAL_BLOCKS) {
                var type = Class.forName("com.mk2525.vsfluidlink.content." + name);
                var block = mock(type, CALLS_REAL_METHODS);
                var use = type.getDeclaredMethod("useWithoutItem", BlockState.class, Level.class, BlockPos.class,
                        Player.class, net.minecraft.world.phys.BlockHitResult.class);
                use.setAccessible(true);
                assertEquals(InteractionResult.SUCCESS, use.invoke(block, null, level, BlockPos.ZERO, player, null));
                selection.verify(() -> com.mk2525.vsfluidlink.util.LinkSelection.set(eq(player), anyString(), eq(endpoint)));
                selection.clearInvocations();
            }
        }
    }

    @Test void allEightConnectorTypesDelegateBothWrenchGestures() throws Exception {
        var names = new java.util.ArrayList<>(java.util.List.of(MANUAL_BLOCKS));
        names.addAll(java.util.List.of("MagnetHoseConnector.MagnetHoseConnectorBlock",
                "ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlock",
                "ItemMagnetHoseConnector.ItemMagnetHoseConnectorBlock", "MagnetChainConnector.MagnetChainConnectorBlock"));
        var state = mock(BlockState.class);
        var context = mock(UseOnContext.class);
        try (var orientation = mockStatic(ConnectorOrientation.class)) {
            orientation.when(() -> ConnectorOrientation.onWrenched(state, context)).thenReturn(InteractionResult.SUCCESS);
            orientation.when(() -> ConnectorOrientation.onSneakWrenched(state, context)).thenReturn(InteractionResult.SUCCESS);
            for (String name : names) {
                var type = Class.forName("com.mk2525.vsfluidlink.content." + name);
                var block = mock(type, CALLS_REAL_METHODS);
                assertEquals(InteractionResult.SUCCESS, type.getMethod("onWrenched", BlockState.class, UseOnContext.class)
                        .invoke(block, state, context));
                assertEquals(InteractionResult.SUCCESS, type.getMethod("onSneakWrenched", BlockState.class, UseOnContext.class)
                        .invoke(block, state, context));
            }
            orientation.verify(() -> ConnectorOrientation.onWrenched(state, context), times(8));
            orientation.verify(() -> ConnectorOrientation.onSneakWrenched(state, context), times(8));
        }
    }
}
