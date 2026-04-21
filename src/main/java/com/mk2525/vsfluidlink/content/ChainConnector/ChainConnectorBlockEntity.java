package com.mk2525.vsfluidlink.content.ChainConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
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

public class ChainConnectorBlockEntity extends KineticBlockEntity implements IRotate {

    private BlockPos targetPos;
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
            refreshKineticConnection(level, oldTarget);
            refreshKineticConnection(level, targetPos);
            refreshOwnKinetics();

            if (isLinked) {
                // ネットワーク更新
                if (level.getBlockEntity(targetPos) instanceof ChainConnectorBlockEntity targetBe) {
                    // 相手側も更新
                }
            } else {
                if (oldTarget != null && level.isLoaded(oldTarget)) {
                    if (level.getBlockEntity(oldTarget) instanceof ChainConnectorBlockEntity oldTargetBe) {
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
        if (level.getBlockEntity(pos) instanceof ChainConnectorBlockEntity connector) {
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
        if (targetPos != null && target.getBlockPos().equals(targetPos)) {
            return 1.0f; // 回転比率 1:1
        }
        return 0.0f;
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
        if (targetPos != null && other.getBlockPos().equals(targetPos)) {
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

                if (!level.isLoaded(targetPos) || !(level.getBlockEntity(targetPos) instanceof ChainConnectorBlockEntity)) {
                    setTargetPos(null);
                    return;
                }

                Vec3 myPos = VSLinkUtil.getWorldPos(level, worldPosition);
                Vec3 targetPosVec = VSLinkUtil.getWorldPos(level, targetPos);

                double maxDist = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
                if (myPos.distanceToSqr(targetPosVec) > maxDist * maxDist) {
                    BlockPos oldTarget = targetPos;
                    setTargetPos(null);

                    if (level.isLoaded(oldTarget)) {
                        if (level.getBlockEntity(oldTarget) instanceof ChainConnectorBlockEntity targetLink) {
                            targetLink.setTargetPos(null);
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

    @Override
    public void remove() {
        // ブロックが撤去されたときに接続を切る
        if (targetPos != null && level != null && !level.isClientSide) {
             if (level.isLoaded(targetPos)) {
                 if (level.getBlockEntity(targetPos) instanceof ChainConnectorBlockEntity targetBe) {
                     targetBe.setTargetPos(null);
                 }
             }
        }
        super.remove();
    }
}
