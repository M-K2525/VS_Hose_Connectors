package com.mk2525.vsfluidlink.content.HoseConnector;

import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

public class HoseConnectorBlockEntity extends BlockEntity implements IHaveHoveringInformation {
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

    private BlockPos targetPos;
    private long lastTransferTick = -1;

    public HoseConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public HoseConnectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.HOSE_CONNECTOR.get(), pos, state);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(
            getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(),
            getBlockPos().getX() + 1, getBlockPos().getY() + 1, getBlockPos().getZ() + 1
        );
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        tank.readFromNBT(provider, tag.getCompound("Tank"));
        if (tag.contains("TargetPos")) {
            CompoundTag targetTag = tag.getCompound("TargetPos");
            targetPos = new BlockPos(targetTag.getInt("x"), targetTag.getInt("y"), targetTag.getInt("z"));
        } else {
            targetPos = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("Tank", tank.writeToNBT(provider, new CompoundTag()));
        if (targetPos != null) {
            CompoundTag targetTag = new CompoundTag();
            targetTag.putInt("x", targetPos.getX());
            targetTag.putInt("y", targetPos.getY());
            targetTag.putInt("z", targetPos.getZ());
            tag.put("TargetPos", targetTag);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
                    setTankContents(blockEntity, myTank, fluid, myAmount);
                }
                if (otherTank.getFluidAmount() != otherAmount) {
                    setTankContents(targetLink, otherTank, fluid, otherAmount);
                }
            }
        } catch (Exception e) {
            // エラー無視
        }
    }

   public FluidTank getTank() {
        return tank;
    }

    private static void setTankContents(HoseConnectorBlockEntity owner, FluidTank tank, FluidStack fluid, int amount) {
        FluidStack updated = amount <= 0 ? FluidStack.EMPTY : fluid.copy();
        if (!updated.isEmpty()) {
            updated.setAmount(amount);
        }

        tank.setFluid(updated);
        owner.setChanged();
        if (owner.level != null && !owner.level.isClientSide) {
            owner.level.sendBlockUpdated(owner.getBlockPos(), owner.getBlockState(), owner.getBlockState(), 3);
        }
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Fluid Buffer").withStyle(ChatFormatting.WHITE));

        if (!tank.isEmpty()) {
            tooltip.add(tank.getFluid().getHoverName().copy().withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.literal(tank.getFluidAmount() + " / " + tank.getCapacity() + " mB")
            .withStyle(ChatFormatting.GOLD));
        return true;
    }
}
