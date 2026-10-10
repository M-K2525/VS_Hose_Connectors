package com.mk2525.vsfluidlink.compat.powergrid;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

final class GridTerminalBehaviour extends ElectricBehaviour {
    // PG copper wire: 0.0015 ohm/item, 0.5 items/m. Fixed equivalent of 20 m.
    static final float LINK_RESISTANCE = 0.015f;
    private final ElectricWireConnectorBlockEntity owner;
    private @Nullable GridTerminalBehaviour peer;
    private @Nullable ElectricWire wire;
    private boolean ready;
    private boolean removed;
    private long nextCheck;

    <T extends ElectricWireConnectorBlockEntity & IElectricEntity> GridTerminalBehaviour(T owner) {
        super(owner);
        this.owner = owner;
    }

    @Override
    public void initialize() {
        super.initialize();
        ready = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!canUpdate()) return;
        if (peer != null && (!peer.ready || peer.removed || peer.owner.isRemoved())) disconnect();
        if (getWorld().getGameTime() < nextCheck) return;
        nextCheck = getWorld().getGameTime() + 20;
        refreshLink();
    }

    void linkChanged() {
        if (!canUpdate()) return;
        refreshLink();
        nextCheck = 0;
    }

    private boolean canUpdate() {
        return ready && !removed && getWorld() != null && !getWorld().isClientSide
                && getWorld().getServer() != null && getWorld().getServer().isSameThread();
    }

    private void refreshLink() {
        var level = getWorld();
        GridTerminalBehaviour desired = null;
        if (!owner.isRemoved() && owner.getTargetPos() != null) {
            var target = VSLinkUtil.findReciprocalLink(level, owner.getBlockPos(), owner.getTargetPos(),
                    ElectricWireConnectorBlockEntity.class, ElectricWireConnectorBlockEntity::getTargetPos);
            if (target != null && target != owner && !target.isRemoved() && target.getLevel() == level
                    && target.getTargetPos() != null
                    && VSLinkUtil.findReciprocalLink(level, target.getBlockPos(), target.getTargetPos(),
                            ElectricWireConnectorBlockEntity.class, ElectricWireConnectorBlockEntity::getTargetPos) == owner) {
                double maxDistance = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
                if (VSLinkUtil.getWorldPos(level, owner.getBlockPos()).distanceToSqr(
                        VSLinkUtil.getWorldPos(level, target.getBlockPos())) <= maxDistance * maxDistance
                        && target.getBehaviour(TYPE) instanceof GridTerminalBehaviour candidate
                        && candidate.ready && !candidate.removed) desired = candidate;
            }
        }
        connect(desired);
    }

    void connect(@Nullable GridTerminalBehaviour desired) {
        if (peer == desired && (desired == null || hasCurrentWire(desired))) return;
        disconnect();
        if (desired == null || desired == this || !desired.ready || desired.removed) return;
        var level = getWorld();
        // VS ship storage belongs to one Minecraft Level. Never join separate managers.
        if (desired.getWorld() != level || GlobalElectricNetworks.getWorldNetworks(level)
                != GlobalElectricNetworks.getWorldNetworks(desired.getWorld())) return;
        desired.disconnect();
        var connection = GlobalElectricNetworks.makeSimpleConnection(level,
                new BlockWireEndpoint(getPos(), 0), new BlockWireEndpoint(desired.getPos(), 0), LINK_RESISTANCE);
        if (connection == null) return;
        wire = connection;
        desired.wire = connection;
        peer = desired;
        desired.peer = this;
    }

    private boolean hasCurrentWire(GridTerminalBehaviour desired) {
        if (wire == null || wire.getNetwork() == null) return false;
        var local = getTerminal(0);
        var remote = desired.getTerminal(0);
        return wire.getNode1() == local && wire.getNode2() == remote
                || wire.getNode2() == local && wire.getNode1() == remote;
    }

    private void disconnect() {
        var previous = peer;
        var previousWire = wire;
        peer = null;
        wire = null;
        if (previous != null && previous.peer == this) {
            previous.peer = null;
            previous.wire = null;
            previous.nextCheck = 0;
        }
        if (previousWire != null) {
            var network = previousWire.getNetwork();
            previousWire.remove();
            if (network != null) GlobalElectricNetworks.getWorldNetworks(getWorld()).scheduleIslandDiscovery(network);
        }
    }

    @Override
    public void unload() {
        if (!ready) return;
        ready = false;
        disconnect();
        super.unload();
    }

    @Override
    public void remove() {
        if (removed) return;
        removed = true;
        ready = false;
        disconnect();
        super.remove();
    }
}
