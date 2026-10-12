package com.mk2525.vsfluidlink.content.HoseConnector;

import com.mk2525.vsfluidlink.util.ConnectorOrientation;
import com.mk2525.vsfluidlink.util.ConnectorInteraction;
import com.mk2525.vsfluidlink.util.LinkSelection;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class HoseConnectorBlock extends BaseEntityBlock implements IWrenchable {
    
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");
    private static final String LINK_SELECTION_CHANNEL = "hose_connector";
    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.FACING;
    public static final BooleanProperty UPSIDE_DOWN = BooleanProperty.create("upside_down");

    public HoseConnectorBlock(Properties properties) {
        super(properties.noOcclusion().strength(2.0f).requiresCorrectToolForDrops());
        registerDefaultState(defaultBlockState()
                .setValue(LINKED, false)
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(UPSIDE_DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINKED, HORIZONTAL_FACING, UPSIDE_DOWN, ConnectorOrientation.ROLL);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(LINKED, false)
                .setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite())
                .setValue(UPSIDE_DOWN, context.getClickedFace() == Direction.DOWN);
    }

    public static Direction getPipeSide(BlockState state) {
        return ConnectorOrientation.modelUp(state).getOpposite();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof HoseConnectorBlockEntity linkBe) {
                BlockPos targetPos = linkBe.getTargetPos();
                if (targetPos != null) {
                    VSLinkUtil.clearReciprocalLink(level, pos, targetPos, HoseConnectorBlockEntity.class, HoseConnectorBlockEntity::getTargetPos, HoseConnectorBlockEntity::setTargetPos);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        
        if (ConnectorInteraction.isWrench(heldItem)) {
            if (player.isShiftKeyDown()) return onSneakWrenched(state, new UseOnContext(player, hand, hit));
            if (VsFluidLinkConfig.wrenchRightClickAction() == VsFluidLinkConfig.WrenchRightClickAction.ROTATE) {
                return onWrenched(state, new UseOnContext(player, hand, hit));
            }
        }

        if (ConnectorInteraction.canLink(heldItem, player.isShiftKeyDown())) {
            if (level.isClientSide) return InteractionResult.SUCCESS;

            CompoundTag tag = new CompoundTag();
            BlockPos selected = LinkSelection.getValid(player, LINK_SELECTION_CHANNEL, HoseConnectorBlockEntity.class);
            if (selected != null) tag.put("LinkPos", net.minecraft.nbt.NbtUtils.writeBlockPos(selected));
            

            BlockEntity clickedBe = level.getBlockEntity(pos);
            if (clickedBe instanceof HoseConnectorBlockEntity linkBe && linkBe.getTargetPos() != null) {
                BlockPos targetPos = linkBe.getTargetPos();
                
                linkBe.setTargetPos(null);
                
                if (targetPos != null) {
                    VSLinkUtil.clearReciprocalLink(level, pos, targetPos, HoseConnectorBlockEntity.class, HoseConnectorBlockEntity::getTargetPos, HoseConnectorBlockEntity::setTargetPos);
                    level.playSound(null, targetPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
                }
                
                player.displayClientMessage(Component.translatable("vsfluidlink.message.link_disconnected"), true);
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
                
                if (tag.contains("LinkPos")) {
                    BlockPos selectedPos = net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("LinkPos"));
                    if (selectedPos.equals(pos)) {
                        tag.remove("LinkPos");
                        LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                    }
                }
                
                return InteractionResult.SUCCESS;
            }

            if (!tag.contains("LinkPos")) {
                tag.put("LinkPos", net.minecraft.nbt.NbtUtils.writeBlockPos(pos));
                LinkSelection.set(player, LINK_SELECTION_CHANNEL, pos);
                player.displayClientMessage(Component.translatable("vsfluidlink.message.first_pos_selected", pos.toShortString()), true);
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            } else {
                BlockPos firstPos = net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("LinkPos"));
                
                if (firstPos.equals(pos)) {
                     player.displayClientMessage(Component.translatable("vsfluidlink.message.cannot_link_to_self"), true);
                     return InteractionResult.SUCCESS;
                }

                Vec3 pos1 = VSLinkUtil.getWorldPos(level, firstPos);
                Vec3 pos2 = VSLinkUtil.getWorldPos(level, pos);

                if (pos1.distanceToSqr(pos2) > 100.0) {
                    player.displayClientMessage(Component.translatable("vsfluidlink.message.distance_too_far"), true);
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    tag.remove("LinkPos");
                    LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                    return InteractionResult.SUCCESS;
                }

                Long shipId1 = VSLinkUtil.getShipId(level, firstPos);
                Long shipId2 = VSLinkUtil.getShipId(level, pos);

                boolean isWorldToWorld = shipId1 == null && shipId2 == null;
                boolean isIntraShip = shipId1 != null && shipId1.equals(shipId2);

                if (isWorldToWorld && VsFluidLinkConfig.SERVER.restrictWorldToWorldConnection.get()) {
                    player.displayClientMessage(Component.translatable("vsfluidlink.message.world_to_world_restricted"), true);
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    tag.remove("LinkPos");
                    LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                    return InteractionResult.SUCCESS;
                }

                if (isIntraShip && VsFluidLinkConfig.SERVER.restrictIntraShipConnection.get()) {
                    player.displayClientMessage(Component.translatable("vsfluidlink.message.intra_ship_restricted"), true);
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    tag.remove("LinkPos");
                    LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                    return InteractionResult.SUCCESS;
                }

                BlockEntity be1 = level.getBlockEntity(firstPos);
                BlockEntity be2 = level.getBlockEntity(pos);

                if (be1 instanceof HoseConnectorBlockEntity link1 && be2 instanceof HoseConnectorBlockEntity link2) {
                    link1.setTargetPos(pos);
                    link2.setTargetPos(firstPos);
                    
                    player.displayClientMessage(Component.translatable("vsfluidlink.message.linked_successfully"), true);
                    level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
                    level.playSound(null, firstPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
                    tag.remove("LinkPos");
                    LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                }
            }
            return InteractionResult.SUCCESS;
        }

        return super.use(state, level, pos, player, hand, hit);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HoseConnectorBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, ModBlockEntities.HOSE_CONNECTOR.get(), HoseConnectorBlockEntity::tick);
    }
    
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return ConnectorOrientation.onWrenched(state, context);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return ConnectorOrientation.onSneakWrenched(state, context);
    }
}
