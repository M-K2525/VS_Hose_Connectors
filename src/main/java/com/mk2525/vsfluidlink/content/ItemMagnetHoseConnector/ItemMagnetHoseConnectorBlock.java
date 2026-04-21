package com.mk2525.vsfluidlink.content.ItemMagnetHoseConnector;

import com.mk2525.vsfluidlink.content.ItemHoseConnecotor.ItemHoseConnectorBlock;
import com.mk2525.vsfluidlink.content.LinkActivity;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class ItemMagnetHoseConnectorBlock extends DirectionalKineticBlock implements IBE<ItemMagnetHoseConnectorBlockEntity> {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LINKED = ItemHoseConnectorBlock.LINKED;
    public static final EnumProperty<LinkActivity> ACTIVITY = EnumProperty.create("activity", LinkActivity.class);

    public ItemMagnetHoseConnectorBlock(Properties properties) {
        super(properties.strength(2.0f).requiresCorrectToolForDrops());
        registerDefaultState(defaultBlockState()
                .setValue(POWERED, false)
                .setValue(LINKED, false)
                .setValue(ACTIVITY, LinkActivity.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, LINKED, ACTIVITY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = context.getNearestLookingDirection().getOpposite();
        return super.getStateForPlacement(context).setValue(FACING, preferred)
                .setValue(POWERED, false)
                .setValue(LINKED, false)
                .setValue(ACTIVITY, LinkActivity.NONE);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis().isVertical()) {
            return Axis.X;
        }
        // North/South (Z軸) のときは East/West (X軸) に接続
        if (facing.getAxis() == Axis.Z) {
            return Axis.X;
        }
        // East/West (X軸) のときは North/South (Z軸) に接続
        return Axis.Z;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) return;

        boolean isPowered = level.hasNeighborSignal(pos);
        if (isPowered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, isPowered), 2);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ItemMagnetHoseConnectorBlockEntity linkBe) {
                BlockPos targetPos = linkBe.getTargetPos();
                if (targetPos != null && level.isLoaded(targetPos)) {
                    BlockEntity targetBe = level.getBlockEntity(targetPos);
                    if (targetBe instanceof ItemMagnetHoseConnectorBlockEntity targetLinkBe) {
                        targetLinkBe.setTargetPos(null);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public Class<ItemMagnetHoseConnectorBlockEntity> getBlockEntityClass() {
        return ItemMagnetHoseConnectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ItemMagnetHoseConnectorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ITEM_MAGNET_HOSE_CONNECTOR.get();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
    
    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.MEDIUM;
    }
}
