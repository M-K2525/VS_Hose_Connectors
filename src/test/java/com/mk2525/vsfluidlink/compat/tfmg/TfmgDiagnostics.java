package com.mk2525.vsfluidlink.compat.tfmg;

import com.drmangotea.tfmg.content.electricity.base.ElectricNetworkManager;
import com.drmangotea.tfmg.content.electricity.base.ElectricalNetwork;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.connection.cables.CableConnectorBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/** Test-only snapshots; never packaged in the mod JAR. */
final class TfmgDiagnostics {
    private TfmgDiagnostics() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vsfluidlink").requires(source -> source.hasPermission(2))
                .then(Commands.literal("tfmgdebug")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> {
                            var source = context.getSource();
                            var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
                            if (!(source.getLevel().getBlockEntity(pos) instanceof IElectric)) {
                                source.sendFailure(Component.literal("No TFMG electrical node at " + pos.toShortString()));
                                return 0;
                            }
                            var lines = report(source.getLevel(), pos);
                            for (var line : lines) LogUtils.getLogger().info("[VSFL-TFMG] {}", line);
                            source.sendSuccess(() -> Component.literal("TFMG snapshot: " + lines.size()
                                    + " lines written to logs/latest.log"), false);
                            return 1;
                        }))));
    }

    static List<String> report(Level level, BlockPos pos) {
        var lines = new ArrayList<String>();
        var registrations = ElectricNetworkManager.networks.getOrDefault(level, Collections.emptyMap());
        var selected = Collections.newSetFromMap(new IdentityHashMap<IElectric, Boolean>());
        if (level.getBlockEntity(pos) instanceof IElectric node) selected.add(node);
        for (var side : Direction.values())
            if (level.isLoaded(pos.relative(side)) && level.getBlockEntity(pos.relative(side)) instanceof IElectric node)
                selected.add(node);
        var networks = Collections.newSetFromMap(new IdentityHashMap<ElectricalNetwork, Boolean>());
        for (var entry : registrations.entrySet()) {
            if (selected.stream().anyMatch(node -> node.getData().getId() == entry.getKey()
                    || entry.getValue().members.contains(node))) networks.add(entry.getValue());
        }
        lines.add("side=" + (level.isClientSide ? "client" : "server")
                + " time=" + level.getGameTime() + " dimension=" + level.dimension().location()
                + " target=" + pos.toShortString() + " networks=" + networks.size());
        for (var network : networks) {
            lines.add("network=" + network.getId() + " members=" + network.members.size());
            selected.addAll(network.members);
        }
        int count = 0;
        for (var node : selected) {
            if (++count > 256) {
                lines.add("TRUNCATED after 256 nodes");
                break;
            }
            var data = node.getData();
            var registered = registrations.get(data.getId());
            var rootPos = BlockPos.of(data.getId());
            var root = level.isLoaded(rootPos) ? level.getBlockEntity(rootPos) : null;
            var ports = java.util.EnumSet.noneOf(Direction.class);
            for (var side : Direction.values())
                if (node.hasElectricitySlot(side)) ports.add(side);
            lines.add("node=" + node.getClass().getSimpleName() + " pos=" + node.getPos().toShortString()
                    + " id=" + data.getId() + " rootId=" + (root instanceof IElectric electric ? electric.getData().getId() : "missing")
                    + " registered=" + (registered != null && registered.members.contains(node))
                    + " placed=" + (level.isLoaded(node.getPos()) && level.getBlockEntity(node.getPos()) == node)
                    + " destroyed=" + data.destroyed + " voltage=" + data.getVoltage()
                    + " sourceVoltage=" + data.lastVoltageGeneration + " sourcePower=" + data.lastPowerGeneration
                    + " networkResistance=" + data.networkResistance + " load=" + data.lastPowerUsage
                    + " cachedGeneration=" + data.networkPowerGeneration + " insufficient=" + data.notEnoughPower
                    + " feOutputs=" + data.energyOutputs.keySet() + " feGiven=" + data.energyGiven
                    + " feTaken=" + data.energyTaken + " updatePending=" + data.updateNextTick);
            if (level.isLoaded(node.getPos()))
                lines.add("state=" + level.getBlockState(node.getPos()) + " ports=" + ports);
            if (node instanceof TfmgNode terminal)
                lines.add("link=" + terminal.owner().getTargetPos() + " space=" + terminal.owner().getTargetSpaceId());
            if (node instanceof CableConnectorBlockEntity cable)
                lines.add("wires=" + cable.connections);
        }
        return List.copyOf(lines);
    }
}
