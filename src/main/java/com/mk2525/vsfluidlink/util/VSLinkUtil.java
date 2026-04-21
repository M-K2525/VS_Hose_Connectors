package com.mk2525.vsfluidlink.util;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class VSLinkUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> printedMethods = new HashSet<>();
    private static final Set<String> printedQueryableMethods = new HashSet<>();
    private static Boolean valkyrienSkiesLoaded;

    public static boolean isValkyrienSkiesLoaded() {
        if (valkyrienSkiesLoaded == null) {
            valkyrienSkiesLoaded = ModList.get().isLoaded("valkyrienskies");
        }
        return valkyrienSkiesLoaded;
    }

    private static Object getShipManagingPos(Level level, BlockPos pos) {
        if (level == null) return null;
        if (!isValkyrienSkiesLoaded()) return null;

        try {
            // 1. Try getShipObjectWorld
            Method getShipObjectWorld = level.getClass().getMethod("getShipObjectWorld");
            Object shipWorld = getShipObjectWorld.invoke(level);

            if (shipWorld != null) {
                // Try getQueryableShipData
                try {
                    Method getQueryableShipData = shipWorld.getClass().getMethod("getQueryableShipData");
                    Object queryableShipData = getQueryableShipData.invoke(shipWorld);

                    if (queryableShipData != null) {
                         int chunkX = pos.getX() >> 4;
                         int chunkZ = pos.getZ() >> 4;

                         // Try getShipDataFromChunkPos(int, int)
                         try {
                             Method getShipDataFromChunkPos = queryableShipData.getClass().getMethod("getShipDataFromChunkPos", int.class, int.class);
                             return getShipDataFromChunkPos.invoke(queryableShipData, chunkX, chunkZ);
                         } catch (NoSuchMethodException e) {
                             // Try getByChunkPos(int, int)
                             try {
                                 Method getByChunkPos = queryableShipData.getClass().getMethod("getByChunkPos", int.class, int.class);
                                 return getByChunkPos.invoke(queryableShipData, chunkX, chunkZ);
                             } catch (NoSuchMethodException ex) {
                                 // Debug: Print methods if still failing
                                 if (!level.isClientSide) {
                                     String className = queryableShipData.getClass().getName();
                                     if (!printedQueryableMethods.contains(className)) {
                                         printedQueryableMethods.add(className);
                                         LOGGER.info("[VS Fluid Link DEBUG] Methods available in QueryableShipData (" + className + "):");
                                         Arrays.stream(queryableShipData.getClass().getMethods())
                                               .map(Method::getName)
                                               .filter(name -> name.toLowerCase(Locale.ROOT).contains("ship") || name.toLowerCase(Locale.ROOT).contains("get"))
                                               .forEach(name -> LOGGER.info(" - " + name));
                                     }
                                 }
                             }
                         }
                    }
                } catch (NoSuchMethodException e) {
                    // Try getLoadedShips
                    try {
                        Method getLoadedShips = shipWorld.getClass().getMethod("getLoadedShips");
                        Object loadedShips = getLoadedShips.invoke(shipWorld);

                        // Try getShipManagingPos on loadedShips
                        Method getShipManagingPosLoaded = loadedShips.getClass().getMethod("getShipManagingPos", BlockPos.class);
                        return getShipManagingPosLoaded.invoke(loadedShips, pos);
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            }

            // 2. Fallback to direct methods on Level (just in case)
            try {
                Method getShipObjectManagingPos = level.getClass().getMethod("getShipObjectManagingPos", BlockPos.class);
                return getShipObjectManagingPos.invoke(level, pos);
            } catch (NoSuchMethodException e) {
                // ignore
            }

        } catch (Exception e) {
            LOGGER.error("[VS Fluid Link] Failed to get ship for pos: " + pos, e);
        }

        // Fallback to VSGameUtilsKt only on client side
        if (level.isClientSide) {
            try {
                Class<?> vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
                Method getShipManagingPosKt = vsGameUtilsClass.getMethod("getShipManagingPos", Level.class, BlockPos.class);
                return getShipManagingPosKt.invoke(null, level, pos);
            } catch (Exception exc) {
                // ignore
            }
        }

        return null;
    }

    public static Long getShipId(Level level, BlockPos pos) {
        if (!isValkyrienSkiesLoaded()) return null;

        Object ship = getShipManagingPos(level, pos);
        if (ship != null) {
            try {
                Method getId = ship.getClass().getMethod("getId");
                return (Long) getId.invoke(ship);
            } catch (Exception e) {
                LOGGER.error("[VS Fluid Link] Failed to get ship ID for pos: " + pos, e);
            }
        }
        return null;
    }

    public static Vec3 getWorldPos(Level level, BlockPos pos) {
        if (!isValkyrienSkiesLoaded()) return Vec3.atCenterOf(pos);

        Object ship = getShipManagingPos(level, pos);
        if (ship != null) {
            try {
                Method getTransform = ship.getClass().getMethod("getTransform");
                Object transform = getTransform.invoke(ship);

                Method getShipToWorld = transform.getClass().getMethod("getShipToWorld");
                Object shipToWorldMatrix = getShipToWorld.invoke(transform);

                Vector3d posInShip = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

                Class<?> matrix4dcClass = Class.forName("org.joml.Matrix4dc");
                Method transformPosition = matrix4dcClass.getMethod("transformPosition", Vector3d.class);
                transformPosition.invoke(shipToWorldMatrix, posInShip);

                return new Vec3(posInShip.x, posInShip.y, posInShip.z);
            } catch (Exception e) {
                LOGGER.error("[VS Fluid Link] getWorldPos (server) failed for pos: " + pos, e);
            }
        }
        return Vec3.atCenterOf(pos);
    }

    public static boolean isVirtualWorld(Level level) {
        if (level == null) return false;
        String className = level.getClass().getName();
        return className.contains("Virtual") || className.contains("Contraption") || className.contains("Schematic");
    }

    public static class WorldTransform {
        public final Vec3 position;
        public final Vec3 direction;

        public WorldTransform(Vec3 position, Vec3 direction) {
            this.position = position;
            this.direction = direction;
        }
    }

    public static WorldTransform getWorldTransform(Level level, BlockPos pos, Direction facing) {
        Vector3d posInShip = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vector3d dirInShip = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Object ship = getShipManagingPos(level, pos);

        if (ship != null) {
            try {
                Method getTransform = ship.getClass().getMethod("getTransform");
                Object transform = getTransform.invoke(ship);

                Method getShipToWorld = transform.getClass().getMethod("getShipToWorld");
                Object shipToWorldMatrix = getShipToWorld.invoke(transform);

                Class<?> matrix4dcClass = Class.forName("org.joml.Matrix4dc");
                Method transformPosition = matrix4dcClass.getMethod("transformPosition", Vector3d.class);
                Method transformDirection = matrix4dcClass.getMethod("transformDirection", Vector3d.class);

                transformPosition.invoke(shipToWorldMatrix, posInShip);
                transformDirection.invoke(shipToWorldMatrix, dirInShip);
            } catch (Exception e) {
                LOGGER.error("[VS Fluid Link] getWorldTransform (server) failed for pos: " + pos, e);
            }
        }

        return new WorldTransform(
            new Vec3(posInShip.x, posInShip.y, posInShip.z),
            new Vec3(dirInShip.x, dirInShip.y, dirInShip.z).normalize()
        );
    }

    public static class Client {
        public static Vec3 getRenderWorldPos(Level level, BlockPos pos) {
            if (!isValkyrienSkiesLoaded()) return Vec3.atCenterOf(pos);

            Object ship = getShipManagingPos(level, pos);
            if (ship != null) {
                try {
                    Method getRenderTransform = ship.getClass().getMethod("getRenderTransform");
                    Object transform = getRenderTransform.invoke(ship);

                    Method getShipToWorld = transform.getClass().getMethod("getShipToWorld");
                    Object shipToWorldMatrix = getShipToWorld.invoke(transform);

                    Vector3d posInShip = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

                    Class<?> matrix4dcClass = Class.forName("org.joml.Matrix4dc");
                    Method transformPosition = matrix4dcClass.getMethod("transformPosition", Vector3d.class);
                    transformPosition.invoke(shipToWorldMatrix, posInShip);

                    return new Vec3(posInShip.x, posInShip.y, posInShip.z);
                } catch (Exception e) {
                    LOGGER.error("[VS Fluid Link] getRenderWorldPos failed for pos: " + pos, e);
                }
            }
            return Vec3.atCenterOf(pos);
        }

        public static WorldTransform getRenderWorldTransform(Level level, BlockPos pos, Direction facing) {
            Vector3d posInShip = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vector3d dirInShip = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
            Object ship = getShipManagingPos(level, pos);

            if (ship != null) {
                try {
                    Method getRenderTransform = ship.getClass().getMethod("getRenderTransform");
                    Object transform = getRenderTransform.invoke(ship);

                    Method getShipToWorld = transform.getClass().getMethod("getShipToWorld");
                    Object shipToWorldMatrix = getShipToWorld.invoke(transform);

                    Class<?> matrix4dcClass = Class.forName("org.joml.Matrix4dc");
                    Method transformPosition = matrix4dcClass.getMethod("transformPosition", Vector3d.class);
                    Method transformDirection = matrix4dcClass.getMethod("transformDirection", Vector3d.class);

                    transformPosition.invoke(shipToWorldMatrix, posInShip);
                    transformDirection.invoke(shipToWorldMatrix, dirInShip);
                } catch (Exception e) {
                    LOGGER.error("[VS Fluid Link] getRenderWorldTransform failed for pos: " + pos, e);
                }
            }

            return new WorldTransform(
                new Vec3(posInShip.x, posInShip.y, posInShip.z),
                new Vec3(dirInShip.x, dirInShip.y, dirInShip.z).normalize()
            );
        }
    }
}
