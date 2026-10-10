package com.mk2525.vsfluidlink.compat.powergrid;

import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import static org.junit.jupiter.api.Assertions.*;

class GridCircuitTest {
    @Test
    void standardSolverAccountsForBothLinkResistanceAndOpenReturnPath() {
        var network = new ElectricalNetwork(true);
        var sourcePositive = new FloatingNode();
        var sourceNegative = new FloatingNode();
        var loadPositive = new FloatingNode();
        var loadNegative = new FloatingNode();
        network.addNode(sourcePositive);
        network.addNode(sourceNegative);
        network.addNode(loadPositive);
        network.addNode(loadNegative);
        var source = new VoltageSourceCoupling(sourcePositive, sourceNegative, 0.1f, 120);
        network.addNode(source);
        var forward = new ElectricWire(GridTerminalBehaviour.LINK_RESISTANCE, sourcePositive, loadPositive);
        var returning = new ElectricWire(GridTerminalBehaviour.LINK_RESISTANCE, loadNegative, sourceNegative);
        var load = new ElectricWire(10, loadPositive, loadNegative);
        network.addWire(forward);
        network.addWire(returning);
        network.addWire(load);
        network.addWire(new ElectricWire(1, sourceNegative, null));
        for (int tick = 0; tick < 5; tick++) network.calculate(1);
        double expected = 120 / (10 + 0.1 + 2 * GridTerminalBehaviour.LINK_RESISTANCE);
        assertEquals(expected, load.current(), 0.001);
        assertEquals(expected * GridTerminalBehaviour.LINK_RESISTANCE, forward.potentialDifference(), 0.001);
        assertEquals(expected * expected * GridTerminalBehaviour.LINK_RESISTANCE, forward.internalPower(), 0.001);
        source.setVoltage(-120);
        for (int tick = 0; tick < 5; tick++) network.calculate(1);
        assertEquals(-expected, load.current(), 0.001);
        returning.remove();
        for (int tick = 0; tick < 5; tick++) network.calculate(1);
        assertEquals(0, load.current(), 0.0001);
        assertNull(returning.getNetwork());
        network.cleanup();
    }
}
