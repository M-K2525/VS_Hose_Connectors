package com.mk2525.vsfluidlink.content.ElectricWireConnector;

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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ElectricWireConnectorBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static class MutableEnergyStorage extends EnergyStorage {
        public MutableEnergyStorage(int capacity, int maxReceive, int maxExtract) {
            super(capacity, maxReceive, maxExtract);
        }

        public void setEnergyStored(int energy) {
            this.energy = Math.max(0, Math.min(capacity, energy));
        }
    }

    protected final MutableEnergyStorage energyStorage = new MutableEnergyStorage(10000, 10000, 10000) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) {
                setChanged();
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (extracted > 0 && !simulate) {
                setChanged();
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
            return extracted;
        }
        };

    private BlockPos targetPos;
    private long lastTransferTick = -1;

    public ElectricWireConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ElectricWireConnectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ELECTRIC_WIRE_CONNECTOR.get(), pos, state);
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
            if (currentState.hasProperty(ElectricWireConnectorBlock.LINKED)) {
                level.setBlock(getBlockPos(), currentState.setValue(ElectricWireConnectorBlock.LINKED, isLinked), 3);
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
        if (tag.contains("Energy")) {
            energyStorage.setEnergyStored(tag.getInt("Energy"));
        }
        if (tag.contains("TargetPos")) {
            CompoundTag targetTag = tag.getCompound("TargetPos");
            targetPos = new BlockPos(targetTag.getInt("x"), targetTag.getInt("y"), targetTag.getInt("z"));
        } else {
            targetPos = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Energy", energyStorage.getEnergyStored());
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

    public static void tick(Level level, BlockPos pos, BlockState state, ElectricWireConnectorBlockEntity blockEntity) {
        if (level.isClientSide || blockEntity.targetPos == null) return;

        // Check if already processed this tick
        if (blockEntity.lastTransferTick == level.getGameTime()) {
            return;
        }

        try {
            if (!level.isLoaded(blockEntity.targetPos)) return;

            BlockEntity targetBe = level.getBlockEntity(blockEntity.targetPos);
            if (!(targetBe instanceof ElectricWireConnectorBlockEntity targetLink)) {
                blockEntity.setTargetPos(null);
                blockEntity.setChanged();
                return;
            }

            // --- Distance Check ---
            Vec3 myPos = VSLinkUtil.getWorldPos(level, pos);
            Vec3 targetPosVec = VSLinkUtil.getWorldPos(level, blockEntity.targetPos);

            double maxDist = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
            if (myPos.distanceToSqr(targetPosVec) > maxDist * maxDist) {
                BlockPos oldTarget = blockEntity.targetPos;
                blockEntity.setTargetPos(null);
                targetLink.setTargetPos(null);

                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f);
                blockEntity.setChanged();
                return;
            }

            // --- Energy Transfer Logic ---
            EnergyStorage myStorage = blockEntity.energyStorage;
            EnergyStorage otherStorage = targetLink.energyStorage;

            // Only the connector with more energy should handle the transfer
            if (myStorage.getEnergyStored() < otherStorage.getEnergyStored()) {
                return;
            }
            // If amounts are equal, the one with the "smaller" position handles it
            if (myStorage.getEnergyStored() == otherStorage.getEnergyStored() && pos.compareTo(blockEntity.targetPos) > 0) {
                return;
            }

            // Mark both as processed for this tick
            blockEntity.lastTransferTick = level.getGameTime();
            targetLink.lastTransferTick = level.getGameTime();

            if (myStorage.getEnergyStored() == 0 && otherStorage.getEnergyStored() == 0) return;

            int totalEnergy = myStorage.getEnergyStored() + otherStorage.getEnergyStored();
            int myAmount = totalEnergy / 2;
            int otherAmount = totalEnergy - myAmount; // Remainder goes to the other storage

            // Transfer energy to balance
            if (myStorage.getEnergyStored() > myAmount) {
                int toExtract = myStorage.getEnergyStored() - myAmount;
                // Limit by transfer rate (10000 FE/t)
                toExtract = Math.min(toExtract, 10000);

                int extracted = myStorage.extractEnergy(toExtract, false);
                otherStorage.receiveEnergy(extracted, false);
            } else if (otherStorage.getEnergyStored() > otherAmount) {
                // This case should not happen if logic is correct (myStorage >= otherStorage),
                // but for safety or if transfer rate limited the previous tick
                int toExtract = otherStorage.getEnergyStored() - otherAmount;
                toExtract = Math.min(toExtract, 10000);

                int extracted = otherStorage.extractEnergy(toExtract, false);
                myStorage.receiveEnergy(extracted, false);
            }
        } catch (Exception e) {
            // エラー無視
        }
    }

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }
}
