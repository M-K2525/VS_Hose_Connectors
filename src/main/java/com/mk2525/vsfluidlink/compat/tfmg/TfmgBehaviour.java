package com.mk2525.vsfluidlink.compat.tfmg;

import com.drmangotea.tfmg.content.electricity.base.*;
import com.drmangotea.tfmg.content.electricity.connection.cables.CableConnectorBlockEntity;
import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public final class TfmgBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<TfmgBehaviour> TYPE = new BehaviourType<>();
    static final ElectricNetworkManager MANAGER = new ElectricNetworkManager();
    final ElectricBlockValues data;
    private final TfmgNode node;
    private final ElectricWireConnectorBlockEntity owner;
    private TfmgBehaviour peer;
    private List<IElectric> neighbours = List.of();
    private record Topology(IElectric member, long network, boolean live, List<IElectric> links) {}
    private List<Topology> topology = List.of();
    private boolean ready;
    private long nextCheck;

    public <T extends ElectricWireConnectorBlockEntity & TfmgNode> TfmgBehaviour(T owner) {
        super(owner);
        this.owner = owner;
        node = owner;
        data = new ElectricBlockValues(owner.getBlockPos().asLong());
    }

    @Override public BehaviourType<?> getType() { return TYPE; }
    @Override public void initialize() {
        ready = true;
        data.destroyed = false;
        refresh();
    }

    boolean active() { return ready && !data.destroyed && !owner.isRemoved(); }
    private boolean networkThread() {
        return getWorld() != null && (getWorld().isClientSide || getWorld().getServer() != null
                && getWorld().getServer().isSameThread());
    }

    @Override public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        // The owner's link fields are read after behaviours; refresh on the following client tick.
        if (clientPacket) nextCheck = 0;
    }

    @Override public void tick() {
        if (!active() || !networkThread()) return;
        node.tickElectricity();
        if (getWorld().getGameTime() >= nextCheck) {
            nextCheck = getWorld().getGameTime() + 20;
            refresh();
            if (!getWorld().isClientSide) node.lazyTickElectricity();
        }
    }

    public void refresh() {
        if (!active() || !networkThread()) return;
        TfmgBehaviour desired = resolvePeer();
        List<IElectric> local = adjacent(node);
        boolean missing = !node.getOrCreateElectricNetwork().members.contains(node);
        boolean separated = desired != null && desired.data.getId() != data.getId()
                || local.stream().anyMatch(other -> other.getData().getId() != data.getId());
        var current = snapshot();
        if (peer == desired && neighbours.equals(local) && !missing && !separated && topology.equals(current)) return;
        var seeds = new ArrayList<IElectric>(neighbours);
        for (var entry : topology) seeds.add(entry.member());
        for (var entry : current) seeds.add(entry.member());
        seeds.add(node);
        if (peer != null) seeds.add(peer.node);
        peer = desired;
        if (peer != null) seeds.add(peer.node);
        seeds.addAll(local);
        neighbours = local;
        if (!getWorld().isClientSide) getWorld().invalidateCapabilities(getPos());
        rebuild(getWorld(), seeds);
        topology = snapshot();
    }

    private List<Topology> snapshot() {
        return List.copyOf(node.getOrCreateElectricNetwork().members).stream()
                .map(member -> {
                    boolean loaded = live(getWorld(), member);
                    return new Topology(member, member.getData().getId(), loaded,
                            loaded ? List.copyOf(connected(getWorld(), member)) : List.of());
                }).toList();
    }

    private TfmgBehaviour resolvePeer() {
        if (owner.getTargetPos() == null || !getWorld().isLoaded(owner.getTargetPos())) return null;
        var target = VSLinkUtil.resolveBlockEntity(getWorld(), owner.getTargetPos(), owner.getTargetSpaceId(),
                ElectricWireConnectorBlockEntity.class);
        if (!(target instanceof TfmgNode remote) || target == owner || target.isRemoved()
                || target.getBlockPos().equals(owner.getBlockPos())
                || target.getLevel() != getWorld() || !remote.tfmg().active()
                || target.getTargetPos() == null || !getWorld().isLoaded(target.getTargetPos())
                || VSLinkUtil.resolveBlockEntity(getWorld(), target.getTargetPos(), target.getTargetSpaceId(),
                        ElectricWireConnectorBlockEntity.class) != owner) return null;
        double distance = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
        return VSLinkUtil.getWorldPos(getWorld(), getPos()).distanceToSqr(
                VSLinkUtil.getWorldPos(getWorld(), target.getBlockPos())) <= distance * distance ? remote.tfmg() : null;
    }

    // Called during TFMG's own adjacency propagation; merge the existing member snapshots.
    void joinPeer() {
        var remote = resolvePeer();
        if (remote == null || data.getId() == remote.data.getId()) return;
        var network = node.getOrCreateElectricNetwork();
        var previous = remote.node.getOrCreateElectricNetwork();
        for (IElectric member : List.copyOf(previous.members)) {
            if (!live(getWorld(), member)) continue;
            if (!network.members.contains(member)) network.members.add(member);
            member.setNetwork(network.getId());
            member.updateNextTick();
        }
        node.updateNextTick();
    }

    @Override public void unload() { stop(); }
    public void stop() {
        if (!ready) return;
        ready = false;
        data.destroyed = true;
        var seeds = new ArrayList<IElectric>(neighbours);
        for (var entry : topology) seeds.add(entry.member());
        seeds.add(node);
        if (peer != null) seeds.add(peer.node);
        peer = null;
        topology = List.of();
        if (networkThread() && ElectricNetworkManager.networks.containsKey(getWorld())) rebuild(getWorld(), seeds);
    }

    private static boolean live(Level level, IElectric node) {
        if (node.getLevelAccessor() != level || node instanceof BlockEntity be && be.isRemoved()
                || !level.isLoaded(node.getPos())) return false;
        if (node instanceof TfmgNode terminal) return terminal.tfmg().active();
        if (level.getBlockEntity(node.getPos()) != node) return false;
        // CE marks an unwired, still placed terminal as destroyed; wiring does not reset it.
        // Revive only actual placed terminals with a wire to another actual placed terminal.
        if (node.destroyed() && node instanceof CableConnectorBlockEntity cable) {
            for (var connection : cable.connections) {
                var otherPos = connection.pos1().equals(cable.getPos()) ? connection.pos2() : connection.pos1();
                if (otherPos.equals(cable.getPos()) || !level.isLoaded(otherPos)) continue;
                if (level.getBlockEntity(otherPos) instanceof CableConnectorBlockEntity other
                        && other.getLevelAccessor() == level && !other.isRemoved()) {
                    node.getData().destroyed = false;
                    break;
                }
            }
        }
        return !node.destroyed();
    }

    private static List<IElectric> adjacent(IElectric node) {
        var result = new ArrayList<IElectric>();
        var level = (Level) node.getLevelAccessor();
        for (Direction side : Direction.values()) {
            var pos = node.getPos().relative(side);
            if (!node.hasElectricitySlot(side) || !level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof IElectric other && live(level, other)
                    && other.hasElectricitySlot(side.getOpposite())) result.add(other);
        }
        return result;
    }

    static boolean usesNativePort(ElectricWireConnectorBlockEntity owner, Direction side) {
        var level = owner.getLevel();
        // TFMG probes a specific face. Preserve unsided FE access for unrelated mods.
        if (level == null || side == null) return false;
        var pos = owner.getBlockPos().relative(side);
        // Check physical ports before their first tick, not just already registered networks.
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof IElectric other
                && other.getLevelAccessor() == level && other.hasElectricitySlot(side.getOpposite());
    }

    private static List<IElectric> connected(Level level, IElectric node) {
        var result = adjacent(node);
        if (node instanceof CableConnectorBlockEntity connector) {
            for (var cable : connector.connections) {
                var otherPos = cable.pos1().equals(connector.getPos()) ? cable.pos2() : cable.pos1();
                if (!level.isLoaded(otherPos)) continue;
                if (level.getBlockEntity(otherPos) instanceof CableConnectorBlockEntity other
                        && live(level, other)) result.add(other);
            }
        }
        if (node instanceof TfmgNode terminal) {
            var remote = terminal.tfmg().resolvePeer();
            if (remote != null) result.add(remote.node);
        }
        return result;
    }

    // Repartition only touched networks. Preserve TFMG's actual nodes, cable topology and solver.
    static void rebuild(Level level, Collection<IElectric> seeds) {
        var map = ElectricNetworkManager.networks.computeIfAbsent(level, ignored -> new HashMap<>());
        var affected = new LinkedHashSet<IElectric>(seeds);
        var old = new HashSet<ElectricalNetwork>();
        for (IElectric seed : seeds) {
            var network = map.get(seed.getData().getId());
            if (network != null) {
                affected.addAll(network.members);
                old.add(network);
            }
        }
        for (var network : old) {
            map.remove(network.getId(), network);
            // TFMG queues callbacks that capture this object and call members.getFirst().
            // Retire only the map entry; those callbacks resolve the node's new network.
        }
        var visited = Collections.newSetFromMap(new IdentityHashMap<IElectric, Boolean>());
        var rebuilt = new ArrayList<ElectricalNetwork>();
        for (IElectric seed : affected) {
            if (!live(level, seed) || visited.contains(seed)) continue;
            var component = new ArrayList<IElectric>();
            var queue = new ArrayDeque<IElectric>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                var member = queue.removeFirst();
                if (!live(level, member) || !visited.add(member)) continue;
                component.add(member);
                queue.addAll(connected(level, member));
            }
            var network = new TfmgElectricalNetwork(seed.getPos().asLong());
            for (IElectric member : component) {
                member.setNetwork(network.getId());
                member.getData().updateNextTick = false;
                network.members.add(member);
            }
            rebuilt.add(network);
        }
        // All IDs must be resolvable before the standard current/power calculation runs.
        for (var network : rebuilt) map.put(network.getId(), network);
        for (var network : rebuilt) {
            network.updateNetwork();
            for (IElectric member : network.members) member.sendStuff();
        }
        for (var network : rebuilt) {
            for (IElectric member : network.members) {
                if (member instanceof TfmgNode terminal) {
                    var behaviour = terminal.tfmg();
                    behaviour.peer = behaviour.resolvePeer();
                    behaviour.neighbours = adjacent(member);
                    behaviour.topology = behaviour.snapshot();
                }
            }
        }
    }
}
