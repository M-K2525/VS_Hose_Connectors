package com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ElectricMagnetWireConnectorBlockEntity extends ElectricWireConnectorBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<BlockPos> ALL_CONNECTORS = Collections.synchronizedSet(new HashSet<>());
    private int checkCooldown = 0;

    public ElectricMagnetWireConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_MAGNET_WIRE_CONNECTOR.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            ALL_CONNECTORS.add(getBlockPos());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            ALL_CONNECTORS.remove(getBlockPos());
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ElectricMagnetWireConnectorBlockEntity blockEntity) {
        if (level.isClientSide) return;

        // --- Connection Check (every 20 ticks) ---
        if (blockEntity.checkCooldown-- <= 0) {
            blockEntity.checkCooldown = 20;

            boolean isPowered = state.getValue(ElectricMagnetWireConnectorBlock.POWERED);
            BlockPos currentTarget = blockEntity.getTargetPos();

            if (isPowered) {
                if (currentTarget != null) {
                    disconnect(level, pos, blockEntity);
                }
                return; // Powered, so no transfer and no new connection
            }

            if (currentTarget != null) {
                BlockEntity targetBe = level.getBlockEntity(currentTarget);
                double maxDist = VsFluidLinkConfig.SERVER.maxLinkDistance.get();
                if (!(targetBe instanceof ElectricMagnetWireConnectorBlockEntity) || !level.isLoaded(currentTarget) || VSLinkUtil.getWorldPos(level, pos).distanceToSqr(VSLinkUtil.getWorldPos(level, currentTarget)) > maxDist * maxDist) {
                    disconnect(level, pos, blockEntity);
                    return;
                }

                // 角度チェック
                Direction myFacing = state.getValue(ElectricMagnetWireConnectorBlock.FACING);
                BlockState targetState = targetBe.getBlockState();
                if (!(targetState.getBlock() instanceof ElectricMagnetWireConnectorBlock)) {
                    disconnect(level, pos, blockEntity);
                    return;
                }
                Direction targetFacing = targetState.getValue(ElectricMagnetWireConnectorBlock.FACING);

                VSLinkUtil.WorldTransform myTransform = VSLinkUtil.getWorldTransform(level, pos, myFacing);
                VSLinkUtil.WorldTransform targetTransform = VSLinkUtil.getWorldTransform(level, currentTarget, targetFacing);

                double dot = myTransform.direction.dot(targetTransform.direction);

                if (dot > -0.500) { // 角度が開きすぎた場合
                    disconnect(level, pos, blockEntity);
                    return;
                }
            } else {
                // --- Scan for new connection ---
                Direction facing = state.getValue(ElectricMagnetWireConnectorBlock.FACING);
                AABB scanBox = getScanBoxInWorld(level, pos, facing);

                synchronized (ALL_CONNECTORS) {
                    for (BlockPos scanPos : new ArrayList<>(ALL_CONNECTORS)) {
                        if (scanPos.equals(pos)) continue;
                        if (!level.isLoaded(scanPos)) continue;

                        Vec3 targetWorldPos = VSLinkUtil.getWorldPos(level, scanPos);
                        if (!scanBox.contains(targetWorldPos)) continue;

                        if (isConnectorAt(level, pos, facing, scanPos)) {
                            blockEntity.setTargetPos(scanPos);
                            break; // Found a target, stop scanning
                        }
                    }
                }
            }
        }

        // --- Energy Transfer (every tick) ---
        if (blockEntity.getTargetPos() != null) {
            ElectricWireConnectorBlockEntity.tick(level, pos, state, blockEntity);
        }
    }

    private static boolean isConnectorAt(Level level, BlockPos selfPos, Direction selfFacing, BlockPos scanPos) {
        if (!level.isLoaded(scanPos)) return false;

        BlockEntity be = level.getBlockEntity(scanPos);
        if (!(be instanceof ElectricMagnetWireConnectorBlockEntity targetConnector)) return false;

        if (targetConnector.getTargetPos() != null) return false;

        BlockState scanState = be.getBlockState();
        if (scanState.getValue(ElectricMagnetWireConnectorBlock.POWERED)) return false;

        Long shipId1 = VSLinkUtil.getShipId(level, selfPos);
        Long shipId2 = VSLinkUtil.getShipId(level, scanPos);

        boolean isWorldToWorld = shipId1 == null && shipId2 == null;
        boolean isIntraShip = shipId1 != null && shipId1.equals(shipId2);

        if (isWorldToWorld && VsFluidLinkConfig.SERVER.restrictWorldToWorldConnection.get()) {
            return false;
        }

        if (isIntraShip && VsFluidLinkConfig.SERVER.restrictIntraShipConnection.get()) {
            return false;
        }

        if (areFacingsOppositeInWorld(level, selfPos, selfFacing, scanPos, scanState.getValue(ElectricMagnetWireConnectorBlock.FACING))) {
            level.playSound(null, selfPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
            level.playSound(null, scanPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);

            targetConnector.setTargetPos(selfPos);
            return true;
        }
        return false;
    }

    private static AABB getScanBoxInWorld(Level level, BlockPos pos, Direction facing) {
        int scanDist = VsFluidLinkConfig.SERVER.magnetScanDistance.get();
        int scanRadius = VsFluidLinkConfig.SERVER.magnetScanRadius.get();

        Vec3 start = VSLinkUtil.getWorldPos(level, pos.relative(facing, 1));
        Vec3 end = VSLinkUtil.getWorldPos(level, pos.relative(facing, scanDist));
        AABB box = new AABB(start, end).minmax(new AABB(end, start));

        switch (facing.getAxis()) {
            case X -> box = box.inflate(0, scanRadius, scanRadius);
            case Y -> box = box.inflate(scanRadius, 0, scanRadius);
            case Z -> box = box.inflate(scanRadius, scanRadius, 0);
        }
        return box;
    }

    private static boolean areFacingsOppositeInWorld(Level level, BlockPos pos1, Direction facing1, BlockPos pos2, Direction facing2) {
        VSLinkUtil.WorldTransform transform1 = VSLinkUtil.getWorldTransform(level, pos1, facing1);
        VSLinkUtil.WorldTransform transform2 = VSLinkUtil.getWorldTransform(level, pos2, facing2);

        return transform1.direction.dot(transform2.direction) < -0.890; // approx 170 degrees
    }

    private static void disconnect(Level level, BlockPos pos, ElectricMagnetWireConnectorBlockEntity blockEntity) {
        BlockPos oldTarget = blockEntity.getTargetPos();
        blockEntity.setTargetPos(null);

        if (oldTarget != null && level.isLoaded(oldTarget)) {
            BlockEntity targetBe = level.getBlockEntity(oldTarget);
            if (targetBe instanceof ElectricMagnetWireConnectorBlockEntity targetLinkBe) {
                targetLinkBe.setTargetPos(null);
            }
            level.playSound(null, oldTarget, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
    }
}
