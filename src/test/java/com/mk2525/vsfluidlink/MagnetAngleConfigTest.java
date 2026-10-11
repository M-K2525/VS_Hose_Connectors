package com.mk2525.vsfluidlink;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MagnetAngleConfigTest {
    @AfterEach void unload() { VsFluidLinkConfig.SERVER_SPEC.acceptConfig(null); }

    private void load(double connection, double disconnection, double chain) throws Exception {
        var config = CommentedConfig.inMemory();
        VsFluidLinkConfig.SERVER_SPEC.correct(config);
        config.set("general.magnetMaxConnectionAngle", connection);
        config.set("general.magnetDisconnectionAngle", disconnection);
        config.set("general.magnetChainDisconnectionAngle", chain);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        VsFluidLinkConfig.SERVER_SPEC.acceptConfig((IConfigSpec.ILoadedConfig)
                constructor.newInstance(config, null, null));
    }

    private double dot(double misalignment) { return -Math.cos(Math.toRadians(misalignment)); }

    @Test void defaultsUse45DegreeConnectionAnd90DegreeDisconnection() {
        VsFluidLinkConfig.SERVER_SPEC.acceptConfig(null);
        assertTrue(VsFluidLinkConfig.canMagnetConnect(dot(44.999)));
        assertFalse(VsFluidLinkConfig.canMagnetConnect(dot(45.001)));
        for (boolean chain : new boolean[]{false, true}) {
            assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(89.999), chain));
            assertTrue(VsFluidLinkConfig.shouldMagnetDisconnect(dot(90.001), chain));
        }
        var config = CommentedConfig.inMemory();
        VsFluidLinkConfig.SERVER_SPEC.correct(config);
        assertEquals(45.0, (double) config.get("general.magnetMaxConnectionAngle"));
        assertEquals(90.0, (double) config.get("general.magnetDisconnectionAngle"));
        assertEquals(90.0, (double) config.get("general.magnetChainDisconnectionAngle"));
    }

    @Test void configuredDegreesControlConnectionAndSeparateDisconnectionLimits() throws Exception {
        load(45, 80, 55);
        assertTrue(VsFluidLinkConfig.canMagnetConnect(dot(44)));
        assertFalse(VsFluidLinkConfig.canMagnetConnect(dot(46)));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(79), false));
        assertTrue(VsFluidLinkConfig.shouldMagnetDisconnect(dot(81), false));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(54), true));
        assertTrue(VsFluidLinkConfig.shouldMagnetDisconnect(dot(56), true));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(56), false));
    }

    @Test void hysteresisKeepsAnExistingLinkOutsideConnectionRange() throws Exception {
        load(30, 60, 40);
        assertFalse(VsFluidLinkConfig.canMagnetConnect(dot(35)));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(35), false));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(35), true));
    }

    @Test void zeroAndFullRangeUseStrictBoundaryChecks() throws Exception {
        load(0, 180, 180);
        assertFalse(VsFluidLinkConfig.canMagnetConnect(-1));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(1, false));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(1, true));
        load(180, 0, 0);
        assertTrue(VsFluidLinkConfig.canMagnetConnect(dot(179)));
        assertFalse(VsFluidLinkConfig.canMagnetConnect(1));
        assertTrue(VsFluidLinkConfig.shouldMagnetDisconnect(dot(1), false));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(-1, true));
    }
}
