package com.mk2525.vsfluidlink.compat.tfmg;

import com.drmangotea.tfmg.content.electricity.connection.cables.CableConnectorBlockEntity;
import com.mk2525.vsfluidlink.mixin.TfmgMixinPlugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TfmgSpoolEndpointsTest {
    private Level level;
    private final Map<BlockPos, BlockEntity> terminals = new HashMap<>();
    private final Map<BlockPos, BlockState> states = new HashMap<>();

    @BeforeAll static void bootstrap() throws Exception { TfmgNetworkTest.bootstrap(); }

    @BeforeEach void setup() {
        level = mock(Level.class);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getBlockEntity(any())).thenAnswer(call -> terminals.get(call.getArgument(0)));
        when(level.getBlockState(any())).thenAnswer(call -> states.getOrDefault(
                call.getArgument(0), Blocks.AIR.defaultBlockState()));
    }

    private void terminal(BlockPos pos, Direction facing) {
        terminals.put(pos, mock(CableConnectorBlockEntity.class));
        states.put(pos, Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, facing));
    }

    @Test void differentFacingTerminalAboveConnectorIsNotAnExtension() {
        var lower = new BlockPos(20481032, 129, 20526088);
        terminal(lower, Direction.UP);
        terminal(lower.above(), Direction.EAST);
        assertEquals(lower, TfmgSpoolEndpoints.resolve(level, lower));
    }

    @Test void sameFacingStacksStillSelectOuterTerminalInEveryDirection() {
        for (var facing : Direction.values()) {
            terminals.clear();
            states.clear();
            var origin = new BlockPos(4, 8, 12);
            for (int i = 0; i < 4; i++) terminal(origin.relative(facing, i), facing);
            assertEquals(origin.relative(facing, 3), TfmgSpoolEndpoints.resolve(level, origin));
        }
    }

    @Test void endpointsUseTheirOwnFacingRegardlessOfClickOrder() {
        var first = new BlockPos(0, 2, 0);
        var second = new BlockPos(8, 2, 0);
        terminal(first, Direction.UP);
        terminal(first.above(), Direction.UP);
        terminal(second, Direction.EAST);
        terminal(second.east(), Direction.EAST);
        terminal(first.east(), Direction.EAST);
        assertEquals(first.above(), TfmgSpoolEndpoints.resolve(level, first));
        assertEquals(second.east(), TfmgSpoolEndpoints.resolve(level, second));
        assertEquals(first.above(), TfmgSpoolEndpoints.resolve(level, first));
    }

    @Test void nonTerminalTargetsAreUnchanged() {
        var origin = new BlockPos(1, 2, 3);
        terminal(origin.above(), Direction.UP);
        assertEquals(origin, TfmgSpoolEndpoints.resolve(level, origin));
    }

    @Test void doesNotLoadChunksOrWalkBeyondNativeLimit() {
        var origin = new BlockPos(1, 2, 3);
        for (int i = 0; i < 70; i++) terminal(origin.above(i), Direction.UP);
        assertEquals(origin.above(64), TfmgSpoolEndpoints.resolve(level, origin));
        when(level.isLoaded(origin.above(2))).thenReturn(false);
        assertEquals(origin.above(), TfmgSpoolEndpoints.resolve(level, origin));
        verify(level, times(1)).getBlockEntity(origin.above(2));
    }

    @Test void missingFacingStopsAtClickedTerminal() {
        var origin = new BlockPos(1, 2, 3);
        terminal(origin, Direction.UP);
        states.put(origin, Blocks.STONE.defaultBlockState());
        assertEquals(origin, TfmgSpoolEndpoints.resolve(level, origin));
    }

    @Test void optionalMixinOnlyPatchesVerifiedCeVersion() {
        var plugin = new TfmgMixinPlugin();
        try (var loading = mockStatic(LoadingModList.class)) {
            loading.when(LoadingModList::get).thenReturn(null);
            assertFalse(plugin.shouldApplyMixin("SpoolItem", "TfmgSpoolItemMixin"));
            var mods = mock(LoadingModList.class);
            loading.when(LoadingModList::get).thenReturn(mods);
            when(mods.getMods()).thenReturn(List.of());
            assertFalse(plugin.shouldApplyMixin("SpoolItem", "TfmgSpoolItemMixin"));
            var mod = mock(ModInfo.class);
            when(mod.getModId()).thenReturn("tfmg");
            when(mods.getMods()).thenReturn(List.of(mod));
            for (String version : List.of("1.0.2f", "1.3.1-community", "1.3.3-community")) {
                when(mod.getVersion()).thenReturn(new DefaultArtifactVersion(version));
                assertFalse(plugin.shouldApplyMixin("SpoolItem", "TfmgSpoolItemMixin"));
            }
            when(mod.getVersion()).thenReturn(new DefaultArtifactVersion("1.3.2a-community"));
            assertTrue(plugin.shouldApplyMixin("SpoolItem", "TfmgSpoolItemMixin"));
        }
    }
}
