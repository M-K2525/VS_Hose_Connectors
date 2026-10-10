package com.mk2525.vsfluidlink.compat.computercraft;

import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredNode;
import dan200.computercraft.api.network.wired.WiredNetworkChange;
import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.PacketReceiver;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CCWiredConnectionTest {
    private static class Observer implements WiredElement, PacketReceiver {
        final Map<String, IPeripheral> peripherals = new HashMap<>();
        final WiredNode node = ComputerCraftAPI.createWiredNodeForElement(this);
        Packet lastPacket;
        public WiredNode getNode() { return node; }
        public Level getLevel() { return null; }
        public Vec3 getPosition() { return Vec3.ZERO; }
        public String getSenderID() { return "test"; }
        public double getRange() { return Double.POSITIVE_INFINITY; }
        public boolean isInterdimensional() { return false; }
        public void receiveSameDimension(Packet packet, double distance) { lastPacket = packet; }
        public void receiveDifferentDimension(Packet packet) { lastPacket = packet; }
        public void networkChanged(WiredNetworkChange change) {
            change.peripheralsRemoved().keySet().forEach(peripherals::remove);
            peripherals.putAll(change.peripheralsAdded());
        }
    }

    @Test
    void packetsCrossBridgeInBothDirectionsAndStopOnDetach() {
        var left = connector();
        var right = connector();
        var sender = new Observer();
        var receiver = new Observer();
        sender.node.connectTo(left.getNode());
        receiver.node.connectTo(right.getNode());
        sender.node.addReceiver(sender);
        receiver.node.addReceiver(receiver);
        left.connect(right);
        var forward = new Packet(12, 34, "forward", sender);
        sender.node.transmitSameDimension(forward, Double.POSITIVE_INFINITY);
        assertSame(forward, receiver.lastPacket);
        var reply = new Packet(34, 12, "reply", receiver);
        receiver.node.transmitSameDimension(reply, Double.POSITIVE_INFINITY);
        assertSame(reply, sender.lastPacket);
        left.connect(null);
        receiver.lastPacket = null;
        sender.node.transmitSameDimension(forward, Double.POSITIVE_INFINITY);
        assertNull(receiver.lastPacket);
        left.remove();
        right.remove();
        sender.node.remove();
        receiver.node.remove();
    }
    @BeforeAll
    static void bootstrap() {
        assumeTrue(Boolean.getBoolean("withComputerCraft"), "Run with -PwithComputerCraft=true for real CC graph tests");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CCWiredConnection connector() {
        return new CCWiredConnection(new ElectricWireConnectorBlockEntity(
                BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()));
    }

    private static IPeripheral peripheral() {
        return (IPeripheral) Proxy.newProxyInstance(IPeripheral.class.getClassLoader(),
                new Class<?>[]{IPeripheral.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> "test_inventory";
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                });
    }

    @Test
    void detachReloadAndReplacementShareAndRemovePeripherals() {
        var left = connector();
        var right = connector();
        var cableLeft = new Observer();
        var cableRight = connector();
        cableLeft.getNode().connectTo(left.getNode());
        cableRight.getNode().connectTo(right.getNode());
        var inventory = peripheral();
        cableRight.getNode().updatePeripherals(Map.of("inventory_1", inventory));
        left.connect(right);
        assertSame(inventory, cableLeft.peripherals.get("inventory_1"));
        left.connect(null);
        assertFalse(cableLeft.peripherals.containsKey("inventory_1"));
        left.connect(right);
        right.remove();
        right.remove();
        assertFalse(cableLeft.peripherals.containsKey("inventory_1"));
        var replacement = connector();
        cableRight.getNode().connectTo(replacement.getNode());
        left.connect(replacement);
        assertSame(inventory, cableLeft.peripherals.get("inventory_1"));
        left.remove();
        replacement.remove();
        cableLeft.node.remove();
        cableRight.remove();
    }

    @Test
    void loopRetainsAlternativePathUntilLastBridgeIsRemoved() {
        var a = connector();
        var b = connector();
        var c = connector();
        var d = connector();
        var observer = new Observer();
        observer.node.connectTo(a.getNode());
        a.getNode().connectTo(c.getNode());
        b.getNode().connectTo(d.getNode());
        b.getNode().updatePeripherals(Map.of("inventory_2", peripheral()));
        a.connect(b);
        c.connect(d);
        a.connect(null);
        assertTrue(observer.peripherals.containsKey("inventory_2"));
        c.connect(null);
        assertFalse(observer.peripherals.containsKey("inventory_2"));
        a.remove();
        b.remove();
        c.remove();
        d.remove();
        observer.node.remove();
    }
}
