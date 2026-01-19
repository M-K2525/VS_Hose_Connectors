package com.mk2525.vsfluidlink.content.HoseConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class HoseConnectorBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final FluidTank tank = new FluidTank(1000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    protected final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> tank);
    
    private BlockPos targetPos;
    private long lastTransferTick = -1;

    public HoseConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public HoseConnectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.HOSE_CONNECTOR.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    public void setTargetPos(BlockPos targetPos) {
        if (this.level == null) return;
        
        boolean wasLinked = this.targetPos != null;
        boolean isLinked = targetPos != null;
        
        this.targetPos = targetPos;
        setChanged();
        
        if (wasLinked != isLinked) {
            BlockState currentState = getBlockState();
            if (currentState.hasProperty(HoseConnectorBlock.LINKED)) {
                level.setBlock(getBlockPos(), currentState.setValue(HoseConnectorBlock.LINKED, isLinked), 3);
            }
        }
        
        if (!level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }
    
    public BlockPos getTargetPos() {
        return targetPos;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        handleUpdateTag(pkt.getTag());
    }

    public static Vec3 getWorldPos(Level level, BlockPos pos, boolean isClient) {
        if (level == null) return Vec3.atCenterOf(pos);
        if (isClient) {
            return VSLinkUtil.Client.getRenderWorldPos(level, pos);
        } else {
            return VSLinkUtil.getWorldPos(level, pos);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HoseConnectorBlockEntity blockEntity) {
        if (level.isClientSide || blockEntity.targetPos == null) return;

        // Check if already processed this tick
        if (blockEntity.lastTransferTick == level.getGameTime()) {
            return;
        }

        try {
            if (!level.isLoaded(blockEntity.targetPos)) return;

            BlockEntity targetBe = level.getBlockEntity(blockEntity.targetPos);
            if (!(targetBe instanceof HoseConnectorBlockEntity targetLink)) {
                blockEntity.setTargetPos(null);
                blockEntity.setChanged();
                return;
            }

            // --- Distance Check ---
            Vec3 myPos = getWorldPos(level, pos, false);
            Vec3 targetPosVec = getWorldPos(level, blockEntity.targetPos, false);
            
            double maxDist = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
            if (myPos.distanceToSqr(targetPosVec) > maxDist * maxDist) {
                BlockPos oldTarget = blockEntity.targetPos;
                blockEntity.setTargetPos(null);
                targetLink.setTargetPos(null);
                
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
                blockEntity.setChanged();
                return;
            }

            // --- Fluid Transfer Logic ---
            FluidTank myTank = blockEntity.tank;
            FluidTank otherTank = targetLink.tank;

            // Only the connector with more fluid should handle the transfer
            if (myTank.getFluidAmount() < otherTank.getFluidAmount()) {
                return;
            }
            // If amounts are equal, the one with the "smaller" position handles it to prevent duplicate processing
            if (myTank.getFluidAmount() == otherTank.getFluidAmount() && pos.compareTo(blockEntity.targetPos) > 0) {
                return;
            }

            // Mark both as processed for this tick
            blockEntity.lastTransferTick = level.getGameTime();
            targetLink.lastTransferTick = level.getGameTime();

            if (myTank.isEmpty() && otherTank.isEmpty()) return;

            boolean canTransfer = myTank.isEmpty() || otherTank.isEmpty() || myTank.getFluid().isFluidEqual(otherTank.getFluid());
            
            if (canTransfer) {
                FluidStack fluid = myTank.isEmpty() ? otherTank.getFluid() : myTank.getFluid();
                int totalAmount = myTank.getFluidAmount() + otherTank.getFluidAmount();
                int myAmount = totalAmount / 2;
                int otherAmount = totalAmount - myAmount; // Remainder goes to the other tank

                if (myTank.getFluidAmount() != myAmount) {
                    myTank.setFluid(fluid.copy());
                    myTank.getFluid().setAmount(myAmount);
                }
                if (otherTank.getFluidAmount() != otherAmount) {
                    otherTank.setFluid(fluid.copy());
                    otherTank.getFluid().setAmount(otherAmount);
                }
            }
        } catch (Exception e) {
            // エラー無視
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER && side == Direction.DOWN) {
            return fluidHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidHandler.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Tank", tank.writeToNBT(new CompoundTag()));
        if (targetPos != null) {
            tag.put("TargetPos", NbtUtils.writeBlockPos(targetPos));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag.getCompound("Tank"));
        if (tag.contains("TargetPos")) {
            targetPos = NbtUtils.readBlockPos(tag.getCompound("TargetPos"));
        } else {
            targetPos = null;
        }
    }
    
    public FluidTank getTank() {
        return tank;
    }
}
