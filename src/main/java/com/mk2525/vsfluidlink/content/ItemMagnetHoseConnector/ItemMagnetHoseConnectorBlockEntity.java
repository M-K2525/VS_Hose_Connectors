package com.mk2525.vsfluidlink.content.ItemMagnetHoseConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.content.LinkActivity;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ItemMagnetHoseConnectorBlockEntity extends KineticBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final Set<BlockPos> ALL_CONNECTORS = Collections.synchronizedSet(new HashSet<>());
    
    protected final ItemStackHandler inventory = new ItemStackHandler(1) {
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
    private int scanCooldown = 0;

    public ItemMagnetHoseConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemMagnetHoseConnectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ITEM_MAGNET_HOSE_CONNECTOR.get(), pos, state);
    }

    @Override
    public float calculateStressApplied() {
        return 2.0f;
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
        super.remove();
        if (level != null && !level.isClientSide) {
            ALL_CONNECTORS.remove(getBlockPos());
        }
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
            if (currentState.hasProperty(ItemMagnetHoseConnectorBlock.LINKED)) {
                level.setBlock(getBlockPos(), currentState.setValue(ItemMagnetHoseConnectorBlock.LINKED, isLinked), 3);
            }
        }
        
        if (!level.isClientSide) {
            sendData();
            updateOwnActivity();
            if (isLinked) {
                if (level.getBlockEntity(targetPos) instanceof ItemMagnetHoseConnectorBlockEntity targetBe) {
                    targetBe.updateOwnActivity();
                }
            }
            if (wasLinked) {
                 if (level.getBlockEntity(oldTarget) instanceof ItemMagnetHoseConnectorBlockEntity oldTargetBe) {
                    oldTargetBe.updateOwnActivity();
                }
            }
        }
    }
    
    public BlockPos getTargetPos() {
        return targetPos;
    }

    public VSLinkUtil.WorldTransform getWorldTransform(boolean isClient) {
        if (level == null) return new VSLinkUtil.WorldTransform(Vec3.atCenterOf(worldPosition), Vec3.ZERO);
        Direction facing = getBlockState().getValue(ItemMagnetHoseConnectorBlock.FACING);
        if (isClient) {
            return VSLinkUtil.Client.getRenderWorldTransform(level, worldPosition, facing);
        } else {
            return VSLinkUtil.getWorldTransform(level, worldPosition, facing);
        }
    }
    
    public static Vec3 getWorldPos(Level level, BlockPos pos, boolean isClient) {
        if (level == null) return Vec3.atCenterOf(pos);
        if (isClient) {
            return VSLinkUtil.Client.getRenderWorldPos(level, pos);
        } else {
            return VSLinkUtil.getWorldPos(level, pos);
        }
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
        if (level != null && !level.isClientSide) {
            updateOwnActivity();
            if (targetPos != null && level.getBlockEntity(targetPos) instanceof ItemMagnetHoseConnectorBlockEntity targetBe) {
                targetBe.updateOwnActivity();
            }
        }
    }

    public void updateOwnActivity() {
        if (level == null || level.isClientSide) return;

        LinkActivity activity = LinkActivity.NONE;
        float mySpeed = Math.abs(getSpeed());

        if (targetPos != null && level.isLoaded(targetPos)) {
            if (level.getBlockEntity(targetPos) instanceof ItemMagnetHoseConnectorBlockEntity targetBe) {
                float targetSpeed = Math.abs(targetBe.getSpeed());
                if (mySpeed >= 32 && targetSpeed < 32) {
                    activity = LinkActivity.SEND;
                } else if (mySpeed < 32 && targetSpeed >= 32) {
                    activity = LinkActivity.RECEIVE;
                }
            }
        }

        BlockState currentState = getBlockState();
        if (currentState.getValue(ItemMagnetHoseConnectorBlock.ACTIVITY) != activity) {
            level.setBlock(getBlockPos(), currentState.setValue(ItemMagnetHoseConnectorBlock.ACTIVITY, activity), 3);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) return;
        
        BlockState state = getBlockState();
        boolean isPowered = state.getValue(ItemMagnetHoseConnectorBlock.POWERED);
        
        if (targetPos != null) {
            if (scanCooldown-- <= 0) {
                scanCooldown = 20;
                if (isPowered) {
                    disconnect();
                    return;
                }

                if (!level.isLoaded(targetPos) || !(level.getBlockEntity(targetPos) instanceof ItemMagnetHoseConnectorBlockEntity)) {
                    disconnect();
                    return;
                }

                BlockEntity targetBe = level.getBlockEntity(targetPos);
                if (targetBe instanceof ItemMagnetHoseConnectorBlockEntity targetLink) {
                     if (targetLink.getBlockState().getValue(ItemMagnetHoseConnectorBlock.POWERED)) {
                        disconnect();
                        return;
                    }
                }

                VSLinkUtil.WorldTransform myTransform = getWorldTransform(false);
                VSLinkUtil.WorldTransform targetTransform = ((ItemMagnetHoseConnectorBlockEntity)targetBe).getWorldTransform(false);

                double maxDist = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
                if (myTransform.position.distanceToSqr(targetTransform.position) > maxDist * maxDist) {
                    disconnect();
                    return;
                }
                
                // 角度チェック
                double dot = myTransform.direction.dot(targetTransform.direction);
                if (dot > -0.500) { // 角度が開きすぎた場合
                    disconnect();
                    return;
                }
            }
        }
        
        if (targetPos == null && !isPowered) {
            if (scanCooldown-- <= 0) {
                scanCooldown = 20;
                scanAndConnect();
            }
        }

        if (targetPos != null && getBlockState().getValue(ItemMagnetHoseConnectorBlock.ACTIVITY) == LinkActivity.SEND) {
            if (level.getBlockEntity(targetPos) instanceof ItemMagnetHoseConnectorBlockEntity targetLink) {
                ItemStack myStack = inventory.getStackInSlot(0);
                if (!myStack.isEmpty()) {
                    ItemStack remaining = targetLink.inventory.insertItem(0, myStack, false);
                    inventory.setStackInSlot(0, remaining);
                }
            }
        }
    }
    
    private void disconnect() {
        BlockPos oldTarget = targetPos;
        setTargetPos(null);
        
        if (oldTarget != null && level.isLoaded(oldTarget)) {
            if (level.getBlockEntity(oldTarget) instanceof ItemMagnetHoseConnectorBlockEntity targetLink) {
                targetLink.setTargetPos(null);
            }
            level.playSound(null, oldTarget, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
        }
        
        level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
    }
    
    private void scanAndConnect() {
        Direction facing = getBlockState().getValue(ItemMagnetHoseConnectorBlock.FACING);
        AABB scanBox = getScanBoxInWorld(level, worldPosition, facing);

        synchronized (ALL_CONNECTORS) {
            for (BlockPos scanPos : new ArrayList<>(ALL_CONNECTORS)) {
                if (scanPos.equals(worldPosition)) continue;
                if (!level.isLoaded(scanPos)) continue;

                Vec3 targetWorldPos = getWorldPos(level, scanPos, false);
                if (!scanBox.contains(targetWorldPos)) continue;

                if (isConnectorAt(scanPos)) {
                    setTargetPos(scanPos);
                    return;
                }
            }
        }
    }

    private boolean isConnectorAt(BlockPos scanPos) {
        if (!level.isLoaded(scanPos)) return false;

        BlockEntity be = level.getBlockEntity(scanPos);
        if (!(be instanceof ItemMagnetHoseConnectorBlockEntity candidateBe)) return false;

        if (candidateBe.getTargetPos() != null) return false;

        BlockState candidateState = candidateBe.getBlockState();
        if (candidateState.getValue(ItemMagnetHoseConnectorBlock.POWERED)) return false;

        Long shipId1 = VSLinkUtil.getShipId(level, worldPosition);
        Long shipId2 = VSLinkUtil.getShipId(level, scanPos);

        boolean isWorldToWorld = shipId1 == null && shipId2 == null;
        boolean isIntraShip = shipId1 != null && shipId1.equals(shipId2);

        if (isWorldToWorld && VsFluidLinkConfig.SERVER.restrictWorldToWorldConnection.get()) {
            return false;
        }

        if (isIntraShip && VsFluidLinkConfig.SERVER.restrictIntraShipConnection.get()) {
            return false;
        }

        if (areFacingsOppositeInWorld(candidateBe)) {
            level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
            level.playSound(null, scanPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
            candidateBe.setTargetPos(worldPosition);
            return true;
        }
        return false;
    }

    private AABB getScanBoxInWorld(Level level, BlockPos pos, Direction facing) {
        int scanDist = VsFluidLinkConfig.SERVER.magnetScanDistance.get();
        int scanRadius = VsFluidLinkConfig.SERVER.magnetScanRadius.get();

        Vec3 start = getWorldPos(level, pos.relative(facing, 1), false);
        Vec3 end = getWorldPos(level, pos.relative(facing, scanDist), false);
        AABB box = new AABB(start, end).minmax(new AABB(end, start));

        switch (facing.getAxis()) {
            case X -> box = box.inflate(0, scanRadius, scanRadius);
            case Y -> box = box.inflate(scanRadius, 0, scanRadius);
            case Z -> box = box.inflate(scanRadius, scanRadius, 0);
        }
        return box;
    }

    private boolean areFacingsOppositeInWorld(ItemMagnetHoseConnectorBlockEntity candidateBe) {
        VSLinkUtil.WorldTransform myTransform = this.getWorldTransform(false);
        VSLinkUtil.WorldTransform candidateTransform = candidateBe.getWorldTransform(false);

        double dot = myTransform.direction.dot(candidateTransform.direction);
        return dot < -0.890; // approx 170 degrees
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
