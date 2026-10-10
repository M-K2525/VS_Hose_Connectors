package com.mk2525.vsfluidlink.content.ItemHoseConnecotor;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.content.LinkActivity;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mk2525.vsfluidlink.util.ConnectorItemBuffer;
import com.mojang.logging.LogUtils;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ItemHoseConnectorBlockEntity extends KineticBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    protected final ConnectorItemBuffer inventory = new ConnectorItemBuffer() {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };

    protected final LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> inventory);
    
    private BlockPos targetPos;
    private int checkCooldown = 0;

    public ItemHoseConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemHoseConnectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ITEM_HOSE_CONNECTOR.get(), pos, state);
    }

    @Override
    public void onLoad() {
        inventory.applyConfiguredSize();
        super.onLoad();
        VSLinkUtil.registerLinkBlockEntity(this);
    }

    @Override
    public float calculateStressApplied() {
        return 2.0f;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Override
    public void remove() {
        VSLinkUtil.unregisterLinkBlockEntity(this);
        super.remove();
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
            if (currentState.hasProperty(ItemHoseConnectorBlock.LINKED)) {
                level.setBlock(getBlockPos(), currentState.setValue(ItemHoseConnectorBlock.LINKED, isLinked), 3);
            }
        }
        
        if (!level.isClientSide) {
            sendData();
            updateOwnActivity();
            if (isLinked) {
                ItemHoseConnectorBlockEntity targetBe = VSLinkUtil.findReciprocalLink(level, worldPosition, targetPos, ItemHoseConnectorBlockEntity.class, ItemHoseConnectorBlockEntity::getTargetPos);
                if (targetBe != null) {
                    targetBe.updateOwnActivity();
                }
            }
            if (wasLinked) {
                 ItemHoseConnectorBlockEntity oldTargetBe = VSLinkUtil.findReciprocalLink(level, worldPosition, oldTarget, ItemHoseConnectorBlockEntity.class, ItemHoseConnectorBlockEntity::getTargetPos);
                 if (oldTargetBe != null) {
                    oldTargetBe.updateOwnActivity();
                }
            }
        }
    }
    
    public BlockPos getTargetPos() {
        return targetPos;
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
        if (level != null && !level.isClientSide) {
            updateOwnActivity();
            if (targetPos != null) {
                ItemHoseConnectorBlockEntity targetBe = VSLinkUtil.findReciprocalLink(level, worldPosition, targetPos, ItemHoseConnectorBlockEntity.class, ItemHoseConnectorBlockEntity::getTargetPos);
                if (targetBe != null) {
                    targetBe.updateOwnActivity();
                }
            }
        }
    }

    public void updateOwnActivity() {
        if (level == null || level.isClientSide) return;

        LinkActivity activity = LinkActivity.NONE;
        float mySpeed = Math.abs(getSpeed());

        if (targetPos != null) {
            ItemHoseConnectorBlockEntity targetBe = VSLinkUtil.findReciprocalLink(level, worldPosition, targetPos, ItemHoseConnectorBlockEntity.class, ItemHoseConnectorBlockEntity::getTargetPos);
            if (targetBe != null) {
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

        if (targetPos != null) {
            if (checkCooldown-- <= 0) {
                checkCooldown = 20; // 1秒に1回チェック

                if (VSLinkUtil.findReciprocalLink(level, worldPosition, targetPos, ItemHoseConnectorBlockEntity.class, ItemHoseConnectorBlockEntity::getTargetPos) == null) {
                    setTargetPos(null);
                    return;
                }

                Vec3 myPos = VSLinkUtil.getWorldPos(level, worldPosition);
                Vec3 targetPosVec = VSLinkUtil.getWorldPos(level, targetPos);

                double maxDist = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
                if (myPos.distanceToSqr(targetPosVec) > maxDist * maxDist) {
                    BlockPos oldTarget = targetPos;
                    setTargetPos(null);

                    VSLinkUtil.clearReciprocalLink(level, worldPosition, oldTarget, ItemHoseConnectorBlockEntity.class, ItemHoseConnectorBlockEntity::getTargetPos, ItemHoseConnectorBlockEntity::setTargetPos);

                    level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
                    return;
                }
            }
        }

        if (targetPos != null && getBlockState().getValue(ItemHoseConnectorBlock.ACTIVITY) == LinkActivity.SEND) {
            ItemHoseConnectorBlockEntity targetLink = VSLinkUtil.findReciprocalLink(level, worldPosition, targetPos, ItemHoseConnectorBlockEntity.class, ItemHoseConnectorBlockEntity::getTargetPos);
            if (targetLink != null) {
                inventory.transferTo(targetLink.inventory);
            }
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("Inventory", inventory.serializeNBT());
        if (targetPos != null) {
            tag.put("TargetPos", NbtUtils.writeBlockPos(targetPos));
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        if (tag.contains("TargetPos")) {
            targetPos = NbtUtils.readBlockPos(tag.getCompound("TargetPos"));
        } else {
            targetPos = null;
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
