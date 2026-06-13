package com.mk2525.vsfluidlink.content.ChainConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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

import java.util.List;

public class ChainConnectorBlockEntity extends DirectionalShaftHalvesBlockEntity implements IRotate {

    private BlockPos targetPos;
    private Long targetSpaceId;
    private int checkCooldown = 0;

    public ChainConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ChainConnectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.CHAIN_CONNECTOR.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
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

    public void setTarget(BlockPos targetPos, Long targetSpaceId) {
        if (this.level == null) return;

        BlockPos oldTarget = this.targetPos;
        boolean wasLinked = oldTarget != null;
        boolean isLinked = targetPos != null;

        this.targetPos = targetPos;
        this.targetSpaceId = targetPos == null ? null : targetSpaceId;
        setChanged();

        if (wasLinked != isLinked) {
            BlockState currentState = getBlockState();
            if (currentState.hasProperty(ChainConnectorBlock.LINKED)) {
                level.setBlock(getBlockPos(), currentState.setValue(ChainConnectorBlock.LINKED, isLinked), 3);
            }
        }

        if (!level.isClientSide) {
            sendData();
            refreshKineticConnection(level, oldTarget);
            refreshKineticConnection(level, targetPos);
            refreshOwnKinetics();

            if (isLinked) {
                // ネットワーク更新
                if (VSLinkUtil.resolveBlockEntity(level, targetPos, this.targetSpaceId, ChainConnectorBlockEntity.class) instanceof ChainConnectorBlockEntity targetBe) {
                    // 相手側も更新
                }
            } else {
                if (oldTarget != null && level.isLoaded(oldTarget)) {
                    if (VSLinkUtil.resolveBlockEntity(level, oldTarget, VSLinkUtil.getSpatialId(level, oldTarget), ChainConnectorBlockEntity.class) instanceof ChainConnectorBlockEntity oldTargetBe) {
                    }
                }
            }
        }
    }

    public void setTargetPos(BlockPos targetPos) {
        setTarget(targetPos, targetPos == null || level == null ? null : VSLinkUtil.getSpatialId(level, targetPos));
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public Long getTargetSpaceId() {
        return targetSpaceId;
    }

    private static void refreshKineticConnection(Level level, BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        if (VSLinkUtil.resolveBlockEntity(level, pos, VSLinkUtil.getSpatialId(level, pos), ChainConnectorBlockEntity.class) instanceof ChainConnectorBlockEntity connector) {
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

    // --- Create Kinetic Network Integration ---

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        if (targetPos != null && VSLinkUtil.isSameEndpoint(target, targetPos, targetSpaceId)) {
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
        if (state.getBlock() instanceof ChainConnectorBlock) {
            return state.getValue(ChainConnectorBlock.FACING);
        }
        if (state.getBlock() instanceof com.mk2525.vsfluidlink.content.MagnetChainConnector.MagnetChainConnectorBlock) {
            return state.getValue(com.mk2525.vsfluidlink.content.MagnetChainConnector.MagnetChainConnectorBlock.FACING);
        }
        return Direction.NORTH;
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        // 接続先への伝播を追加
        if (targetPos != null) {
            neighbours.add(targetPos);
        }
        return neighbours;
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        if (targetPos != null && VSLinkUtil.isSameEndpoint(other, targetPos, targetSpaceId)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return state.getBlock() instanceof ChainConnectorBlock block && face.getAxis() == block.getRotationAxis(state);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        if (state.getBlock() instanceof ChainConnectorBlock block) {
            return block.getRotationAxis(state);
        }
        return Direction.Axis.Y;
    }

    // ------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        if (targetPos != null) {
            if (checkCooldown-- <= 0) {
                checkCooldown = 20;

                if (!level.isLoaded(targetPos) || VSLinkUtil.resolveBlockEntity(level, targetPos, targetSpaceId, ChainConnectorBlockEntity.class) == null) {
                    setTarget(null, null);
                    return;
                }

                Vec3 myPos = VSLinkUtil.getWorldPos(level, worldPosition);
                Vec3 targetPosVec = VSLinkUtil.getWorldPos(level, targetPos);

                double maxDist = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
                if (myPos.distanceToSqr(targetPosVec) > maxDist * maxDist) {
                    BlockPos oldTarget = targetPos;
                    setTarget(null, null);

                    if (level.isLoaded(oldTarget)) {
                        if (VSLinkUtil.resolveBlockEntity(level, oldTarget, VSLinkUtil.getSpatialId(level, oldTarget), ChainConnectorBlockEntity.class) instanceof ChainConnectorBlockEntity targetLink) {
                            targetLink.setTarget(null, null);
                        }
                    }

                    level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
                    return;
                }
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (targetPos != null) {
            tag.put("TargetPos", new com.mk2525.vsfluidlink.util.LinkTarget(targetPos, targetSpaceId).toTag());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("TargetPos")) {
            CompoundTag targetTag = tag.getCompound("TargetPos");
            com.mk2525.vsfluidlink.util.LinkTarget target = com.mk2525.vsfluidlink.util.LinkTarget.fromTag(targetTag);
            targetPos = target.pos();
            targetSpaceId = target.spaceId();
        } else {
            targetPos = null;
            targetSpaceId = null;
        }
    }

    @Override
    public void remove() {
        // ブロックが撤去されたときに接続を切る
        if (targetPos != null && level != null && !level.isClientSide) {
             if (level.isLoaded(targetPos)) {
                 if (VSLinkUtil.resolveBlockEntity(level, targetPos, targetSpaceId, ChainConnectorBlockEntity.class) instanceof ChainConnectorBlockEntity targetBe) {
                     targetBe.setTarget(null, null);
                 }
             }
        }
        super.remove();
    }
}
