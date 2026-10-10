package com.mk2525.vsfluidlink;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReciprocalLinkTest {
    private static final BlockPos SELF = new BlockPos(1, 2, 3);
    private static final BlockPos TARGET = new BlockPos(10, 20, 30);

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.initialize();
    }

    @Test
    void peerCanBeClearedAfterLocalTargetIsCleared() {
        var level = mock(Level.class);
        var peer = mock(ElectricWireConnectorBlockEntity.class);
        when(level.isLoaded(TARGET)).thenReturn(true);
        when(level.getBlockEntity(TARGET)).thenReturn(peer);
        when(peer.getTargetPos()).thenReturn(SELF);
        assertTrue(VSLinkUtil.clearReciprocalLink(level, SELF, TARGET, ElectricWireConnectorBlockEntity.class,
                ElectricWireConnectorBlockEntity::getTargetPos, ElectricWireConnectorBlockEntity::setTargetPos));
        verify(peer).setTargetPos(null);
    }

    @Test
    void peerConnectedToSomeoneElseIsNotCleared() {
        var level = mock(Level.class);
        var peer = mock(ElectricWireConnectorBlockEntity.class);
        when(level.isLoaded(TARGET)).thenReturn(true);
        when(level.getBlockEntity(TARGET)).thenReturn(peer);
        when(peer.getTargetPos()).thenReturn(new BlockPos(99, 99, 99));
        assertFalse(VSLinkUtil.clearReciprocalLink(level, SELF, TARGET, ElectricWireConnectorBlockEntity.class,
                ElectricWireConnectorBlockEntity::getTargetPos, ElectricWireConnectorBlockEntity::setTargetPos));
        verify(peer, never()).setTargetPos(any());
    }

    @Test
    void registeredPeerFallbackIsClearedAndUnregisteredPeerIsNotRetained() {
        var level = mock(Level.class);
        var peer = mock(ElectricWireConnectorBlockEntity.class);
        when(peer.getLevel()).thenReturn(level);
        when(peer.getBlockPos()).thenReturn(TARGET);
        when(peer.getTargetPos()).thenReturn(SELF);
        VSLinkUtil.registerLinkBlockEntity(peer);
        try {
            assertTrue(VSLinkUtil.clearReciprocalLink(level, SELF, TARGET, ElectricWireConnectorBlockEntity.class,
                    ElectricWireConnectorBlockEntity::getTargetPos, ElectricWireConnectorBlockEntity::setTargetPos));
            verify(peer).setTargetPos(null);
        } finally {
            VSLinkUtil.unregisterLinkBlockEntity(peer);
        }
        assertNull(VSLinkUtil.findReciprocalLink(level, SELF, TARGET, ElectricWireConnectorBlockEntity.class,
                ElectricWireConnectorBlockEntity::getTargetPos));
    }

    @Test
    void removedOrOtherLevelPeerIsIgnored() {
        var level = mock(Level.class);
        var peer = mock(ElectricWireConnectorBlockEntity.class);
        when(peer.getLevel()).thenReturn(level);
        when(peer.getBlockPos()).thenReturn(TARGET);
        when(peer.getTargetPos()).thenReturn(SELF);
        VSLinkUtil.registerLinkBlockEntity(peer);
        try {
            when(peer.isRemoved()).thenReturn(true);
            assertNull(VSLinkUtil.findReciprocalLink(level, SELF, TARGET, ElectricWireConnectorBlockEntity.class,
                    ElectricWireConnectorBlockEntity::getTargetPos));
            when(peer.isRemoved()).thenReturn(false);
            assertNull(VSLinkUtil.findReciprocalLink(mock(Level.class), SELF, TARGET, ElectricWireConnectorBlockEntity.class,
                    ElectricWireConnectorBlockEntity::getTargetPos));
        } finally {
            VSLinkUtil.unregisterLinkBlockEntity(peer);
        }
    }
}
