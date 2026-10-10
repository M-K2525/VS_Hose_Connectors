package com.mk2525.vsfluidlink;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class VsFluidLinkConfig {
    public static final int DEFAULT_FLUID_CAPACITY = 1000;
    public static final int DEFAULT_ITEM_SLOTS = 1;
    public static final int DEFAULT_ENERGY_CAPACITY = 100000;
    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
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

    public static class ServerConfig {
        public final ForgeConfigSpec.DoubleValue maxLinkDistance;
        public final ForgeConfigSpec.IntValue magnetScanDistance;
        public final ForgeConfigSpec.IntValue magnetScanRadius;
        public final ForgeConfigSpec.BooleanValue restrictWorldToWorldConnection;
        public final ForgeConfigSpec.BooleanValue restrictIntraShipConnection;
        public final ForgeConfigSpec.IntValue fluidCapacity;
        public final ForgeConfigSpec.IntValue itemSlots;
        public final ForgeConfigSpec.IntValue energyCapacity;

        public ServerConfig(ForgeConfigSpec.Builder builder) {
            builder.translation("vsfluidlink.configuration.general").push("general");

            maxLinkDistance = builder
                    .translation("vsfluidlink.configuration.maxLinkDistance")
                    .comment("Maximum distance between connected blocks before the link breaks.")
                    .defineInRange("maxLinkDistance", 10.0, 1.0, 300.0);

            magnetScanDistance = builder
                    .translation("vsfluidlink.configuration.magnetScanDistance")
                    .comment("Maximum distance in front of the Magnet Connector to scan for other connectors.")
                    .defineInRange("magnetScanDistance", 5, 1, 100);

            magnetScanRadius = builder
                    .translation("vsfluidlink.configuration.magnetScanRadius")
                    .comment("Radius around the scan line to check for connectors (Scan box size will be 1 + radius * 2).")
                    .defineInRange("magnetScanRadius", 1, 0, 10);
            
            restrictWorldToWorldConnection = builder
                    .translation("vsfluidlink.configuration.restrictWorldToWorldConnection")
                    .comment("If true, connections between two blocks that are both NOT on a ship (i.e., on the ground) will be blocked.")
                    .define("restrictWorldToWorldConnection", false);

            restrictIntraShipConnection = builder
                    .translation("vsfluidlink.configuration.restrictIntraShipConnection")
                    .comment("If true, connections between two blocks that are on the SAME ship will be blocked.")
                    .define("restrictIntraShipConnection", false);

            builder.pop();
            builder.translation("vsfluidlink.configuration.buffers").push("buffers");
            fluidCapacity = builder
                    .translation("vsfluidlink.configuration.fluidCapacity")
                    .comment("Fluid capacity per normal or magnet hose connector, in mB. Restart the world after changing.",
                            "Drain buffers before reducing capacity; excess fluid will be discarded on load.")
                    .worldRestart()
                    .defineInRange("fluidCapacity", DEFAULT_FLUID_CAPACITY, 1, Integer.MAX_VALUE / 2);
            itemSlots = builder
                    .translation("vsfluidlink.configuration.itemSlots")
                    .comment("Inventory slots per normal or magnet item hose connector. Restart the world after changing.",
                            "Occupied slots beyond a reduced limit remain extract-only until emptied and reloaded.")
                    .worldRestart()
                    .defineInRange("itemSlots", DEFAULT_ITEM_SLOTS, 1, 256);
            energyCapacity = builder
                    .translation("vsfluidlink.configuration.energyCapacity")
                    .comment("Energy capacity per normal or magnet electric connector, in FE. Restart the world after changing.",
                            "Transfer rate remains 10000 FE/t. Excess energy will be discarded when capacity is reduced.")
                    .worldRestart()
                    .defineInRange("energyCapacity", DEFAULT_ENERGY_CAPACITY, 1, Integer.MAX_VALUE / 2);
            builder.pop();
        }
    }
}
