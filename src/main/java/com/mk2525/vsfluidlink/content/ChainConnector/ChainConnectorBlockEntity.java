package com.mk2525.vsfluidlink.content.ChainConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ChainConnectorBlockEntity extends KineticBlockEntity {
    
    private BlockPos targetPos;
    private int checkCooldown = 0;

    public ChainConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ChainConnectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.CHAIN_CONNECTOR.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        VSLinkUtil.registerLinkBlockEntity(this);
    }

    @Override
    public float calculateStressApplied() {
        return 0.0f;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    public void setTargetPos(BlockPos targetPos) {
        if (this.level == null) return;
        
        BlockPos oldTarget = this.targetPos;
        boolean wasLinked = oldTarget != null;
        boolean isLinked = targetPos != null;
        
        this.targetPos = targetPos;
        setChanged();
        
        if (wasLinked != isLinked) {
            BlockState currentState = getBlockState();
            if (currentState.hasProperty(ChainConnectorBlock.LINKED)) {
                level.setBlock(getBlockPos(), currentState.setValue(ChainConnectorBlock.LINKED, isLinked), 3);
            }
        }
        
        if (!level.isClientSide) {
            sendData();
            
            // ネットワーク更新をトリガー
            if (isLinked) {
                attachKinetics();
                if (level.getBlockEntity(targetPos) instanceof ChainConnectorBlockEntity targetBe) {
                    // 相手側も更新
                    targetBe.attachKinetics();
                }
            } else {
                detachKinetics(); // 一度切り離す
                // 再接続のために少し遅延させるか、あるいは即座に再アタッチするか
                // ここでは単純にdetachして、tickで再チェックされるのを待つか、
                // あるいはattachKineticsを呼ぶと、接続先がない状態で再構築される。
                // ただし、removeSource()なども必要かもしれない。
                
                if (oldTarget != null && level.isLoaded(oldTarget)) {
                    if (level.getBlockEntity(oldTarget) instanceof ChainConnectorBlockEntity oldTargetBe) {
                         oldTargetBe.detachKinetics();
                         // oldTargetBe.attachKinetics(); // 必要なら
                    }
                }
            }
        }
    }
    
    public BlockPos getTargetPos() {
        return targetPos;
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

    private Direction.Axis getRotationAxis(BlockState state) {
        Direction facing = getFacing(state);
        if (facing.getAxis().isVertical() || facing.getAxis() == Direction.Axis.Z) {
            return Direction.Axis.X;
        }
        return Direction.Axis.Z;
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
        // 通常の隣接ブロックへの伝播
        super.addPropagationLocations(block, state, neighbours);
        
        // 接続先への伝播を追加
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
        return super.isCustomConnection(other, state, otherState);
    }

    // ------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        if (targetPos != null) {
            if (checkCooldown-- <= 0) {
                checkCooldown = 20;

                if (VSLinkUtil.findReciprocalLink(level, worldPosition, targetPos, ChainConnectorBlockEntity.class, ChainConnectorBlockEntity::getTargetPos) == null) {
                    setTargetPos(null);
                    return;
                }

                Vec3 myPos = VSLinkUtil.getWorldPos(level, worldPosition);
                Vec3 targetPosVec = VSLinkUtil.getWorldPos(level, targetPos);

                double maxDist = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
                if (myPos.distanceToSqr(targetPosVec) > maxDist * maxDist) {
                    BlockPos oldTarget = targetPos;
                    setTargetPos(null);

                    VSLinkUtil.clearReciprocalLink(level, worldPosition, oldTarget, ChainConnectorBlockEntity.class, ChainConnectorBlockEntity::getTargetPos, ChainConnectorBlockEntity::setTargetPos);

                    level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
                    return;
                }
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (targetPos != null) {
            tag.put("TargetPos", NbtUtils.writeBlockPos(targetPos));
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        if (tag.contains("TargetPos")) {
            targetPos = NbtUtils.readBlockPos(tag.getCompound("TargetPos"));
        } else {
            targetPos = null;
        }
    }
    
    @Override
    public void remove() {
        VSLinkUtil.unregisterLinkBlockEntity(this);
        // ブロックが撤去されたときに接続を切る
        if (targetPos != null && level != null && !level.isClientSide) {
             VSLinkUtil.clearReciprocalLink(level, worldPosition, targetPos, ChainConnectorBlockEntity.class, ChainConnectorBlockEntity::getTargetPos, ChainConnectorBlockEntity::setTargetPos);
        }
        super.remove();
    }
}
