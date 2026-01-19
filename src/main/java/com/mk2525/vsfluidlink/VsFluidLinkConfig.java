package com.mk2525.vsfluidlink;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class VsFluidLinkConfig {
    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public static class ServerConfig {
        public final ForgeConfigSpec.DoubleValue maxLinkDistance;
        public final ForgeConfigSpec.IntValue magnetScanDistance;
        public final ForgeConfigSpec.IntValue magnetScanRadius;
        public final ForgeConfigSpec.BooleanValue restrictWorldToWorldConnection;
        public final ForgeConfigSpec.BooleanValue restrictIntraShipConnection;

        public ServerConfig(ForgeConfigSpec.Builder builder) {
            builder.push("general");

            maxLinkDistance = builder
                    .comment("Maximum distance between connected blocks before the link breaks.")
                    .defineInRange("maxLinkDistance", 10.0, 1.0, 300.0);

            magnetScanDistance = builder
                    .comment("Maximum distance in front of the Magnet Connector to scan for other connectors.")
                    .defineInRange("magnetScanDistance", 5, 1, 100);

            magnetScanRadius = builder
                    .comment("Radius around the scan line to check for connectors (Scan box size will be 1 + radius * 2).")
                    .defineInRange("magnetScanRadius", 1, 0, 10);
            
            restrictWorldToWorldConnection = builder
                    .comment("If true, connections between two blocks that are both NOT on a ship (i.e., on the ground) will be blocked.")
                    .define("restrictWorldToWorldConnection", false);

            restrictIntraShipConnection = builder
                    .comment("If true, connections between two blocks that are on the SAME ship will be blocked.")
                    .define("restrictIntraShipConnection", false);

            builder.pop();
        }
    }
}
