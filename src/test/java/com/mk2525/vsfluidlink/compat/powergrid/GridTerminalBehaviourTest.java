package com.mk2525.vsfluidlink.compat.powergrid;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GridTerminalBehaviourTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        try (var loading = mockStatic(LoadingModList.class)) {
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loading.when(LoadingModList::get).thenReturn(mods);
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            var config = CommentedConfig.inMemory();
            VsFluidLinkConfig.SERVER_SPEC.correct(config);
            var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                    .getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            VsFluidLinkConfig.SERVER_SPEC.acceptConfig((IConfigSpec.ILoadedConfig)
                    constructor.newInstance(config, null, null));
        }
    }

    static final class Connector extends ElectricWireConnectorBlockEntity implements GridTerminal {
        Connector(BlockPos pos) { super(BlockEntityType.CHEST, pos, Blocks.CHEST.defaultBlockState()); }
        public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
            behaviours.add(new GridTerminalBehaviour(this));
        }
        GridTerminalBehaviour grid() { return (GridTerminalBehaviour) getBehaviour(ElectricBehaviour.TYPE); }
    }

    private static final class Harness implements AutoCloseable {
        final Level level = mock(Level.class);
        final WorldNetworks manager = mock(WorldNetworks.class);
        final ElectricalNetwork network = new ElectricalNetwork(false, null);
        final List<ElectricWire> created = new ArrayList<>();
        final List<Connector> connectors = new ArrayList<>();
        final MockedStatic<GlobalElectricNetworks> globals = mockStatic(GlobalElectricNetworks.class);
        final MockedStatic<ModList> mods = mockStatic(ModList.class);

        Harness() {
            mods.when(ModList::get).thenReturn(mock(ModList.class));
            var server = mock(MinecraftServer.class);
            when(level.getServer()).thenReturn(server);
            when(server.isSameThread()).thenReturn(true);
            when(level.isLoaded(any())).thenReturn(true);
            globals.when(() -> GlobalElectricNetworks.getWorldNetworks(level)).thenReturn(manager);
            globals.when(() -> GlobalElectricNetworks.makeSimpleConnection(eq(level), any(), any(), anyFloat()))
                    .thenAnswer(call -> {
                        BlockWireEndpoint a = call.getArgument(1), b = call.getArgument(2);
                        var first = connectors.stream().filter(c -> c.getBlockPos().equals(a.getPos())).findFirst().orElseThrow();
                        var second = connectors.stream().filter(c -> c.getBlockPos().equals(b.getPos())).findFirst().orElseThrow();
                        var n1 = first.grid().getTerminal(0);
                        var n2 = second.grid().getTerminal(0);
                        network.addNode(n1);
                        network.addNode(n2);
                        var wire = new ElectricWire((Float) call.getArgument(3), n1, n2);
                        network.addWire(wire);
                        created.add(wire);
                        return wire;
                    });
        }

        Connector connector(int x) {
            var connector = new Connector(new BlockPos(x, 1, 1));
            connectors.add(connector);
            connector.setLevel(level);
            connector.grid().initialize();
            return connector;
        }

        public void close() {
            try {
                connectors.forEach(c -> c.grid().remove());
            } finally {
                globals.close();
                mods.close();
            }
        }
    }

    @Test
    void bothEndpointsShareOneWireAndRepeatedChecksDoNotDuplicateIt() {
        try (var h = new Harness()) {
            var a = h.connector(1);
            var b = h.connector(2);
            a.grid().connect(b.grid());
            b.grid().connect(a.grid());
            a.grid().connect(b.grid());
            assertEquals(1, h.created.size());
            assertEquals(GridTerminalBehaviour.LINK_RESISTANCE, h.created.getFirst().getResistance());
            b.grid().connect(null);
            assertNull(h.created.getFirst().getNetwork());
            verify(h.manager).scheduleIslandDiscovery(h.network);
            a.grid().connect(b.grid());
            assertEquals(2, h.created.size());
        }
    }

    @Test
    void unloadEitherEndStopsTransferAndReloadRecreatesOnlyOneWire() {
        try (var h = new Harness()) {
            var a = h.connector(1);
            var b = h.connector(2);
            a.grid().connect(b.grid());
            b.grid().unload();
            assertNull(h.created.getFirst().getNetwork());
            a.grid().connect(b.grid());
            assertEquals(1, h.created.size());
            var reloaded = h.connector(3);
            a.grid().connect(reloaded.grid());
            reloaded.grid().connect(a.grid());
            assertEquals(2, h.created.size());
            a.grid().remove();
            assertNull(h.created.getLast().getNetwork());
        }
    }

    @Test
    void retargetDropsOldWireBeforeMakingAnother() {
        try (var h = new Harness()) {
            var a = h.connector(1);
            var b = h.connector(2);
            var c = h.connector(3);
            a.grid().connect(b.grid());
            a.grid().connect(c.grid());
            assertNull(h.created.getFirst().getNetwork());
            assertNotNull(h.created.getLast().getNetwork());
            b.grid().remove();
            assertNotNull(h.created.getLast().getNetwork());
        }
    }

    @Test
    void separateLevelsAndSelfConnectionsAreRejected() {
        try (var h = new Harness()) {
            var a = h.connector(1);
            var b = h.connector(2);
            a.grid().connect(a.grid());
            b.setLevel(mock(Level.class));
            a.grid().connect(b.grid());
            assertTrue(h.created.isEmpty());
        }
    }

    @Test
    void linkRefreshRequiresReciprocalSpatialIdentityAndLoadedEndpoints() {
        try (var h = new Harness(); var spatial = mockStatic(VSLinkUtil.class)) {
            var a = h.connector(1);
            var b = h.connector(2);
            a.setTarget(b.getBlockPos(), 22L);
            b.setTarget(a.getBlockPos(), 11L);
            spatial.when(() -> VSLinkUtil.resolveBlockEntity(h.level, b.getBlockPos(), 22L,
                    ElectricWireConnectorBlockEntity.class)).thenReturn(b);
            spatial.when(() -> VSLinkUtil.resolveBlockEntity(h.level, a.getBlockPos(), 11L,
                    ElectricWireConnectorBlockEntity.class)).thenReturn(a);
            spatial.when(() -> VSLinkUtil.getWorldPos(h.level, a.getBlockPos())).thenReturn(Vec3.ZERO);
            spatial.when(() -> VSLinkUtil.getWorldPos(h.level, b.getBlockPos())).thenReturn(new Vec3(1, 0, 0));
            a.grid().linkChanged();
            assertEquals(1, h.created.size());
            // Same position but a different spatial identity must not be accepted.
            var wrongIdentity = h.connector(3);
            spatial.when(() -> VSLinkUtil.resolveBlockEntity(h.level, a.getBlockPos(), 11L,
                    ElectricWireConnectorBlockEntity.class)).thenReturn(wrongIdentity);
            a.grid().linkChanged();
            assertNull(h.created.getFirst().getNetwork());
            when(h.level.isLoaded(b.getBlockPos())).thenReturn(false);
            a.grid().linkChanged();
            assertEquals(1, h.created.size());
        }
    }

    @Test
    void blockEntityChunkUnloadImmediatelyRemovesVirtualWire() {
        try (var h = new Harness()) {
            var a = h.connector(1);
            var b = h.connector(2);
            a.grid().connect(b.grid());
            b.onChunkUnloaded();
            assertNull(h.created.getFirst().getNetwork());
            a.grid().connect(b.grid());
            assertEquals(1, h.created.size());
        }
    }

    @Test
    void optionalEntryPointCanRunWithPowerGridClassesHidden() throws Exception {
        try (var h = new Harness()) {
            var owner = h.connector(1);
            String facade = PowerGridCompat.class.getName();
            var isolated = new ClassLoader(getClass().getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("org.patryk3211.powergrid.")
                            || name.startsWith("com.mk2525.vsfluidlink.compat.powergrid.Grid")) {
                        throw new ClassNotFoundException(name);
                    }
                    if (!name.equals(facade)) return super.loadClass(name, resolve);
                    var existing = findLoadedClass(name);
                    if (existing != null) return existing;
                    try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] bytes = input.readAllBytes();
                        var type = defineClass(name, bytes, 0, bytes.length);
                        if (resolve) resolveClass(type);
                        return type;
                    } catch (java.io.IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
            };
            var entry = Class.forName(facade, true, isolated);
            entry.getMethod("linkChanged", ElectricWireConnectorBlockEntity.class).invoke(null, owner);
            entry.getMethod("remove", ElectricWireConnectorBlockEntity.class).invoke(null, owner);
        }
    }
}
