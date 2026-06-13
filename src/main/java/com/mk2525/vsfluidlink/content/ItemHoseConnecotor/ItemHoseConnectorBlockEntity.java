package com.mk2525.vsfluidlink.content.ItemHoseConnecotor;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.content.LinkActivity;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;

public class ItemHoseConnectorBlockEntity extends KineticBlockEntity {

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }
    private static final Logger LOGGER = LogUtils.getLogger();

    protected final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };

    private BlockPos targetPos;
    private Long targetSpaceId;
    private int checkCooldown = 0;

    public ItemHoseConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemHoseConnectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ITEM_HOSE_CONNECTOR.get(), pos, state);
    }

    @Override
    public float calculateStressApplied() {
        return 2.0f;
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
            if (currentState.hasProperty(ItemHoseConnectorBlock.LINKED)) {
                level.setBlock(getBlockPos(), currentState.setValue(ItemHoseConnectorBlock.LINKED, isLinked), 3);
            }
        }

        if (!level.isClientSide) {
            sendData();
            updateOwnActivity();
            if (isLinked) {
                if (VSLinkUtil.resolveBlockEntity(level, targetPos, this.targetSpaceId, ItemHoseConnectorBlockEntity.class) instanceof ItemHoseConnectorBlockEntity targetBe) {
                    targetBe.updateOwnActivity();
                }
            }
            if (wasLinked) {
                 if (oldTarget != null && VSLinkUtil.resolveBlockEntity(level, oldTarget, VSLinkUtil.getSpatialId(level, oldTarget), ItemHoseConnectorBlockEntity.class) instanceof ItemHoseConnectorBlockEntity oldTargetBe) {
                    oldTargetBe.updateOwnActivity();
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

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
    }

    public void updateOwnActivity() {
        if (level == null || level.isClientSide) return;

        LinkActivity activity = LinkActivity.NONE;
        float mySpeed = Math.abs(getSpeed());

        if (targetPos != null && level.isLoaded(targetPos)) {
            if (VSLinkUtil.resolveBlockEntity(level, targetPos, targetSpaceId, ItemHoseConnectorBlockEntity.class) instanceof ItemHoseConnectorBlockEntity targetBe) {
                float targetSpeed = Math.abs(targetBe.getSpeed());
                if (mySpeed >= 32 && targetSpeed < 32) {
                    activity = LinkActivity.SEND;
                } else if (mySpeed < 32 && targetSpeed >= 32) {
                    activity = LinkActivity.RECEIVE;
                }
            }
        }

        BlockState currentState = getBlockState();
        if (currentState.getValue(ItemHoseConnectorBlock.ACTIVITY) != activity) {
            level.setBlock(getBlockPos(), currentState.setValue(ItemHoseConnectorBlock.ACTIVITY, activity), 3);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) return;

        updateOwnActivity();

        if (targetPos != null) {
            if (checkCooldown-- <= 0) {
                checkCooldown = 20; // 1秒に1回チェック

                if (!level.isLoaded(targetPos) || VSLinkUtil.resolveBlockEntity(level, targetPos, targetSpaceId, ItemHoseConnectorBlockEntity.class) == null) {
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
                        if (VSLinkUtil.resolveBlockEntity(level, oldTarget, VSLinkUtil.getSpatialId(level, oldTarget), ItemHoseConnectorBlockEntity.class) instanceof ItemHoseConnectorBlockEntity targetLink) {
                            targetLink.setTarget(null, null);
                        }
                    }

                    level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
                    return;
                }
            }
        }

        if (targetPos != null && getBlockState().getValue(ItemHoseConnectorBlock.ACTIVITY) == LinkActivity.SEND) {
            if (VSLinkUtil.resolveBlockEntity(level, targetPos, targetSpaceId, ItemHoseConnectorBlockEntity.class) instanceof ItemHoseConnectorBlockEntity targetLink) {
                ItemStack myStack = inventory.getStackInSlot(0);
                if (!myStack.isEmpty()) {
                    ItemStack remaining = targetLink.inventory.insertItem(0, myStack, false);
                    inventory.setStackInSlot(0, remaining);
                }
            }
        }
    }

   @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Inventory", inventory.serializeNBT(registries));
        if (targetPos != null) {
            tag.put("TargetPos", new com.mk2525.vsfluidlink.util.LinkTarget(targetPos, targetSpaceId).toTag());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
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

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Override
    public boolean isSpeedRequirementFulfilled() {
        return Math.abs(getSpeed()) >= 32;
    }
}
