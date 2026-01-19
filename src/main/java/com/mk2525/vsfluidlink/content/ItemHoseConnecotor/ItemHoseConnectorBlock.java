package com.mk2525.vsfluidlink.content.ItemHoseConnecotor;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.content.LinkActivity;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ItemHoseConnectorBlock extends DirectionalKineticBlock implements IBE<ItemHoseConnectorBlockEntity>, IWrenchable {

    public static final BooleanProperty LINKED = BooleanProperty.create("linked");
    public static final EnumProperty<LinkActivity> ACTIVITY = EnumProperty.create("activity", LinkActivity.class);

    public ItemHoseConnectorBlock(Properties properties) {
        super(properties.strength(2.0f).requiresCorrectToolForDrops());
        registerDefaultState(defaultBlockState().setValue(LINKED, false).setValue(ACTIVITY, LinkActivity.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LINKED, ACTIVITY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = context.getNearestLookingDirection().getOpposite();
        return super.getStateForPlacement(context).setValue(FACING, preferred).setValue(LINKED, false).setValue(ACTIVITY, LinkActivity.NONE);
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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ItemHoseConnectorBlockEntity linkBe) {
                BlockPos targetPos = linkBe.getTargetPos();
                if (targetPos != null && level.isLoaded(targetPos)) {
                    BlockEntity targetBe = level.getBlockEntity(targetPos);
                    if (targetBe instanceof ItemHoseConnectorBlockEntity targetLinkBe) {
                        targetLinkBe.setTargetPos(null);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (heldItem.getItem().getDescriptionId().contains("wrench")) {
            if (level.isClientSide) return InteractionResult.SUCCESS;

            CompoundTag tag = heldItem.getOrCreateTag();

            if (player.isShiftKeyDown()) {
                if (tag.contains("LinkPos")) {
                    tag.remove("LinkPos");
                    player.displayClientMessage(Component.translatable("vsfluidlink.message.selection_cleared"), true);
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                }
                return InteractionResult.SUCCESS;
            }

            BlockEntity clickedBe = level.getBlockEntity(pos);
            if (clickedBe instanceof ItemHoseConnectorBlockEntity linkBe && linkBe.getTargetPos() != null) {
                BlockPos targetPos = linkBe.getTargetPos();

                linkBe.setTargetPos(null);

                if (level.isLoaded(targetPos)) {
                    BlockEntity targetBe = level.getBlockEntity(targetPos);
                    if (targetBe instanceof ItemHoseConnectorBlockEntity targetLinkBe) {
                        targetLinkBe.setTargetPos(null);
                    }
                    level.playSound(null, targetPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
                }

                player.displayClientMessage(Component.translatable("vsfluidlink.message.link_disconnected"), true);
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);

                if (tag.contains("LinkPos")) {
                    BlockPos selectedPos = net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("LinkPos"));
                    if (selectedPos.equals(pos)) {
                        tag.remove("LinkPos");
                    }
                }

                return InteractionResult.SUCCESS;
            }

            if (!tag.contains("LinkPos")) {
                tag.put("LinkPos", net.minecraft.nbt.NbtUtils.writeBlockPos(pos));
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
                    return InteractionResult.SUCCESS;
                }

                if (isIntraShip && VsFluidLinkConfig.SERVER.restrictIntraShipConnection.get()) {
                    player.displayClientMessage(Component.translatable("vsfluidlink.message.intra_ship_restricted"), true);
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    tag.remove("LinkPos");
                    return InteractionResult.SUCCESS;
                }

                BlockEntity be1 = level.getBlockEntity(firstPos);
                BlockEntity be2 = level.getBlockEntity(pos);

                if (be1 instanceof ItemHoseConnectorBlockEntity link1 && be2 instanceof ItemHoseConnectorBlockEntity link2) {
                    link1.setTargetPos(pos);
                    link2.setTargetPos(firstPos);

                    player.displayClientMessage(Component.translatable("vsfluidlink.message.linked_successfully"), true);
                    level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
                    level.playSound(null, firstPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
                    tag.remove("LinkPos");
                }
            }
            return InteractionResult.SUCCESS;
        }

        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public Class<ItemHoseConnectorBlockEntity> getBlockEntityClass() {
        return ItemHoseConnectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ItemHoseConnectorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ITEM_HOSE_CONNECTOR.get();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public SpeedLevel getMinimumRequiredSpeedLevel() {
        return SpeedLevel.MEDIUM;
    }
}
