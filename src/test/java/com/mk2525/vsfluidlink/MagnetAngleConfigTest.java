package com.mk2525.vsfluidlink;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MagnetAngleConfigTest {
    @AfterEach void reset() { load(45, 90, 90); }

    private void load(double connection, double disconnection, double chain) {
        var config = CommentedConfig.inMemory();
        VsFluidLinkConfig.SERVER_SPEC.correct(config);
        config.set("general.magnetMaxConnectionAngle", connection);
        config.set("general.magnetDisconnectionAngle", disconnection);
        config.set("general.magnetChainDisconnectionAngle", chain);
        VsFluidLinkConfig.SERVER_SPEC.setConfig(config);
    }

    private double dot(double misalignment) { return -Math.cos(Math.toRadians(misalignment)); }

    @Test void defaultsUse45DegreeConnectionAnd90DegreeDisconnection() {
        var config = CommentedConfig.inMemory();
        VsFluidLinkConfig.SERVER_SPEC.correct(config);
        assertEquals(45.0, (double) config.get("general.magnetMaxConnectionAngle"));
        assertEquals(90.0, (double) config.get("general.magnetDisconnectionAngle"));
        assertEquals(90.0, (double) config.get("general.magnetChainDisconnectionAngle"));
        VsFluidLinkConfig.SERVER_SPEC.setConfig(config);
        assertTrue(VsFluidLinkConfig.canMagnetConnect(dot(44.999)));
        assertFalse(VsFluidLinkConfig.canMagnetConnect(dot(45.001)));
        for (boolean chain : new boolean[]{false, true}) {
            assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(89.999), chain));
            assertTrue(VsFluidLinkConfig.shouldMagnetDisconnect(dot(90.001), chain));
        }
    }

    @Test void configuredDegreesControlConnectionAndSeparateDisconnectionLimits() {
        load(45, 80, 55);
        assertTrue(VsFluidLinkConfig.canMagnetConnect(dot(44)));
        assertFalse(VsFluidLinkConfig.canMagnetConnect(dot(46)));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(79), false));
        assertTrue(VsFluidLinkConfig.shouldMagnetDisconnect(dot(81), false));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(54), true));
        assertTrue(VsFluidLinkConfig.shouldMagnetDisconnect(dot(56), true));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(56), false));
    }

    @Test void hysteresisKeepsAnExistingLinkOutsideConnectionRange() {
        load(30, 60, 40);
        assertFalse(VsFluidLinkConfig.canMagnetConnect(dot(35)));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(35), false));
        assertFalse(VsFluidLinkConfig.shouldMagnetDisconnect(dot(35), true));
    }

    @Test void zeroAndFullRangeUseStrictBoundaryChecks() {
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
