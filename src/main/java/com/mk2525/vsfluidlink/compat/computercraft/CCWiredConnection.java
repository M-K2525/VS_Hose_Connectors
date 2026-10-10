package com.mk2525.vsfluidlink.compat.computercraft;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredElementCapability;
import dan200.computercraft.api.network.wired.WiredNode;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/** Loaded only when CC: Tweaked is present. Cable/modem nodes manage adjacent edges. */
final class CCWiredConnection implements WiredConnection, WiredElement {
    private final ElectricWireConnectorBlockEntity owner;
    private final WiredNode node;
    private @Nullable CCWiredConnection peer;
    private long nextCheck;
    private boolean removed;

    CCWiredConnection(ElectricWireConnectorBlockEntity owner) {
        this.owner = owner;
        node = ComputerCraftAPI.createWiredNodeForElement(this);
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(WiredElementCapability.get(), ModBlockEntities.ELECTRIC_WIRE_CONNECTOR.get(),
                (be, side) -> (CCWiredConnection) be.getWiredConnection());
        event.registerBlockEntity(WiredElementCapability.get(), ModBlockEntities.ELECTRIC_MAGNET_WIRE_CONNECTOR.get(),
                (be, side) -> (CCWiredConnection) be.getWiredConnection());
    }

    @Override
    public WiredNode getNode() {
        return node;
    }

    @Override
    public Level getLevel() {
        return owner.getLevel();
    }

    @Override
    public Vec3 getPosition() {
        Level level = owner.getLevel();
        return level == null ? Vec3.atCenterOf(owner.getBlockPos()) : VSLinkUtil.getWorldPos(level, owner.getBlockPos());
    }

    @Override
    public String getSenderID() {
        return "vsfluidlink:" + owner.getBlockPos().toShortString();
    }

    @Override
    public void tick() {
        if (removed) return;
        Level level = owner.getLevel();
        if (level == null || level.isClientSide) return;
        if (peer != null && (peer.removed || peer.owner.isRemoved())) disconnect();
        if (level.getGameTime() < nextCheck) return;
        nextCheck = level.getGameTime() + 20;
        refreshLink();
    }

    @Override
    public void linkChanged() {
        if (removed) return;
        refreshLink();
        nextCheck = 0;
    }

    private void refreshLink() {
        Level level = owner.getLevel();
        CCWiredConnection desired = null;
        if (level != null && !owner.isRemoved() && owner.getTargetPos() != null
                && level.isLoaded(owner.getTargetPos())) {
            ElectricWireConnectorBlockEntity target = VSLinkUtil.resolveBlockEntity(level, owner.getTargetPos(),
                    owner.getTargetSpaceId(), ElectricWireConnectorBlockEntity.class);
            // Verify both identities, not just coordinates (different sub-levels can share coordinates).
            if (target != null && target != owner && !target.isRemoved() && target.getTargetPos() != null
                    && target.getLevel() != null && target.getLevel().isLoaded(target.getTargetPos())
                    && VSLinkUtil.resolveBlockEntity(target.getLevel(), target.getTargetPos(), target.getTargetSpaceId(),
                            ElectricWireConnectorBlockEntity.class) == owner) {
                double maxDistance = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
                if (getPosition().distanceToSqr(VSLinkUtil.getWorldPos(target.getLevel(), target.getBlockPos()))
                        <= maxDistance * maxDistance) {
                    desired = (CCWiredConnection) target.getWiredConnection();
                }
            }
        }
        connect(desired);
    }

    void connect(@Nullable CCWiredConnection desired) {
        if (peer == desired) return;
        disconnect();
        if (desired == null || desired == this || desired.removed || removed) return;
        desired.disconnect();
        node.connectTo(desired.node);
        peer = desired;
        desired.peer = this;
    }

    private void disconnect() {
        CCWiredConnection previous = peer;
        peer = null;
        if (previous == null) return;
        if (previous.peer == this) previous.peer = null;
        node.disconnectFrom(previous.node);
    }

    @Override
    public void remove() {
        if (removed) return;
        removed = true;
        disconnect();
        node.remove();
    }
}
