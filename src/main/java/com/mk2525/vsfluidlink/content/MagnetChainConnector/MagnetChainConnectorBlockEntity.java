package com.mk2525.vsfluidlink.content.MagnetChainConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MagnetChainConnectorBlockEntity extends DirectionalShaftHalvesBlockEntity implements IRotate {

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public net.minecraft.core.Direction.Axis getRotationAxis(BlockState state) {
        if (state.getBlock() instanceof MagnetChainConnectorBlock block) {
            return block.getRotationAxis(state);
        }
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return state.getBlock() instanceof MagnetChainConnectorBlock block && face.getAxis() == block.getRotationAxis(state);
    }

    private static final Set<BlockPos> ALL_CONNECTORS = Collections.synchronizedSet(new HashSet<>());

    private BlockPos targetPos;
    private int scanCooldown = 0;
    private int checkCooldown = 0;

    public MagnetChainConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public MagnetChainConnectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.MAGNET_CHAIN_CONNECTOR.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            ALL_CONNECTORS.add(getBlockPos());
        }
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) {
            ALL_CONNECTORS.remove(getBlockPos());
            if (targetPos != null) {
                BlockPos oldTarget = targetPos;
                if (level.isLoaded(oldTarget)) {
                    BlockEntity targetBe = level.getBlockEntity(oldTarget);
                    if (targetBe instanceof MagnetChainConnectorBlockEntity targetLinkBe) {
                        targetLinkBe.setTargetPos(null);
                    }
                }
            }
        }
        super.remove();
    }

    @Override
    public float calculateStressApplied() {
        return 0.0f;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(
            getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(),
            getBlockPos().getX() + 1, getBlockPos().getY() + 1, getBlockPos().getZ() + 1
        );
    }

    public void setTargetPos(BlockPos targetPos) {
        if (this.level == null) return;

        BlockPos oldTarget = this.targetPos;
        boolean wasLinked = oldTarget != null;
        boolean isLinked = targetPos != null;

        this.targetPos = targetPos;
        setChanged();

        if (!isRemoved()) {
            if (wasLinked != isLinked) {
                BlockState currentState = getBlockState();
                if (currentState.hasProperty(MagnetChainConnectorBlock.LINKED)) {
                    level.setBlock(getBlockPos(), currentState.setValue(MagnetChainConnectorBlock.LINKED, isLinked), 3);
                }
            }
        }

        if (!level.isClientSide) {
            sendData();
            refreshKineticConnection(level, oldTarget);
            refreshKineticConnection(level, targetPos);
            refreshOwnKinetics();

            if (isLinked) {
                // TODO: Was calling attachKinetics() for Create kinetic network integration.
                // KineticBlockEntity-specific methods no longer available after extending SmartBlockEntity.
                if (level.getBlockEntity(targetPos) instanceof MagnetChainConnectorBlockEntity targetBe) {
                    // TODO: Was calling targetBe.attachKinetics() here as well.
                }
            } else {
                // TODO: Was calling detachKinetics() for Create kinetic network integration.

                if (oldTarget != null && level.isLoaded(oldTarget)) {
                     if (level.getBlockEntity(oldTarget) instanceof MagnetChainConnectorBlockEntity oldTargetBe) {
                         // TODO: Was calling oldTargetBe.detachKinetics() here.
                     }
                 }
            }
        }
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    private static void refreshKineticConnection(Level level, BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof MagnetChainConnectorBlockEntity connector) {
            connector.refreshOwnKinetics();
        }
    }

    private void refreshOwnKinetics() {
        if (level == null || level.isClientSide || isRemoved()) {
            return;
        }
        detachKinetics();
        removeSource();
        attachKinetics();
        sendData();
    }

    public void disconnect() {
        if (targetPos != null) {
            BlockPos oldTarget = targetPos;
            setTargetPos(null);
            if (level.isLoaded(oldTarget)) {
                if (level.getBlockEntity(oldTarget) instanceof MagnetChainConnectorBlockEntity targetBe) {
                    targetBe.setTargetPos(null);
                }
            }
            level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
        }
    }

    // --- Create Kinetic Network Integration ---

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        if (targetPos != null && target.getBlockPos().equals(targetPos)) {
            return getChainRotationModifier(target, stateFrom, stateTo);
        }
        return 0.0f;
    }

    private float getChainRotationModifier(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo) {
        if (level == null) {
            return 1.0f;
        }

        Vec3 fromWorld = VSLinkUtil.getWorldPos(level, worldPosition);
        Vec3 toWorld = VSLinkUtil.getWorldPos(level, target.getBlockPos());
        Vec3 worldConnection = toWorld.subtract(fromWorld);
        if (worldConnection.lengthSqr() < 1.0e-6) {
            return 1.0f;
        }

        Vec3 fromConnection = VSLinkUtil.worldVectorToLocal(level, worldPosition, worldConnection);
        Vec3 toConnection = VSLinkUtil.worldVectorToLocal(level, target.getBlockPos(), worldConnection.scale(-1));
        Vec3 fromAnchor = getAnchorVector(stateFrom, fromConnection);
        Vec3 toAnchor = getAnchorVector(stateTo, toConnection);
        double fromSign = getTangentialMovementSign(stateFrom, fromConnection, fromAnchor);
        double toSign = getTangentialMovementSign(stateTo, toConnection, toAnchor);
        if (Math.abs(fromSign) < 1.0e-6 || Math.abs(toSign) < 1.0e-6) {
            return 1.0f;
        }
        if (usesSwappedAnchorPair(worldConnection, fromAnchor, target.getBlockPos(), toAnchor)) {
            toSign = -toSign;
        }
        return fromSign * toSign >= 0 ? -1.0f : 1.0f;
    }

    private double getTangentialMovementSign(BlockState state, Vec3 connection, Vec3 anchor) {
        Vec3 shaft = axisVector(getRotationAxis(state));
        if (connection.lengthSqr() < 1.0e-6 || anchor.lengthSqr() < 1.0e-6) {
            return 0.0;
        }
        return cross(shaft, anchor.normalize()).dot(connection.normalize());
    }

    private boolean usesSwappedAnchorPair(Vec3 worldConnection, Vec3 fromAnchorLocal, BlockPos targetPos, Vec3 toAnchorLocal) {
        Vec3 fromAnchorWorld = VSLinkUtil.localVectorToWorld(level, worldPosition, fromAnchorLocal);
        Vec3 toAnchorWorld = VSLinkUtil.localVectorToWorld(level, targetPos, toAnchorLocal);
        double direct = worldConnection.add(toAnchorWorld).subtract(fromAnchorWorld).lengthSqr()
                + worldConnection.subtract(toAnchorWorld).add(fromAnchorWorld).lengthSqr();
        double swapped = worldConnection.subtract(toAnchorWorld).subtract(fromAnchorWorld).lengthSqr()
                + worldConnection.add(toAnchorWorld).add(fromAnchorWorld).lengthSqr();
        return swapped + 1.0e-6 < direct;
    }

    private Vec3 getAnchorVector(BlockState state, Vec3 connection) {
        Vec3 shaft = axisVector(getRotationAxis(state));
        Vec3 preferred = getPreferredAnchorVector(state, shaft);
        if (connection.lengthSqr() < 1.0e-6) {
            return preferred;
        }

        Vec3 anchor = cross(shaft, connection.normalize());
        if (anchor.lengthSqr() < 1.0e-6) {
            return preferred;
        }

        anchor = anchor.normalize();
        return anchor.dot(preferred) < 0 ? anchor.scale(-1) : anchor;
    }

    private Vec3 getPreferredAnchorVector(BlockState state, Vec3 shaft) {
        Vec3 preferred = directionVector(getModelSide(state));
        Vec3 projected = preferred.subtract(shaft.scale(preferred.dot(shaft)));
        if (projected.lengthSqr() < 1.0e-6) {
            Vec3 up = directionVector(getModelUp(state));
            projected = up.subtract(shaft.scale(up.dot(shaft)));
        }
        return projected.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : projected.normalize();
    }

    private Vec3 directionVector(Direction direction) {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private Vec3 axisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3(1, 0, 0);
            case Y -> new Vec3(0, 1, 0);
            case Z -> new Vec3(0, 0, 1);
        };
    }

    private Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
        );
    }

    private Direction getModelUp(BlockState state) {
        Direction facing = getFacing(state);
        return switch (facing) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    private Direction getModelSide(BlockState state) {
        Direction facing = getFacing(state);
        return switch (facing.getAxis()) {
            case X -> Direction.SOUTH;
            case Z, Y -> Direction.EAST;
        };
    }

    private Direction getFacing(BlockState state) {
        if (state.getBlock() instanceof MagnetChainConnectorBlock) {
            return state.getValue(MagnetChainConnectorBlock.FACING);
        }
        if (state.getBlock() instanceof com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlock) {
            return state.getValue(com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlock.FACING);
        }
        return Direction.NORTH;
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        // super.addPropagationLocations(block, state, neighbours);
        if (targetPos != null) {
            neighbours.add(targetPos);
        }
        return neighbours;
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        if (targetPos != null && other.getBlockPos().equals(targetPos)) {
            return true;
        }
        // return super.isCustomConnection(other, state, otherState);
        return false;
    }

    // ------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        if (targetPos != null) {
            if (checkCooldown-- <= 0) {
                checkCooldown = 20;

                if (!level.isLoaded(targetPos) || !(level.getBlockEntity(targetPos) instanceof MagnetChainConnectorBlockEntity)) {
                    setTargetPos(null);
                    return;
                }

                BlockEntity targetBe = level.getBlockEntity(targetPos);
                if (!(targetBe instanceof MagnetChainConnectorBlockEntity)) {
                    setTargetPos(null);
                    return;
                }

                Vec3 myPos = VSLinkUtil.getWorldPos(level, worldPosition);
                Vec3 targetPosVec = VSLinkUtil.getWorldPos(level, targetPos);

                double maxDist = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
                if (myPos.distanceToSqr(targetPosVec) > maxDist * maxDist) {
                    disconnect();
                    return;
                }

                if (level.hasNeighborSignal(worldPosition)) {
                    disconnect();
                    return;
                }

                // 角度チェック (0.800未満 = 約37度以上ずれたら切断)
                // dot積が 1.0 に近いほど平行。
                // 0.800 を下回ったら切断。
                // ただし、対向しているので -1.0 に近いほど良い。
                // つまり、dot < -0.800 ならOK。 dot > -0.800 なら切断。

                Direction myFacing = getBlockState().getValue(MagnetChainConnectorBlock.FACING);
                BlockState targetState = targetBe.getBlockState();
                if (!(targetState.getBlock() instanceof MagnetChainConnectorBlock)) {
                    disconnect();
                    return;
                }
                Direction targetFacing = targetState.getValue(MagnetChainConnectorBlock.FACING);

                VSLinkUtil.WorldTransform myTransform = VSLinkUtil.getWorldTransform(level, worldPosition, myFacing);
                VSLinkUtil.WorldTransform targetTransform = VSLinkUtil.getWorldTransform(level, targetPos, targetFacing);

                double dot = myTransform.direction.dot(targetTransform.direction);

                if (dot > -0.800) { // 角度が開きすぎた場合
                    disconnect();
                    return;
                }
            }
        } else {
            if (scanCooldown-- <= 0) {
                scanCooldown = 40; // 2秒に1回スキャン

                if (level.hasNeighborSignal(worldPosition)) return;

                Direction facing = getBlockState().getValue(MagnetChainConnectorBlock.FACING);
                AABB scanBox = getScanBoxInWorld(level, worldPosition, facing);

                synchronized (ALL_CONNECTORS) {
                    for (BlockPos scanPos : new ArrayList<>(ALL_CONNECTORS)) {
                        if (scanPos.equals(worldPosition)) continue;
                        if (!level.isLoaded(scanPos)) continue;

                        Vec3 targetWorldPos = VSLinkUtil.getWorldPos(level, scanPos);
                        if (!scanBox.contains(targetWorldPos)) continue;

                        if (isConnectorAt(level, worldPosition, facing, scanPos)) {
                            setTargetPos(scanPos);
                            break; // Found a target, stop scanning
                        }
                    }
                }
            }
        }
    }

    private static boolean isConnectorAt(Level level, BlockPos selfPos, Direction selfFacing, BlockPos scanPos) {
        if (!level.isLoaded(scanPos)) return false;

        BlockEntity be = level.getBlockEntity(scanPos);
        if (!(be instanceof MagnetChainConnectorBlockEntity targetConnector)) return false;

        if (targetConnector.getTargetPos() != null) return false;

        if (level.hasNeighborSignal(scanPos)) return false;

        Long shipId1 = VSLinkUtil.getShipId(level, selfPos);
        Long shipId2 = VSLinkUtil.getShipId(level, scanPos);

        boolean isWorldToWorld = shipId1 == null && shipId2 == null;
        boolean isIntraShip = shipId1 != null && shipId1.equals(shipId2);

        if (isWorldToWorld && VsFluidLinkConfig.SERVER.restrictWorldToWorldConnection.get()) {
            return false;
        }

        if (isIntraShip && VsFluidLinkConfig.SERVER.restrictIntraShipConnection.get()) {
            return false;
        }

        BlockState scanState = be.getBlockState();
        if (areFacingsOppositeInWorld(level, selfPos, selfFacing, scanPos, scanState.getValue(MagnetChainConnectorBlock.FACING))) {
            level.playSound(null, selfPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
            level.playSound(null, scanPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);

            targetConnector.setTargetPos(selfPos);
            return true;
        }
        return false;
    }

    private static AABB getScanBoxInWorld(Level level, BlockPos pos, Direction facing) {
        int scanDist = VsFluidLinkConfig.SERVER.magnetScanDistance.get();
        int scanRadius = VsFluidLinkConfig.SERVER.magnetScanRadius.get();

        Vec3 start = VSLinkUtil.getWorldPos(level, pos.relative(facing, 1));
        Vec3 end = VSLinkUtil.getWorldPos(level, pos.relative(facing, scanDist));
        AABB box = new AABB(start, end).minmax(new AABB(end, start));

        switch (facing.getAxis()) {
            case X -> box = box.inflate(0, scanRadius, scanRadius);
            case Y -> box = box.inflate(scanRadius, 0, scanRadius);
            case Z -> box = box.inflate(scanRadius, scanRadius, 0);
        }
        return box;
    }

    private static boolean areFacingsOppositeInWorld(Level level, BlockPos pos1, Direction facing1, BlockPos pos2, Direction facing2) {
        VSLinkUtil.WorldTransform transform1 = VSLinkUtil.getWorldTransform(level, pos1, facing1);
        VSLinkUtil.WorldTransform transform2 = VSLinkUtil.getWorldTransform(level, pos2, facing2);

        return transform1.direction.dot(transform2.direction) < -0.890; // approx 170 degrees
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (targetPos != null) {
            CompoundTag targetTag = new CompoundTag();
            targetTag.putInt("x", targetPos.getX());
            targetTag.putInt("y", targetPos.getY());
            targetTag.putInt("z", targetPos.getZ());
            tag.put("TargetPos", targetTag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("TargetPos")) {
            CompoundTag targetTag = tag.getCompound("TargetPos");
            targetPos = new BlockPos(targetTag.getInt("x"), targetTag.getInt("y"), targetTag.getInt("z"));
        } else {
            targetPos = null;
        }
    }
}
