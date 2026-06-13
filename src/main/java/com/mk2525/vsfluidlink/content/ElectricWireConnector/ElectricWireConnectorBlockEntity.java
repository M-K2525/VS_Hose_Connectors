package com.mk2525.vsfluidlink.content.ElectricWireConnector;

import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

public class ElectricWireConnectorBlockEntity extends BlockEntity implements IHaveHoveringInformation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEIGHBOR_TRANSFER_RATE = 10000;

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
    private Long targetSpaceId;
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

    public void setTarget(BlockPos targetPos, Long targetSpaceId) {
        if (this.level == null) return;

        boolean wasLinked = this.targetPos != null;
        boolean isLinked = targetPos != null;

        this.targetPos = targetPos;
        this.targetSpaceId = targetPos == null ? null : targetSpaceId;
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("Energy")) {
            energyStorage.setEnergyStored(tag.getInt("Energy"));
        }
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Energy", energyStorage.getEnergyStored());
        if (targetPos != null) {
            tag.put("TargetPos", new com.mk2525.vsfluidlink.util.LinkTarget(targetPos, targetSpaceId).toTag());
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
        if (level.isClientSide) return;

        transferWithAdjacent(level, pos, blockEntity);

        if (blockEntity.targetPos == null) return;
        if (blockEntity.lastTransferTick == level.getGameTime()) return;

        try {
            if (!level.isLoaded(blockEntity.targetPos)) return;

            BlockEntity targetBe = VSLinkUtil.resolveBlockEntity(level, blockEntity.targetPos, blockEntity.targetSpaceId, ElectricWireConnectorBlockEntity.class);
            if (!(targetBe instanceof ElectricWireConnectorBlockEntity targetLink)) {
                blockEntity.setTarget(null, null);
                blockEntity.setChanged();
                return;
            }

            // --- Distance Check ---
            Vec3 myPos = VSLinkUtil.getWorldPos(level, pos);
            Vec3 targetPosVec = VSLinkUtil.getWorldPos(level, blockEntity.targetPos);

            double maxDist = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
            if (myPos.distanceToSqr(targetPosVec) > maxDist * maxDist) {
                BlockPos oldTarget = blockEntity.targetPos;
                blockEntity.setTarget(null, null);
                targetLink.setTarget(null, null);

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

    private static void transferWithAdjacent(Level level, BlockPos pos, ElectricWireConnectorBlockEntity blockEntity) {
        for (var direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            if (!level.isLoaded(neighbourPos)) {
                continue;
            }

            IEnergyStorage neighbourStorage = findEnergyStorage(level, neighbourPos, direction.getOpposite());

            if (neighbourStorage == null) {
                continue;
            }

            pullFromAdjacent(blockEntity.energyStorage, neighbourStorage);
            pushToAdjacent(blockEntity.energyStorage, neighbourStorage);
        }
    }

    private static void pullFromAdjacent(IEnergyStorage localStorage, IEnergyStorage neighbourStorage) {
        int simulatedExtract = neighbourStorage.extractEnergy(NEIGHBOR_TRANSFER_RATE, true);
        if (simulatedExtract <= 0) {
            return;
        }

        int accepted = localStorage.receiveEnergy(simulatedExtract, true);
        if (accepted <= 0) {
            return;
        }

        int extracted = neighbourStorage.extractEnergy(accepted, false);
        if (extracted > 0) {
            localStorage.receiveEnergy(extracted, false);
        }
    }

    private static void pushToAdjacent(IEnergyStorage localStorage, IEnergyStorage neighbourStorage) {
        int simulatedExtract = localStorage.extractEnergy(NEIGHBOR_TRANSFER_RATE, true);
        if (simulatedExtract <= 0) {
            return;
        }

        int accepted = neighbourStorage.receiveEnergy(simulatedExtract, true);
        if (accepted <= 0) {
            return;
        }

        int extracted = localStorage.extractEnergy(accepted, false);
        if (extracted <= 0) {
            return;
        }

        int received = neighbourStorage.receiveEnergy(extracted, false);
        if (received < extracted) {
            localStorage.receiveEnergy(extracted - received, false);
        }
    }

    @Nullable
    private static IEnergyStorage findEnergyStorage(Level level, BlockPos pos, net.minecraft.core.Direction preferredSide) {
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, preferredSide);
        if (storage != null) {
            return storage;
        }

        storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (storage != null) {
            return storage;
        }

        for (var side : net.minecraft.core.Direction.values()) {
            if (side == preferredSide) {
                continue;
            }
            storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
            if (storage != null) {
                return storage;
            }
        }

        return null;
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Energy Buffer").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal(energyStorage.getEnergyStored() + " / " + energyStorage.getMaxEnergyStored() + " FE")
            .withStyle(ChatFormatting.GOLD));
        return true;
    }
}
