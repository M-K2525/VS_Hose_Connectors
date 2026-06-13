package com.mk2525.vsfluidlink.content.ChainConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.util.LinkSelection;
import com.mk2525.vsfluidlink.util.LinkTarget;
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
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ChainConnectorBlock extends DirectionalKineticBlock implements IBE<ChainConnectorBlockEntity>, IWrenchable {

    public static final BooleanProperty LINKED = BooleanProperty.create("linked");
    private static final String LINK_SELECTION_CHANNEL = "chain_connector";

    public ChainConnectorBlock(Properties properties) {
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
        // North/South (Z霆ｸ) 縺ｮ縺ｨ縺阪・ East/West (X霆ｸ) 縺ｫ謗･邯・
        if (facing.getAxis() == Axis.Z) {
            return Axis.X;
        }
        // East/West (X霆ｸ) 縺ｮ縺ｨ縺阪・ North/South (Z霆ｸ) 縺ｫ謗･邯・
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
            if (blockEntity instanceof ChainConnectorBlockEntity linkBe) {
                BlockPos targetPos = linkBe.getTargetPos();
                if (targetPos != null && level.isLoaded(targetPos)) {
                    BlockEntity targetBe = VSLinkUtil.resolveBlockEntity(level, targetPos, linkBe.getTargetSpaceId(), ChainConnectorBlockEntity.class);
                    if (targetBe instanceof ChainConnectorBlockEntity targetLinkBe) {
                        targetLinkBe.setTarget(null, null);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // In 1.21, BaseEntityBlock.use() became internal; override the method without @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (heldItem.getItem().getDescriptionId().contains("wrench")) {
            if (level.isClientSide) return InteractionResult.SUCCESS;

            CompoundTag tag = new CompoundTag();
            LinkTarget selectedTarget = LinkSelection.getTarget(player, LINK_SELECTION_CHANNEL);
            if (selectedTarget != null) {
                CompoundTag selectedTag = new CompoundTag();
                selectedTag.putInt("x", selectedTarget.pos().getX());
                selectedTag.putInt("y", selectedTarget.pos().getY());
                selectedTag.putInt("z", selectedTarget.pos().getZ());
                tag.put("LinkPos", selectedTag);
            }

            if (player.isShiftKeyDown()) {
                if (tag.contains("LinkPos")) {
                    tag.remove("LinkPos");
                    LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                    player.displayClientMessage(Component.translatable("vsfluidlink.message.selection_cleared"), true);
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                }
                return InteractionResult.SUCCESS;
            }

            BlockEntity clickedBe = level.getBlockEntity(pos);
            if (clickedBe instanceof ChainConnectorBlockEntity linkBe && linkBe.getTargetPos() != null) {
                BlockPos targetPos = linkBe.getTargetPos();

                linkBe.setTargetPos(null);

                if (level.isLoaded(targetPos)) {
                    BlockEntity targetBe = VSLinkUtil.resolveBlockEntity(level, targetPos, linkBe.getTargetSpaceId(), ChainConnectorBlockEntity.class);
                    if (targetBe instanceof ChainConnectorBlockEntity targetLinkBe) {
                        targetLinkBe.setTarget(null, null);
                    }
                    level.playSound(null, targetPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
                }

                player.displayClientMessage(Component.translatable("vsfluidlink.message.link_disconnected"), true);
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);

                if (tag.contains("LinkPos")) {
                    CompoundTag linkPosTag = tag.getCompound("LinkPos");
                    BlockPos selectedPos = new BlockPos(linkPosTag.getInt("x"), linkPosTag.getInt("y"), linkPosTag.getInt("z"));
                    if (selectedPos.equals(pos)) {
                        tag.remove("LinkPos");
                    LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                    }
                }

                return InteractionResult.SUCCESS;
            }

            if (!tag.contains("LinkPos")) {
                CompoundTag linkPosTag = new CompoundTag();
                linkPosTag.putInt("x", pos.getX());
                linkPosTag.putInt("y", pos.getY());
                linkPosTag.putInt("z", pos.getZ());
                tag.put("LinkPos", linkPosTag);
                LinkSelection.set(player, LINK_SELECTION_CHANNEL, clickedBe);
                player.displayClientMessage(Component.translatable("vsfluidlink.message.first_pos_selected", pos.toShortString()), true);
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
            } else {
                CompoundTag firstTag = tag.getCompound("LinkPos");
                LinkTarget firstTarget = LinkSelection.getTarget(player, LINK_SELECTION_CHANNEL);
                BlockPos firstPos = firstTarget != null ? firstTarget.pos() : new BlockPos(firstTag.getInt("x"), firstTag.getInt("y"), firstTag.getInt("z"));
                Long firstSpaceId = firstTarget != null ? firstTarget.spaceId() : VSLinkUtil.getSpatialId(level, firstPos);
                Long secondSpaceId = VSLinkUtil.getSpatialId(level, pos);

                if (firstPos.equals(pos) && java.util.Objects.equals(firstSpaceId, secondSpaceId)) {
                     player.displayClientMessage(Component.translatable("vsfluidlink.message.cannot_link_to_self"), true);
                     return InteractionResult.SUCCESS;
                }

                Vec3 pos1 = VSLinkUtil.getWorldPos(level, firstPos);
                Vec3 pos2 = VSLinkUtil.getWorldPos(level, pos);

                if (pos1.distanceToSqr(pos2) > 100.0) {
                    player.displayClientMessage(Component.translatable("vsfluidlink.message.distance_too_far"), true);
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    tag.remove("LinkPos");
                    LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                    return InteractionResult.SUCCESS;
                }

                Long shipId1 = firstSpaceId;
                Long shipId2 = secondSpaceId;

                boolean isWorldToWorld = shipId1 == null && shipId2 == null;
                boolean isIntraShip = shipId1 != null && shipId1.equals(shipId2);

                if (isWorldToWorld && VsFluidLinkConfig.SERVER.restrictWorldToWorldConnection.get()) {
                    player.displayClientMessage(Component.translatable("vsfluidlink.message.world_to_world_restricted"), true);
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    tag.remove("LinkPos");
                    LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                    return InteractionResult.SUCCESS;
                }

                if (isIntraShip && VsFluidLinkConfig.SERVER.restrictIntraShipConnection.get()) {
                    player.displayClientMessage(Component.translatable("vsfluidlink.message.intra_ship_restricted"), true);
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    tag.remove("LinkPos");
                    LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                    return InteractionResult.SUCCESS;
                }

                BlockEntity be1 = LinkSelection.resolve(player, LINK_SELECTION_CHANNEL, ChainConnectorBlockEntity.class);
                BlockEntity be2 = level.getBlockEntity(pos);

                if (be1 instanceof ChainConnectorBlockEntity link1 && be2 instanceof ChainConnectorBlockEntity link2) {
                    link1.setTarget(pos, secondSpaceId);
                    link2.setTarget(firstPos, firstSpaceId);

                    player.displayClientMessage(Component.translatable("vsfluidlink.message.second_pos_selected", pos.toShortString()), true);
                    level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
                    level.playSound(null, firstPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
                    tag.remove("LinkPos");
                    LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
                }
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return use(state, level, pos, player, hand, hit).consumesAction()
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public Class<ChainConnectorBlockEntity> getBlockEntityClass() {
        return ChainConnectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ChainConnectorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CHAIN_CONNECTOR.get();
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
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (player != null && !level.isClientSide && LinkSelection.get(player, LINK_SELECTION_CHANNEL) != null) {
            LinkSelection.clear(player, LINK_SELECTION_CHANNEL);
            player.displayClientMessage(Component.translatable("vsfluidlink.message.selection_cleared"), true);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        if (player != null && !level.isClientSide) {
            if (!player.isCreative()) {
                ItemStack itemStack = new ItemStack(this);
                if (!player.getInventory().add(itemStack)) {
                    player.drop(itemStack, false);
                }
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.levelEvent(2001, pos, Block.getId(state));
        }

        return InteractionResult.SUCCESS;
    }
}
