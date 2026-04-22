package com.mk2525.vsfluidlink.util;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class VSLinkUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> printedMethods = new HashSet<>();
    private static final Set<String> printedQueryableMethods = new HashSet<>();
    private static Boolean valkyrienSkiesLoaded;
    private static Boolean sableLoaded;
    private static Object sableCompanion;

    public static boolean isValkyrienSkiesLoaded() {
        if (valkyrienSkiesLoaded == null) {
            valkyrienSkiesLoaded = ModList.get().isLoaded("valkyrienskies");
        }
        return valkyrienSkiesLoaded;
    }

    public static boolean isSableLoaded() {
        if (sableLoaded == null) {
            sableLoaded = (ModList.get().isLoaded("sable") || ModList.get().isLoaded("sablecompanion"))
                    && classExists("dev.ryanhcode.sable.companion.SableCompanion");
        }
        return sableLoaded;
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, VSLinkUtil.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
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
        Object ship = getShipManagingPos(level, pos);
        if (ship != null) {
            try {
                Method getId = ship.getClass().getMethod("getId");
                return (Long) getId.invoke(ship);
            } catch (Exception e) {
                LOGGER.error("[VS Fluid Link] Failed to get ship ID for pos: " + pos, e);
            }
        }
        UUID subLevelId = getSableSubLevelId(level, pos);
        if (subLevelId != null) {
            return subLevelId.getMostSignificantBits() ^ subLevelId.getLeastSignificantBits();
        }
        return null;
    }

    public static Vec3 getWorldPos(Level level, BlockPos pos) {
        Vec3 localPos = Vec3.atCenterOf(pos);

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
        return getSableWorldPos(level, localPos, false);
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

        if (ship == null) {
            WorldTransform sableTransform = getSableWorldTransform(level, pos, facing, false);
            if (sableTransform != null) {
                return sableTransform;
            }
        }

        return new WorldTransform(
            new Vec3(posInShip.x, posInShip.y, posInShip.z),
            new Vec3(dirInShip.x, dirInShip.y, dirInShip.z).normalize()
        );
    }

    public static Vec3 worldVectorToLocal(Level level, BlockPos origin, Vec3 worldVector) {
        Vec3 vsVector = transformVsWorldVectorToLocal(level, origin, worldVector, false);
        if (vsVector != null) return vsVector;

        Vec3 sableVector = transformSableWorldVectorToLocal(level, origin, worldVector, false);
        if (sableVector != null) return sableVector;

        return worldVector;
    }

    public static Vec3 localVectorToWorld(Level level, BlockPos origin, Vec3 localVector) {
        Vec3 vsVector = transformVsLocalVectorToWorld(level, origin, localVector, false);
        if (vsVector != null) return vsVector;

        Vec3 sableVector = transformSableLocalVectorToWorld(level, origin, localVector, false);
        if (sableVector != null) return sableVector;

        return localVector;
    }

    private static Object getSableCompanion() {
        if (!isSableLoaded()) return null;
        if (sableCompanion != null) return sableCompanion;

        try {
            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            Field instance = companionClass.getField("INSTANCE");
            sableCompanion = instance.get(null);
            return sableCompanion;
        } catch (Exception e) {
            sableLoaded = false;
            LOGGER.warn("[VS Fluid Link] Sable Companion was detected, but its API could not be initialized. Sable coordinate transforms will be disabled.", e);
            return null;
        }
    }

    private static Object getSableContaining(Level level, BlockPos pos) {
        Object companion = getSableCompanion();
        if (companion == null || level == null || pos == null) return null;

        try {
            Method getContaining = companion.getClass().getMethod("getContaining", Level.class, Vec3i.class);
            return getContaining.invoke(companion, level, pos);
        } catch (Exception e) {
            LOGGER.debug("[VS Fluid Link] Sable getContaining(BlockPos) failed for pos: {}", pos, e);
            return null;
        }
    }

    private static Object getSableContaining(Level level, Vec3 pos) {
        Object companion = getSableCompanion();
        if (companion == null || level == null || pos == null) return null;

        try {
            Method getContaining = companion.getClass().getMethod("getContaining", Level.class, Position.class);
            return getContaining.invoke(companion, level, pos);
        } catch (Exception e) {
            LOGGER.debug("[VS Fluid Link] Sable getContaining(Vec3) failed for pos: {}", pos, e);
            return null;
        }
    }

    private static UUID getSableSubLevelId(Level level, BlockPos pos) {
        Object subLevel = getSableContaining(level, pos);
        if (subLevel == null) return null;

        try {
            Class<?> accessClass = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess");
            Method getUniqueId = accessClass.getMethod("getUniqueId");
            Object id = getUniqueId.invoke(subLevel);
            return id instanceof UUID uuid ? uuid : null;
        } catch (Exception e) {
            LOGGER.debug("[VS Fluid Link] Sable getUniqueId failed for pos: {}", pos, e);
            return null;
        }
    }

    private static Object getSablePose(Object subLevel, boolean render) {
        if (subLevel == null) return null;

        try {
            if (render) {
                try {
                    Class<?> clientAccessClass = Class.forName("dev.ryanhcode.sable.companion.ClientSubLevelAccess");
                    if (clientAccessClass.isInstance(subLevel)) {
                        Method renderPose = clientAccessClass.getMethod("renderPose");
                        return renderPose.invoke(subLevel);
                    }
                } catch (NoSuchMethodException | ClassNotFoundException ignored) {
                    // Fall through to logical pose.
                }
            }

            Class<?> accessClass = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess");
            Method logicalPose = accessClass.getMethod("logicalPose");
            return logicalPose.invoke(subLevel);
        } catch (Exception e) {
            LOGGER.debug("[VS Fluid Link] Sable pose lookup failed.", e);
            return null;
        }
    }

    private static Vec3 transformSablePosition(Object pose, Vec3 localPos) {
        if (pose == null) return null;

        try {
            Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
            Method transformPosition = poseClass.getMethod("transformPosition", Vec3.class);
            Object result = transformPosition.invoke(pose, localPos);
            return result instanceof Vec3 vec ? vec : null;
        } catch (Exception e) {
            LOGGER.debug("[VS Fluid Link] Sable position transform failed.", e);
            return null;
        }
    }

    private static Vec3 transformSableNormal(Object pose, Vec3 localNormal) {
        if (pose == null) return null;

        try {
            Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
            Method transformNormal = poseClass.getMethod("transformNormal", Vec3.class);
            Object result = transformNormal.invoke(pose, localNormal);
            return result instanceof Vec3 vec ? vec : null;
        } catch (Exception e) {
            LOGGER.debug("[VS Fluid Link] Sable normal transform failed.", e);
            return null;
        }
    }

    private static Vec3 transformSableNormalInverse(Object pose, Vec3 worldNormal) {
        if (pose == null) return null;

        try {
            Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
            Method transformNormalInverse = poseClass.getMethod("transformNormalInverse", Vec3.class);
            Object result = transformNormalInverse.invoke(pose, worldNormal);
            return result instanceof Vec3 vec ? vec : null;
        } catch (Exception e) {
            LOGGER.debug("[VS Fluid Link] Sable inverse normal transform failed.", e);
            return null;
        }
    }

    private static Vec3 getSableWorldPos(Level level, Vec3 localPos, boolean render) {
        Object subLevel = getSableContaining(level, localPos);
        Object pose = getSablePose(subLevel, render);
        Vec3 transformed = transformSablePosition(pose, localPos);
        return transformed != null ? transformed : localPos;
    }

    private static WorldTransform getSableWorldTransform(Level level, BlockPos pos, Direction facing, boolean render) {
        Object subLevel = getSableContaining(level, pos);
        Object pose = getSablePose(subLevel, render);
        if (pose == null) return null;

        Vec3 transformedPos = transformSablePosition(pose, Vec3.atCenterOf(pos));
        Vec3 transformedDir = transformSableNormal(pose, new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()));
        if (transformedPos == null || transformedDir == null) return null;

        return new WorldTransform(transformedPos, transformedDir.normalize());
    }

    private static Vec3 transformVsWorldVectorToLocal(Level level, BlockPos origin, Vec3 worldVector, boolean render) {
        Object ship = getShipManagingPos(level, origin);
        if (ship == null) return null;

        try {
            Method getTransform = ship.getClass().getMethod(render ? "getRenderTransform" : "getTransform");
            Object transform = getTransform.invoke(ship);

            Method getWorldToShip = transform.getClass().getMethod("getWorldToShip");
            Object worldToShipMatrix = getWorldToShip.invoke(transform);

            Vector3d result = new Vector3d(worldVector.x, worldVector.y, worldVector.z);
            Class<?> matrix4dcClass = Class.forName("org.joml.Matrix4dc");
            Method transformDirection = matrix4dcClass.getMethod("transformDirection", Vector3d.class);
            transformDirection.invoke(worldToShipMatrix, result);
            return new Vec3(result.x, result.y, result.z);
        } catch (Exception e) {
            LOGGER.debug("[VS Fluid Link] VS inverse vector transform failed for pos: {}", origin, e);
            return null;
        }
    }

    private static Vec3 transformVsLocalVectorToWorld(Level level, BlockPos origin, Vec3 localVector, boolean render) {
        Object ship = getShipManagingPos(level, origin);
        if (ship == null) return null;

        try {
            Method getTransform = ship.getClass().getMethod(render ? "getRenderTransform" : "getTransform");
            Object transform = getTransform.invoke(ship);

            Method getShipToWorld = transform.getClass().getMethod("getShipToWorld");
            Object shipToWorldMatrix = getShipToWorld.invoke(transform);

            Vector3d result = new Vector3d(localVector.x, localVector.y, localVector.z);
            Class<?> matrix4dcClass = Class.forName("org.joml.Matrix4dc");
            Method transformDirection = matrix4dcClass.getMethod("transformDirection", Vector3d.class);
            transformDirection.invoke(shipToWorldMatrix, result);
            return new Vec3(result.x, result.y, result.z);
        } catch (Exception e) {
            LOGGER.debug("[VS Fluid Link] VS vector transform failed for pos: {}", origin, e);
            return null;
        }
    }

    private static Vec3 transformSableWorldVectorToLocal(Level level, BlockPos origin, Vec3 worldVector, boolean render) {
        Object subLevel = getSableContaining(level, origin);
        Object pose = getSablePose(subLevel, render);
        return transformSableNormalInverse(pose, worldVector);
    }

    private static Vec3 transformSableLocalVectorToWorld(Level level, BlockPos origin, Vec3 localVector, boolean render) {
        Object subLevel = getSableContaining(level, origin);
        Object pose = getSablePose(subLevel, render);
        return transformSableNormal(pose, localVector);
    }

    public static class Client {
        public static Vec3 getRenderWorldPos(Level level, BlockPos pos) {
            Vec3 localPos = Vec3.atCenterOf(pos);

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
            return getSableWorldPos(level, localPos, true);
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

            if (ship == null) {
                WorldTransform sableTransform = getSableWorldTransform(level, pos, facing, true);
                if (sableTransform != null) {
                    return sableTransform;
                }
            }

            return new WorldTransform(
                new Vec3(posInShip.x, posInShip.y, posInShip.z),
                new Vec3(dirInShip.x, dirInShip.y, dirInShip.z).normalize()
            );
        }

        public static Vec3 renderWorldVectorToLocal(Level level, BlockPos origin, Vec3 worldVector) {
            Vec3 vsVector = transformVsWorldVectorToLocal(level, origin, worldVector, true);
            if (vsVector != null) return vsVector;

            Vec3 sableVector = transformSableWorldVectorToLocal(level, origin, worldVector, true);
            if (sableVector != null) return sableVector;

            return worldVector;
        }

        public static Vec3 renderLocalVectorToWorld(Level level, BlockPos origin, Vec3 localVector) {
            Vec3 vsVector = transformVsLocalVectorToWorld(level, origin, localVector, true);
            if (vsVector != null) return vsVector;

            Vec3 sableVector = transformSableLocalVectorToWorld(level, origin, localVector, true);
            if (sableVector != null) return sableVector;

            return localVector;
        }
    }
}
