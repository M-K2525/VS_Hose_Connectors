package com.mk2525.vsfluidlink.content.MagnetChainConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class MagnetChainConnectorBlock extends DirectionalKineticBlock implements IBE<MagnetChainConnectorBlockEntity>, IWrenchable {

    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    public MagnetChainConnectorBlock(Properties properties) {
        super(properties.strength(2.0f).requiresCorrectToolForDrops());
        registerDefaultState(defaultBlockState().setValue(LINKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LINKED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = context.getNearestLookingDirection().getOpposite();
        return super.getStateForPlacement(context).setValue(FACING, preferred).setValue(LINKED, false);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis().isVertical()) {
            return Axis.X;
        }
        if (facing.getAxis() == Axis.Z) {
            return Axis.X;
        }
        return Axis.Z;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MagnetChainConnectorBlockEntity linkBe) {
                // 接続解除処理はBlockEntityのremove()で行うため、ここでは何もしない
                // BlockEntityのremove()はsuper.onRemove()内で呼ばれる
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MagnetChainConnectorBlockEntity magnet) {
                if (level.hasNeighborSignal(pos)) {
                    magnet.disconnect();
                }
            }
        }
    }

    @Override
    public Class<MagnetChainConnectorBlockEntity> getBlockEntityClass() {
        return MagnetChainConnectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MagnetChainConnectorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.MAGNET_CHAIN_CONNECTOR.get();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }
}
