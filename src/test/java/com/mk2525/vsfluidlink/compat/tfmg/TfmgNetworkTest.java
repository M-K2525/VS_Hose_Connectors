package com.mk2525.vsfluidlink.compat.tfmg;

import com.drmangotea.tfmg.content.electricity.base.*;
import com.drmangotea.tfmg.content.electricity.connection.cables.*;
import com.drmangotea.tfmg.content.electricity.connection.cable_type.CableType;
import com.drmangotea.tfmg.content.electricity.lights.LightBulbBlockEntity;
import com.drmangotea.tfmg.content.electricity.lights.LightBulbBlock;
import com.drmangotea.tfmg.content.electricity.utilities.electric_motor.ElectricMotorBlockEntity;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlock;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TfmgNetworkTest {
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


    static final class Connector extends ElectricWireConnectorBlockEntity implements TfmgNode {
        Connector(BlockPos pos) { super(BlockEntityType.CHEST, pos, Blocks.CHEST.defaultBlockState()); }
        @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
            behaviours.add(new TfmgBehaviour(this));
        }
        @Override public void sendStuff() {}
    }

    static final class Machine extends SmartBlockEntity implements IElectric {
        final ElectricBlockValues data;
        final int voltage;
        int generation;
        final float resistance;
        Machine(BlockPos pos, int voltage, int generation, float resistance) {
            super(BlockEntityType.CHEST, pos, Blocks.CHEST.defaultBlockState());
            data = new ElectricBlockValues(pos.asLong());
            this.voltage = voltage;
            this.generation = generation;
            this.resistance = resistance;
        }
        @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
        @Override public BlockPos getPos() { return getBlockPos(); }
        @Override public Level getLevelAccessor() { return getLevel(); }
        @Override public ElectricBlockValues getData() { return data; }
        @Override public void sendStuff() {}
        @Override public int voltageGeneration() { return voltage; }
        @Override public float powerGeneration() { return generation; }
        @Override public float resistance() { return resistance; }
        @Override public ElectricalNetwork getOrCreateElectricNetwork() {
            return TfmgBehaviour.MANAGER.getOrCreateNetworkFor(this);
        }
    }

    static final class Magnet extends ElectricMagnetWireConnectorBlockEntity implements TfmgNode {
        Magnet(BlockPos pos, Direction facing, ElectricMagnetWireConnectorBlock block) {
            super(validType(), pos, magnetState(block, facing, false, false));
        }
        private static BlockState magnetState(ElectricMagnetWireConnectorBlock block, Direction facing,
                boolean linked, boolean powered) {
            var state = mock(BlockState.class);
            when(state.getBlock()).thenReturn(block);
            when(state.getValue(ElectricMagnetWireConnectorBlock.FACING)).thenReturn(facing);
            when(state.getValue(ElectricMagnetWireConnectorBlock.POWERED)).thenReturn(powered);
            when(state.hasProperty(ElectricMagnetWireConnectorBlock.LINKED)).thenReturn(true);
            when(state.getValue(ElectricMagnetWireConnectorBlock.LINKED)).thenReturn(linked);
            when(state.setValue(eq(ElectricMagnetWireConnectorBlock.LINKED), anyBoolean()))
                    .thenAnswer(call -> magnetState(block, facing, call.getArgument(1), powered));
            return state;
        }
        private static BlockEntityType<?> validType() {
            var type = mock(BlockEntityType.class);
            when(type.isValid(any())).thenReturn(true);
            return type;
        }
        @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
            behaviours.add(new TfmgBehaviour(this));
        }
        @Override public void sendStuff() {}
    }

    static final class Cable extends CableConnectorBlockEntity {
        Direction slot;
        Cable(BlockPos pos) {
            super(BlockEntityType.CHEST, pos, Blocks.CHEST.defaultBlockState());
            // These fixtures initialize topology explicitly, without TFMG placement/FE setup.
            getData().connectNextTick = false;
        }
        @Override public boolean hasElectricitySlot(net.minecraft.core.Direction side) { return slot == null || side == slot; }
        @Override public void lazyTick() {
            // Output-mode fixture: native electrical checks, without a registered TFMG block state.
            lazyTickElectricity();
            checkForFEOutputs(List.of(Direction.DOWN));
        }
        @Override public void sendStuff() {}
        @Override public ElectricalNetwork getOrCreateElectricNetwork() {
            return TfmgBehaviour.MANAGER.getOrCreateNetworkFor(this);
        }
    }

    static final class Lamp extends LightBulbBlockEntity {
        Lamp(BlockPos pos) {
            super(lampType(), pos, Blocks.LIGHT.defaultBlockState());
            data.connectNextTick = false;
        }
        private static BlockEntityType<?> lampType() {
            var type = mock(BlockEntityType.class);
            when(type.isValid(any())).thenReturn(true);
            return type;
        }
        @Override public boolean hasElectricitySlot(Direction side) { return true; }
        @Override public void lazyTick() {}
        @Override public void sendStuff() {}
        @Override public ElectricalNetwork getOrCreateElectricNetwork() {
            return TfmgBehaviour.MANAGER.getOrCreateNetworkFor(this);
        }
    }

    static final class Motor extends ElectricMotorBlockEntity {
        @SuppressWarnings("unchecked")
        Motor(BlockPos pos) {
            super(BlockEntityType.CHEST, pos, Blocks.CHEST.defaultBlockState());
            data.connectNextTick = false;
            movementDirection = mock(ScrollOptionBehaviour.class);
            when(movementDirection.get()).thenReturn(WindmillBearingBlockEntity.RotationDirection.CLOCKWISE);
        }
        @Override public boolean hasElectricitySlot(Direction side) { return true; }
        @Override public float resistance() { return 30; }
        @Override public void notifyUpdate() {}
        @Override public void sendStuff() {}
        @Override public void updateGeneratedRotation() {
            if (!getLevel().isClientSide) setSpeed(getGeneratedSpeed());
        }
        void flushRotation() {
            // ElectricMotorBlockEntity.tick's deferred output update, without Create world propagation.
            if (delayedUpdate) {
                updateGeneratedRotation();
                delayedUpdate = false;
            }
        }
        @Override public ElectricalNetwork getOrCreateElectricNetwork() {
            return TfmgBehaviour.MANAGER.getOrCreateNetworkFor(this);
        }
    }

    static final class Harness implements AutoCloseable {
        final Level level = mock(Level.class);
        final Map<BlockPos, BlockEntity> entities = new HashMap<>();
        final MockedStatic<ModList> mods = mockStatic(ModList.class);
        final MockedStatic<VSLinkUtil> spatial = mockStatic(VSLinkUtil.class);
        Harness() { this(false); }
        Harness(boolean client) {
            try {
                var side = Level.class.getField("isClientSide");
                side.setAccessible(true);
                side.setBoolean(level, client);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            when(level.isClientSide()).thenReturn(client);
            mods.when(ModList::get).thenReturn(mock(ModList.class));
            var server = mock(MinecraftServer.class);
            when(level.getServer()).thenReturn(server);
            when(server.isSameThread()).thenReturn(true);
            when(level.isLoaded(any())).thenReturn(true);
            when(level.getBlockEntity(any())).thenAnswer(call -> entities.get(call.getArgument(0)));
            when(level.getBlockState(any())).thenAnswer(call -> {
                var be = entities.get(call.getArgument(0));
                return be == null ? Blocks.AIR.defaultBlockState() : be.getBlockState();
            });
            when(level.setBlock(any(), any(), anyInt())).thenAnswer(call -> {
                var be = entities.get(call.getArgument(0));
                if (be != null) be.setBlockState(call.getArgument(1));
                return true;
            });
            spatial.when(() -> VSLinkUtil.resolveBlockEntity(eq(level), any(), any(),
                    eq(ElectricWireConnectorBlockEntity.class)))
                    .thenAnswer(call -> entities.get(call.getArgument(1)));
            spatial.when(() -> VSLinkUtil.resolveBlockEntity(eq(level), any(), any(),
                    eq(ElectricMagnetWireConnectorBlockEntity.class)))
                    .thenAnswer(call -> entities.get(call.getArgument(1)));
            spatial.when(() -> VSLinkUtil.getWorldTransform(eq(level), any(), any()))
                    .thenAnswer(call -> new VSLinkUtil.WorldTransform(Vec3.atCenterOf((BlockPos) call.getArgument(1)),
                            Vec3.atLowerCornerOf(((Direction) call.getArgument(2)).getNormal())));
            spatial.when(() -> VSLinkUtil.getWorldPos(eq(level), any()))
                    .thenAnswer(call -> Vec3.atCenterOf((BlockPos) call.getArgument(1)));
        }
        <T extends BlockEntity> T put(T be) {
            be.setLevel(level);
            entities.put(be.getBlockPos(), be);
            return be;
        }
        void enableTFMG() {
            var container = mock(net.neoforged.fml.ModContainer.class);
            var info = mock(net.neoforged.neoforgespi.language.IModInfo.class);
            when(container.getModInfo()).thenReturn(info);
            when(info.getVersion()).thenReturn(new org.apache.maven.artifact.versioning.DefaultArtifactVersion("1.3.2a-community"));
            when(ModList.get().getModContainerById("tfmg")).thenAnswer(call -> Optional.of(container));
        }
        Connector connector(int x) {
            var be = put(new Connector(new BlockPos(x, 1, 1)));
            be.tfmg().initialize();
            return be;
        }
        Machine machine(int x, int voltage, int generation, float resistance) {
            return put(new Machine(new BlockPos(x, 1, 1), voltage, generation, resistance));
        }
        void link(Connector a, Connector b) {
            a.setTarget(b.getBlockPos(), null);
            b.setTarget(a.getBlockPos(), null);
            a.tfmg().refresh();
            b.tfmg().refresh();
        }
        @Override public void close() {
            try { ElectricNetworkManager.networks.remove(level); }
            finally { spatial.close(); mods.close(); }
        }
    }

    @Test void clientLampStaysLitAcrossNativeNetworkUpdatesAndDisconnects() {
        try (var h = new Harness(true)) {
            h.machine(0, 120, 2000, 0);
            var a = h.connector(1);
            var b = h.connector(4);
            var lamp = h.put(new Lamp(new BlockPos(5, 1, 1)));
            h.link(a, b);
            // The server's block-state update can light the lamp once, but client ticks must sustain it.
            lamp.getData().voltage = 120;
            for (int tick = 0; tick < 40; tick++) {
                when(h.level.getGameTime()).thenReturn((long) tick);
                a.tfmg().tick();
                b.tfmg().tick();
                lamp.updateNetwork();
                lamp.tick();
                assertEquals(12, lamp.getBlockState().getValue(LightBulbBlock.LIGHT), "tick " + tick);
            }
            b.tfmg().read(new net.minecraft.nbt.CompoundTag(), null, true);
            b.setTarget(null, null);
            b.tfmg().tick();
            lamp.tick();
            assertEquals(0, lamp.getBlockState().getValue(LightBulbBlock.LIGHT));
            h.link(a, b);
            lamp.tick();
            assertEquals(12, lamp.getBlockState().getValue(LightBulbBlock.LIGHT));
        }
    }

    @Test void generatorMotorAndParallelLampStayPoweredAcrossPeriodicChecks() {
        for (boolean client : List.of(false, true)) {
            try (var h = new Harness(client)) {
                var generator = h.machine(0, 255, 12080, 100000);
                var a = h.connector(1);
                var b = h.connector(4);
                var motor = h.put(new Motor(new BlockPos(5, 1, 1)));
                var lamp = h.put(new Lamp(new BlockPos(4, 2, 1)));
                h.link(a, b);
                for (int tick = 0; tick < 120; tick++) {
                    when(h.level.getGameTime()).thenReturn((long) tick);
                    generator.tickElectricity();
                    a.tfmg().tick();
                    b.tfmg().tick();
                    motor.tickElectricity();
                    motor.flushRotation();
                    lamp.tick();
                    assertEquals(204, motor.getGeneratedSpeed(), "motor tick " + tick + ", client=" + client);
                    if (!client) assertEquals(204, motor.getTheoreticalSpeed(), "output tick " + tick);
                    assertEquals(15, lamp.getBlockState().getValue(LightBulbBlock.LIGHT), "lamp tick " + tick);
                    assertFalse(motor.getData().notEnoughPower);
                    assertEquals(5, motor.getOrCreateElectricNetwork().members.size());
                }
            }
        }
    }

    @Test void motorRestartsWhenPowerRecoversWithoutVoltageChange() {
        try (var h = new Harness()) {
            var source = h.machine(0, 255, 12080, 100000);
            var a = h.connector(1);
            var b = h.connector(4);
            var motor = h.put(new Motor(new BlockPos(5, 1, 1)));
            var lamp = h.put(new Lamp(new BlockPos(4, 2, 1)));
            h.link(a, b);
            motor.flushRotation();
            assertEquals(204, motor.getTheoreticalSpeed());
            for (int cycle = 0; cycle < 3; cycle++) {
                source.generation = 1000;
                source.updateNetwork();
                motor.flushRotation();
                lamp.tick();
                assertEquals(0, motor.getTheoreticalSpeed());
                assertEquals(0, lamp.getBlockState().getValue(LightBulbBlock.LIGHT));
                source.generation = 12080;
                source.updateNetwork();
                motor.flushRotation();
                lamp.tick();
                assertEquals(255, motor.getData().getVoltage());
                assertEquals(204, motor.getGeneratedSpeed());
                assertEquals(15, lamp.getBlockState().getValue(LightBulbBlock.LIGHT));
                assertEquals(204, motor.getTheoreticalSpeed(), "restart cycle " + cycle);
            }
        }
    }

    @Test void nativeWiredMotorAndLampStayPoweredAcrossTerminalTicks() {
        for (boolean client : List.of(false, true)) {
            try (var h = new Harness(client)) {
                var input = h.put(new Cable(new BlockPos(1, 2, 1)));
                var output = h.put(new Cable(new BlockPos(4, 2, 1)));
                var generatorTerminal = h.put(new Cable(new BlockPos(1, 4, 1)));
                var loadTerminal = h.put(new Cable(new BlockPos(4, 4, 1)));
                var source = h.put(new Machine(new BlockPos(0, 4, 1), 255, 12080, 100000));
                var motor = h.put(new Motor(new BlockPos(5, 4, 1)));
                var lamp = h.put(new Lamp(new BlockPos(4, 5, 1)));
                var a = h.connector(1);
                var b = h.connector(4);
                var type = mock(CableType.class);
                var inputWire = new CableConnection(input.getPos(), generatorTerminal.getPos(), type, true);
                var outputWire = new CableConnection(output.getPos(), loadTerminal.getPos(), type, true);
                input.connections.add(inputWire);
                generatorTerminal.connections.add(inputWire);
                output.connections.add(outputWire);
                loadTerminal.connections.add(outputWire);
                h.link(a, b);
                for (int tick = 0; tick < 120; tick++) {
                    when(h.level.getGameTime()).thenReturn((long) tick);
                    source.tickElectricity();
                    for (var cable : List.of(input, output, generatorTerminal, loadTerminal)) cable.tick();
                    a.tfmg().tick();
                    b.tfmg().tick();
                    motor.tickElectricity();
                    motor.flushRotation();
                    lamp.tick();
                    assertEquals(204, motor.getGeneratedSpeed(), "motor tick " + tick + ", client=" + client);
                    if (!client) assertEquals(204, motor.getTheoreticalSpeed(), "output tick " + tick);
                    assertEquals(15, lamp.getBlockState().getValue(LightBulbBlock.LIGHT), "lamp tick " + tick);
                    assertEquals(9, motor.getOrCreateElectricNetwork().members.size());
                }
            }
        }
    }

    @Test void automaticMagnetConnectionPublishesOnlyReciprocalLinksAndPowersMotor() {
        try (var h = new Harness()) {
            h.enableTFMG();
            var block = mock(ElectricMagnetWireConnectorBlock.class);
            var a = h.put(new Magnet(new BlockPos(1, 1, 1), Direction.EAST, block));
            var b = h.put(new Magnet(new BlockPos(4, 1, 1), Direction.WEST, block));
            h.machine(0, 255, 12080, 100000);
            var motor = h.put(new Motor(new BlockPos(5, 1, 1)));
            var lamp = h.put(new Lamp(new BlockPos(4, 2, 1)));
            h.spatial.when(() -> VSLinkUtil.getSpatialId(h.level, a.getBlockPos())).thenReturn(101L);
            h.spatial.when(() -> VSLinkUtil.getSpatialId(h.level, b.getBlockPos())).thenReturn(102L);
            a.onLoad();
            b.onLoad();
            doAnswer(call -> {
                BlockState sentState = call.getArgument(2);
                if (sentState.getValue(ElectricMagnetWireConnectorBlock.LINKED)) {
                    assertEquals(b.getBlockPos(), a.getTargetPos());
                    assertEquals(a.getBlockPos(), b.getTargetPos());
                    assertEquals(102L, a.getTargetSpaceId());
                    assertEquals(101L, b.getTargetSpaceId());
                }
                return null;
            }).when(h.level).sendBlockUpdated(any(), any(), any(), anyInt());
            try {
                for (int tick = 1; tick <= 120; tick++) {
                    when(h.level.getGameTime()).thenReturn((long) tick);
                    if (tick == 41 || tick == 66)
                        b.setBlockState(Magnet.magnetState(block, Direction.WEST, b.getTargetPos() != null, tick == 41));
                    ElectricMagnetWireConnectorBlockEntity.tick(h.level, a.getBlockPos(), a.getBlockState(), a);
                    ElectricMagnetWireConnectorBlockEntity.tick(h.level, b.getBlockPos(), b.getBlockState(), b);
                    motor.tickElectricity();
                    motor.flushRotation();
                    lamp.tick();
                    if (tick <= 40 || tick >= 86) {
                        assertEquals(204, motor.getTheoreticalSpeed(), "magnet tick " + tick);
                        assertEquals(15, lamp.getBlockState().getValue(LightBulbBlock.LIGHT));
                    } else if (tick >= 44 && tick <= 65) {
                        assertEquals(0, motor.getTheoreticalSpeed(), "disconnected tick " + tick);
                        assertEquals(0, lamp.getBlockState().getValue(LightBulbBlock.LIGHT));
                    }
                }
                verify(h.level, atLeastOnce()).sendBlockUpdated(any(), any(), any(), anyInt());
            } finally {
                a.invalidate();
                b.invalidate();
            }
        }
    }

    @Test void standardWiresAndVirtualLinkCarryNativePowerWithoutFEConversion() {
        try (var h = new Harness()) {
            h.enableTFMG();
            var input = h.put(new Cable(new BlockPos(1, 2, 1)));
            var output = h.put(new Cable(new BlockPos(4, 2, 1)));
            var generatorTerminal = h.put(new Cable(new BlockPos(1, 4, 1)));
            var loadTerminal = h.put(new Cable(new BlockPos(4, 4, 1)));
            for (var cable : List.of(input, output, generatorTerminal, loadTerminal)) cable.onRemoved();
            var source = h.put(new Machine(new BlockPos(0, 4, 1), 120, 2000, 0));
            var load = h.put(new Machine(new BlockPos(5, 4, 1), 0, 0, 10));
            var a = h.connector(1);
            var b = h.connector(4);
            h.link(a, b);
            assertEquals(0, load.getData().getVoltage());
            var type = mock(CableType.class);
            var inputWire = new CableConnection(input.getPos(), generatorTerminal.getPos(), type, true);
            var outputWire = new CableConnection(output.getPos(), loadTerminal.getPos(), type, true);
            input.connections.add(inputWire);
            generatorTerminal.connections.add(inputWire);
            output.connections.add(outputWire);
            loadTerminal.connections.add(outputWire);
            a.tfmg().refresh();
            assertSame(source.getOrCreateElectricNetwork(), load.getOrCreateElectricNetwork());
            assertEquals(120, load.getData().getVoltage());
            assertEquals(12, load.getCurrent(), 0.001);
            assertFalse(input.destroyed());
            assertFalse(output.destroyed());
            assertNull(a.getEnergyCapability(Direction.UP));
            assertSame(a.getEnergyStorage(), a.getEnergyCapability(null));
            assertSame(a.getEnergyStorage(), a.getEnergyCapability(Direction.EAST));
            when(h.level.getCapability(eq(Capabilities.EnergyStorage.BLOCK), any(BlockPos.class), any(), any(), any()))
                    .thenAnswer(call -> {
                        var be = h.entities.get(call.getArgument(1));
                        return be instanceof ElectricWireConnectorBlockEntity owner
                                ? owner.getEnergyCapability(call.getArgument(4)) : null;
                    });
            input.checkForFEOutputs(List.of(Direction.DOWN));
            output.checkForFEOutputs(List.of(Direction.DOWN));
            assertTrue(input.getData().energyOutputs.isEmpty());
            assertTrue(output.getData().energyOutputs.isEmpty());
            for (int tick = 0; tick < 3; tick++) {
                for (var be : List.copyOf(h.entities.values()))
                    if (be instanceof IElectric electric) electric.tickElectricity();
            }
            assertEquals(120, load.getData().getVoltage());
            assertEquals(0, a.getEnergyStorage().getEnergyStored());
            assertEquals(0, b.getEnergyStorage().getEnergyStored());
            b.setTarget(null, null);
            assertEquals(0, load.getData().getVoltage());
            h.link(a, b);
            assertEquals(120, load.getData().getVoltage());
            h.entities.remove(input.getPos());
            a.tfmg().refresh();
            assertSame(a.getEnergyStorage(), a.getEnergyCapability(Direction.UP));
            assertEquals(0, load.getData().getVoltage());
        }
    }

    @Test void nonPortFacesAndMissingTFMGKeepExistingFEAccess() {
        try (var h = new Harness()) {
            var a = h.connector(1);
            var cable = h.put(new Cable(new BlockPos(1, 2, 1)));
            assertSame(a.getEnergyStorage(), a.getEnergyCapability(Direction.UP));
            h.enableTFMG();
            cable.slot = Direction.UP;
            assertSame(a.getEnergyStorage(), a.getEnergyCapability(Direction.UP));
            cable.slot = Direction.DOWN;
            assertNull(a.getEnergyCapability(Direction.UP));
            for (Direction side : Direction.values())
                if (side != Direction.UP) assertSame(a.getEnergyStorage(), a.getEnergyCapability(side));
        }
    }

    @Test void attachingStandardTerminalPreservesPendingNativeActions() {
        try (var h = new Harness()) {
            var cable = h.put(new Cable(new BlockPos(1, 2, 1)));
            var retired = cable.getOrCreateElectricNetwork();
            retired.updateNetwork();
            assertFalse(cable.getData().scheduledActions.isEmpty());

            var connector = h.connector(1);
            assertNotSame(retired, cable.getOrCreateElectricNetwork());
            assertSame(connector.getOrCreateElectricNetwork(), cable.getOrCreateElectricNetwork());
            // Execute TFMG's actual deferred resistance calculation after the topology changes.
            assertDoesNotThrow(cable::tickElectricity);
            assertTrue(cable.getData().scheduledActions.isEmpty());
            assertFalse(ElectricNetworkManager.networks.get(h.level).containsValue(retired));
        }
    }

    @Test void repeatedTopologyChangesPreserveNativeActionsUntilNextTick() {
        try (var h = new Harness()) {
            h.machine(0, 120, 2000, 0);
            var a = h.connector(1);
            var b = h.connector(4);
            var load = h.machine(5, 0, 0, 10);
            var cable = h.put(new Cable(new BlockPos(1, 2, 1)));
            h.link(a, b);
            var retired = cable.getOrCreateElectricNetwork();
            retired.updateNetwork();
            b.setTarget(null, null);
            b.tfmg().refresh();
            h.link(a, b);
            a.onChunkUnloaded();
            assertEquals(0, load.getData().getVoltage());

            for (var member : List.copyOf(h.entities.values())) {
                if (member instanceof IElectric electric && !electric.destroyed())
                    assertDoesNotThrow(electric::tickElectricity);
            }
            assertFalse(ElectricNetworkManager.networks.get(h.level).containsValue(retired));
            assertFalse(cable.getOrCreateElectricNetwork().members.contains(a));
        }
    }

    @Test void standardVoltageCurrentAndPowerArePreservedWithoutFEConversion() {
        try (var h = new Harness()) {
            var source = h.machine(0, 120, 2000, 0);
            var a = h.connector(1);
            var b = h.connector(4);
            var load = h.machine(5, 0, 0, 10);
            h.link(a, b);
            assertSame(a.getOrCreateElectricNetwork(), b.getOrCreateElectricNetwork());
            assertEquals(4, a.getOrCreateElectricNetwork().members.size());
            assertEquals(120, load.getData().getVoltage());
            assertEquals(12, load.getCurrent(), 0.001);
            assertEquals(1440, load.getPowerUsage());
            assertFalse(load.getData().notEnoughPower);
            assertEquals(0, a.getEnergyStorage().getEnergyStored());
            assertEquals(0, b.getEnergyStorage().getEnergyStored());
            b.setTarget(null, null);
            b.tfmg().refresh();
            assertEquals(0, load.getData().getVoltage());
            assertFalse(load.getOrCreateElectricNetwork().members.contains(source));
            h.link(a, b);
            assertEquals(120, load.getData().getVoltage());
            assertEquals(4, a.getOrCreateElectricNetwork().members.size());
        }
    }

    @Test void overloadAndMultipleSourcesUseTheStandardNetworkCalculation() {
        try (var h = new Harness()) {
            var source = h.machine(0, 120, 100, 0);
            var a = h.connector(1);
            var b = h.connector(4);
            var load = h.machine(5, 0, 0, 10);
            h.link(a, b);
            assertTrue(load.getData().notEnoughPower);
            var source2 = h.put(new Machine(new BlockPos(1, 2, 1), 100, 2000, 0));
            a.tfmg().refresh();
            assertEquals(120, load.getData().getVoltage());
            assertEquals(2100, load.getNetworkPowerGeneration());
            assertFalse(load.getData().notEnoughPower);
            assertTrue(source.getOrCreateElectricNetwork().members.contains(source2));
        }
    }

    @Test void unloadAndSamePositionReplacementDropStaleMembers() {
        try (var h = new Harness()) {
            h.machine(0, 120, 2000, 0);
            var a = h.connector(1);
            var b = h.connector(4);
            var load = h.machine(5, 0, 0, 10);
            h.link(a, b);
            b.onChunkUnloaded();
            assertEquals(0, load.getData().getVoltage());
            assertFalse(a.getOrCreateElectricNetwork().members.contains(b));
            var replacement = h.connector(4);
            h.link(a, replacement);
            assertEquals(120, load.getData().getVoltage());
            assertFalse(a.getOrCreateElectricNetwork().members.contains(b));
        }
    }

    @Test void removedAdjacentGeneratorStopsPowerWithoutRebuildingEveryTick() {
        try (var h = new Harness()) {
            var source = h.machine(0, 120, 2000, 0);
            var a = h.connector(1);
            var b = h.connector(4);
            var load = h.machine(5, 0, 0, 10);
            h.link(a, b);
            var stable = a.getOrCreateElectricNetwork();
            a.tfmg().refresh();
            assertSame(stable, a.getOrCreateElectricNetwork());
            h.entities.remove(source.getBlockPos());
            a.tfmg().refresh();
            assertEquals(0, load.getData().getVoltage());
            assertFalse(a.getOrCreateElectricNetwork().members.contains(source));
        }
    }

    @Test void multiplePairsAndLoopedStandardCablesRemainConnectedWhenOnePairIsCut() {
        try (var h = new Harness()) {
            h.machine(0, 120, 2000, 0);
            var a = h.connector(1);
            var b = h.connector(4);
            var c = h.connector(5);
            var d = h.connector(8);
            var load = h.machine(9, 0, 0, 10);
            h.link(a, b);
            h.link(c, d);
            assertEquals(120, load.getData().getVoltage());
            var left = h.put(new Cable(new BlockPos(1, 2, 1)));
            var right = h.put(new Cable(new BlockPos(8, 2, 1)));
            var type = mock(CableType.class);
            left.connections.add(new CableConnection(left.getBlockPos(), right.getBlockPos(), type, true));
            right.connections.add(new CableConnection(left.getBlockPos(), right.getBlockPos(), type, false));
            a.tfmg().refresh();
            a.setTarget(null, null);
            a.tfmg().refresh();
            assertEquals(120, load.getData().getVoltage());
            left.connections.clear();
            right.connections.clear();
            // A standard cable changes beyond the directly adjacent block.
            a.tfmg().refresh();
            assertEquals(0, load.getData().getVoltage());
        }
    }

    @Test void nonreciprocalAndDifferentLevelTargetsDoNotJoin() {
        try (var h = new Harness()) {
            var a = h.connector(1);
            var b = h.connector(4);
            a.setTarget(b.getBlockPos(), null);
            a.tfmg().refresh();
            assertNotEquals(a.getData().getId(), b.getData().getId());
            b.setTarget(a.getBlockPos(), null);
            b.setLevel(mock(Level.class));
            a.tfmg().refresh();
            assertNotEquals(a.getData().getId(), b.getData().getId());
        }
    }

    @Test void optionalFacadeLoadsWithAllTFMGClassesHidden() throws Exception {
        try (var h = new Harness()) {
            var owner = h.connector(1);
            String facade = TfmgCompat.class.getName();
            var isolated = new ClassLoader(getClass().getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("com.drmangotea.tfmg.") || name.startsWith(
                            "com.mk2525.vsfluidlink.compat.tfmg.Tfmg") && !name.equals(facade))
                        throw new ClassNotFoundException(name);
                    if (!name.equals(facade)) return super.loadClass(name, resolve);
                    var existing = findLoadedClass(name);
                    if (existing != null) return existing;
                    try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        var bytes = input.readAllBytes();
                        var type = defineClass(name, bytes, 0, bytes.length);
                        if (resolve) resolveClass(type);
                        return type;
                    } catch (java.io.IOException e) { throw new ClassNotFoundException(name, e); }
                }
            };
            var entry = Class.forName(facade, true, isolated);
            assertEquals(false, entry.getMethod("enabled").invoke(null));
            entry.getMethod("linkChanged", ElectricWireConnectorBlockEntity.class).invoke(null, owner);
            assertEquals(false, entry.getMethod("usesNativePort", ElectricWireConnectorBlockEntity.class,
                    Direction.class).invoke(null, owner, Direction.UP));
        }
    }

    @Test void unsupportedVersionsDisableOnlyTheTFMGIntegration() {
        try (var h = new Harness()) {
            var container = mock(net.neoforged.fml.ModContainer.class);
            var info = mock(net.neoforged.neoforgespi.language.IModInfo.class);
            when(container.getModInfo()).thenReturn(info);
            when(ModList.get().getModContainerById("tfmg")).thenAnswer(call -> Optional.of(container));
            for (String version : List.of("1.3.0-community", "1.3.1-community", "1.3.2a-community")) {
                when(info.getVersion()).thenReturn(new org.apache.maven.artifact.versioning.DefaultArtifactVersion(version));
                assertTrue(TfmgCompat.enabled(), version);
            }
            for (String version : List.of("1.2.0", "1.3.2", "1.2.4b-community", "1.4.0-community", "1.0.2f")) {
                when(info.getVersion()).thenReturn(new org.apache.maven.artifact.versioning.DefaultArtifactVersion(version));
                assertFalse(TfmgCompat.enabled(), version);
            }
        }
    }

    @Test void combinedSubclassesExposeBothIndependentElectricityInterfaces() throws Exception {
        for (String name : List.of("Electric", "Magnet")) {
            var type = Class.forName("com.mk2525.vsfluidlink.compat.powergrid.GridTfmgConnectorFactory$" + name);
            assertTrue(TfmgNode.class.isAssignableFrom(type));
            assertTrue(Class.forName("com.mk2525.vsfluidlink.compat.powergrid.GridTerminal").isAssignableFrom(type));
        }
    }
}
