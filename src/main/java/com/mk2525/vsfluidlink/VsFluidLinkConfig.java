package com.mk2525.vsfluidlink;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class VsFluidLinkConfig {
    public static final int DEFAULT_FLUID_CAPACITY = 1000;
    public static final int DEFAULT_ITEM_SLOTS = 1;
    public static final int DEFAULT_ENERGY_CAPACITY = 100000;
    public static final double DEFAULT_MAGNET_CONNECTION_ANGLE = 45.0;
    public static final double DEFAULT_MAGNET_DISCONNECTION_ANGLE = 90.0;
    public static final double DEFAULT_MAGNET_CHAIN_DISCONNECTION_ANGLE = 90.0;
    public enum WrenchRightClickAction implements net.neoforged.neoforge.common.TranslatableEnum {
        CONNECT, ROTATE;

        @Override
        public net.minecraft.network.chat.Component getTranslatedName() {
            return net.minecraft.network.chat.Component.translatable("vsfluidlink.configuration.wrenchRightClickAction." + name());
        }
    }
    public static final ServerConfig SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    static {
        final Pair<ServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public static int fluidCapacity() {
        return SERVER_SPEC.isLoaded() ? SERVER.fluidCapacity.get() : DEFAULT_FLUID_CAPACITY;
    }

    public static int itemSlots() {
        return SERVER_SPEC.isLoaded() ? SERVER.itemSlots.get() : DEFAULT_ITEM_SLOTS;
    }

    public static int energyCapacity() {
        return SERVER_SPEC.isLoaded() ? SERVER.energyCapacity.get() : DEFAULT_ENERGY_CAPACITY;
    }

    public static boolean canMagnetConnect(double facingDot) {
        double angle = SERVER_SPEC.isLoaded() ? SERVER.magnetMaxConnectionAngle.get() : DEFAULT_MAGNET_CONNECTION_ANGLE;
        return facingDot < -Math.cos(Math.toRadians(angle));
    }

    public static boolean connectWithoutWrench() {
        return !SERVER_SPEC.isLoaded() || SERVER.connectWithoutWrench.get();
    }

    public static WrenchRightClickAction wrenchRightClickAction() {
        return SERVER_SPEC.isLoaded() ? SERVER.wrenchRightClickAction.get() : WrenchRightClickAction.ROTATE;
    }

    public static boolean shouldMagnetDisconnect(double facingDot, boolean chain) {
        double angle = chain
                ? (SERVER_SPEC.isLoaded() ? SERVER.magnetChainDisconnectionAngle.get() : DEFAULT_MAGNET_CHAIN_DISCONNECTION_ANGLE)
                : (SERVER_SPEC.isLoaded() ? SERVER.magnetDisconnectionAngle.get() : DEFAULT_MAGNET_DISCONNECTION_ANGLE);
        return facingDot > -Math.cos(Math.toRadians(angle));
    }

    public static class ServerConfig {
        public final ModConfigSpec.DoubleValue maxLinkDistance;
        public final ModConfigSpec.IntValue magnetScanDistance;
        public final ModConfigSpec.IntValue magnetScanRadius;
        public final ModConfigSpec.DoubleValue magnetMaxConnectionAngle;
        public final ModConfigSpec.DoubleValue magnetDisconnectionAngle;
        public final ModConfigSpec.DoubleValue magnetChainDisconnectionAngle;
        public final ModConfigSpec.BooleanValue connectWithoutWrench;
        public final ModConfigSpec.EnumValue<WrenchRightClickAction> wrenchRightClickAction;
        public final ModConfigSpec.BooleanValue restrictWorldToWorldConnection;
        public final ModConfigSpec.BooleanValue restrictIntraShipConnection;
        public final ModConfigSpec.IntValue fluidCapacity;
        public final ModConfigSpec.IntValue itemSlots;
        public final ModConfigSpec.IntValue energyCapacity;

        public ServerConfig(ModConfigSpec.Builder builder) {
            builder.push("general");

            connectWithoutWrench = builder
                    .comment("Allow non-wrench items and an empty hand to connect or disconnect manual connectors.",
                            "Sneaking with a non-wrench item bypasses linking so items can be used normally.")
                    .define("connectWithoutWrench", true);
            wrenchRightClickAction = builder
                    .comment("ROTATE: wrench right-click rotates, sneak-right-click picks up the connector.",
                            "CONNECT: wrench right-click links manual connectors, sneak-right-click rotates (legacy behavior).",
                            "Magnet connectors remain automatic and cannot be manually linked.")
                    .defineEnum("wrenchRightClickAction", WrenchRightClickAction.ROTATE);

            maxLinkDistance = builder
                    .comment("Maximum distance between connected blocks before the link breaks.")
                    .defineInRange("maxLinkDistance", 10.0, 1.0, 300.0);

            magnetScanDistance = builder
                    .comment("Maximum distance in front of the Magnet Connector to scan for other connectors.")
                    .defineInRange("magnetScanDistance", 5, 1, 100);

            magnetScanRadius = builder
                    .comment("Radius around the scan line to check for connectors (Scan box size will be 1 + radius * 2).")
                    .defineInRange("magnetScanRadius", 1, 0, 10);

            magnetMaxConnectionAngle = builder
                    .comment("Maximum facing misalignment from directly opposing (0 degrees) for all magnet connectors to connect.",
                            "Degrees, not the angle to the line between blocks. Connection requires strictly less than this angle.")
                    .defineInRange("magnetMaxConnectionAngle", DEFAULT_MAGNET_CONNECTION_ANGLE, 0.0, 180.0);
            magnetDisconnectionAngle = builder
                    .comment("Facing misalignment in degrees above which fluid, item and electric magnet connectors disconnect.",
                            "Use a value larger than magnetMaxConnectionAngle to avoid repeated connecting and disconnecting.")
                    .defineInRange("magnetDisconnectionAngle", DEFAULT_MAGNET_DISCONNECTION_ANGLE, 0.0, 180.0);
            magnetChainDisconnectionAngle = builder
                    .comment("Facing misalignment in degrees above which magnet chain connectors disconnect.",
                            "Use a value larger than magnetMaxConnectionAngle to avoid repeated connecting and disconnecting.")
                    .defineInRange("magnetChainDisconnectionAngle", DEFAULT_MAGNET_CHAIN_DISCONNECTION_ANGLE, 0.0, 180.0);
            
            restrictWorldToWorldConnection = builder
                    .comment("If true, connections between two blocks that are both NOT on a ship (i.e., on the ground) will be blocked.")
                    .define("restrictWorldToWorldConnection", false);

            restrictIntraShipConnection = builder
                    .comment("If true, connections between two blocks that are on the SAME ship will be blocked.")
                    .define("restrictIntraShipConnection", false);

            builder.pop();

            builder.push("buffers");
            fluidCapacity = builder
                    .comment("Fluid capacity per normal or magnet hose connector, in mB. Restart the world after changing.",
                            "Drain buffers before reducing capacity; excess fluid will be discarded on load.")
                    .worldRestart()
                    .defineInRange("fluidCapacity", DEFAULT_FLUID_CAPACITY, 1, Integer.MAX_VALUE / 2);
            itemSlots = builder
                    .comment("Inventory slots per normal or magnet item hose connector. Restart the world after changing.",
                            "Occupied slots beyond a reduced limit remain extract-only until emptied and reloaded.")
                    .worldRestart()
                    .defineInRange("itemSlots", DEFAULT_ITEM_SLOTS, 1, 256);
            energyCapacity = builder
                    .comment("Energy capacity per normal or magnet electric connector, in FE. Restart the world after changing.",
                            "Transfer rate remains 10000 FE/t. Excess energy will be discarded when capacity is reduced.")
                    .worldRestart()
                    .defineInRange("energyCapacity", DEFAULT_ENERGY_CAPACITY, 1, Integer.MAX_VALUE / 2);
            builder.pop();
        }
    }
}
