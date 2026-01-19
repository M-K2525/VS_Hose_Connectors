package com.mk2525.vsfluidlink.content.ChainConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
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
        this(ModBlockEntities.BELT_CONNECTOR.get(), pos, state);
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
            return 1.0f; // 回転比率 1:1
        }
        return 0.0f;
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
