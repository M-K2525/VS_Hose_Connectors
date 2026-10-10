package com.mk2525.vsfluidlink.content.ElectricWireConnector;

import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mk2525.vsfluidlink.compat.computercraft.ComputerCraftCompat;
import com.mk2525.vsfluidlink.compat.computercraft.WiredConnection;
import com.mk2525.vsfluidlink.compat.powergrid.PowerGridCompat;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

public class ElectricWireConnectorBlockEntity extends SmartBlockEntity implements IHaveHoveringInformation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEIGHBOR_TRANSFER_RATE = 10000;
    
    private static class MutableEnergyStorage extends EnergyStorage {
        private MutableEnergyStorage() {
            super(VsFluidLinkConfig.energyCapacity(), 10000, 10000);
        }

        public void applyCapacity() {
            capacity = VsFluidLinkConfig.energyCapacity();
            energy = Math.max(0, Math.min(capacity, energy));
        }
    }

    protected final MutableEnergyStorage energyStorage = new MutableEnergyStorage() {
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

    protected final LazyOptional<IEnergyStorage> energyHandler = LazyOptional.of(() -> energyStorage);
    
    private BlockPos targetPos;
    private long lastTransferTick = -1;
    private long lastBehaviourTick = Long.MIN_VALUE;
    private @Nullable WiredConnection wiredConnection;

    public ElectricWireConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ElectricWireConnectorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ELECTRIC_WIRE_CONNECTOR.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void onLoad() {
        super.onLoad();
        energyStorage.applyCapacity();
        VSLinkUtil.registerLinkBlockEntity(this);
        if (level != null && !level.isClientSide) {
            tickWiredConnection();
            if (wiredConnection != null) level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
        }
    }

    @Override
    public void invalidate() {
        removeWiredConnection();
        VSLinkUtil.unregisterLinkBlockEntity(this);
        super.invalidate();
    }

    @Override
    public void remove() {
        PowerGridCompat.remove(this);
        super.remove();
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
            if (currentState.hasProperty(ElectricWireConnectorBlock.LINKED)) {
                level.setBlock(getBlockPos(), currentState.setValue(ElectricWireConnectorBlock.LINKED, isLinked), 3);
            }
        }
        
        if (!level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            if (level.getServer() != null) {
                if (level.getServer().isSameThread()) PowerGridCompat.linkChanged(this);
                else level.getServer().execute(() -> PowerGridCompat.linkChanged(this));
            }
            WiredConnection connection = wiredConnection;
            if (connection != null && level.getServer() != null) {
                if (level.getServer().isSameThread()) connection.linkChanged();
                else level.getServer().execute(connection::linkChanged);
            }
        }
    }
    
    public BlockPos getTargetPos() {
        return targetPos;
    }

    public @Nullable WiredConnection getWiredConnection() {
        if (level == null || level.isClientSide || isRemoved() || level.getServer() == null
                || !level.getServer().isSameThread()) return null;
        if (wiredConnection == null) wiredConnection = ComputerCraftCompat.create(this);
        return wiredConnection;
    }

    public void tickWiredConnection() {
        if (level != null && !isRemoved() && (level.isClientSide || level.getServer() != null
                && level.getServer().isSameThread()) && lastBehaviourTick != level.getGameTime()) {
            lastBehaviourTick = level.getGameTime();
            super.tick();
        }
        WiredConnection connection = getWiredConnection();
        if (connection != null) connection.tick();
    }

    private void removeWiredConnection() {
        WiredConnection connection = wiredConnection;
        wiredConnection = null;
        if (connection == null || level == null || level.isClientSide || level.getServer() == null) return;
        if (level.getServer().isSameThread()) connection.remove();
        else level.getServer().execute(connection::remove);
    }

    @Override
    public void onChunkUnloaded() {
        invalidate();
        super.onChunkUnloaded();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ElectricWireConnectorBlockEntity blockEntity) {
        blockEntity.tickWiredConnection();
        if (level.isClientSide) return;

        transferWithAdjacent(level, pos, blockEntity);

        if (blockEntity.targetPos == null) return;

        // Check if already processed this tick
        if (blockEntity.lastTransferTick == level.getGameTime()) {
            return;
        }

        try {
            ElectricWireConnectorBlockEntity targetLink = VSLinkUtil.findReciprocalLink(level, pos, blockEntity.targetPos, ElectricWireConnectorBlockEntity.class, ElectricWireConnectorBlockEntity::getTargetPos);
            if (targetLink == null) {
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

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyHandler.cast();
        }
        WiredConnection connection = getWiredConnection();
        if (connection != null) {
            LazyOptional<T> optional = connection.getCapability(cap);
            if (optional.isPresent()) return optional;
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        removeWiredConnection();
        super.invalidateCaps();
        energyHandler.invalidate();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("Energy", energyStorage.serializeNBT());
        if (targetPos != null) {
            tag.put("TargetPos", NbtUtils.writeBlockPos(targetPos));
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        if (tag.contains("Energy")) {
            energyStorage.deserializeNBT(tag.get("Energy"));
        }
        energyStorage.applyCapacity();
        
        if (tag.contains("TargetPos")) {
            targetPos = NbtUtils.readBlockPos(tag.getCompound("TargetPos"));
        } else {
            targetPos = null;
        }
    }
    
    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Energy Buffer").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal(energyStorage.getEnergyStored() + " / " + energyStorage.getMaxEnergyStored() + " FE")
            .withStyle(ChatFormatting.GOLD));
        return true;
    }

    private static void transferWithAdjacent(Level level, BlockPos pos, ElectricWireConnectorBlockEntity blockEntity) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            if (!level.isLoaded(neighbourPos)) {
                continue;
            }

            IEnergyStorage neighbourStorage = findEnergyStorage(level, neighbourPos, direction.getOpposite());
            if (neighbourStorage == null || neighbourStorage == blockEntity.energyStorage) {
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
    private static IEnergyStorage findEnergyStorage(Level level, BlockPos pos, @Nullable Direction preferredSide) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }

        IEnergyStorage storage = getEnergyStorage(be, preferredSide);
        if (storage != null) {
            return storage;
        }

        storage = getEnergyStorage(be, null);
        if (storage != null) {
            return storage;
        }

        for (Direction side : Direction.values()) {
            if (side == preferredSide) {
                continue;
            }
            storage = getEnergyStorage(be, side);
            if (storage != null) {
                return storage;
            }
        }

        return null;
    }

    @Nullable
    private static IEnergyStorage getEnergyStorage(BlockEntity be, @Nullable Direction side) {
        LazyOptional<IEnergyStorage> optional = be.getCapability(ForgeCapabilities.ENERGY, side);
        return optional.isPresent() ? optional.orElse(null) : null;
    }
}
