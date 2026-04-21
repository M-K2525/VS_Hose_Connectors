package com.mk2525.vsfluidlink.content.HoseConnector;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import com.mk2525.vsfluidlink.util.LinkSelection;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class HoseConnectorBlock extends BaseEntityBlock implements IWrenchable {

    public static final MapCodec<HoseConnectorBlock> CODEC = simpleCodec(HoseConnectorBlock::new);
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");
    private static final String LINK_SELECTION_CHANNEL = "hose_connector";

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public HoseConnectorBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion().strength(2.0f).requiresCorrectToolForDrops());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINKED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(LINKED, false);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof HoseConnectorBlockEntity linkBe) {
                BlockPos targetPos = linkBe.getTargetPos();
                if (targetPos != null && level.isLoaded(targetPos)) {
                    BlockEntity targetBe = level.getBlockEntity(targetPos);
                    if (targetBe instanceof HoseConnectorBlockEntity targetLinkBe) {
                        targetLinkBe.setTargetPos(null);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // In 1.21, BaseEntityBlock.use() is no longer overridden; remove @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (heldItem.getItem().getDescriptionId().contains("wrench")) {
            if (level.isClientSide) return InteractionResult.SUCCESS;

            CompoundTag tag = new CompoundTag();
            BlockPos selectedLinkPos = LinkSelection.get(player, LINK_SELECTION_CHANNEL);
            if (selectedLinkPos != null) {
                CompoundTag selectedTag = new CompoundTag();
                selectedTag.putInt("x", selectedLinkPos.getX());
                selectedTag.putInt("y", selectedLinkPos.getY());
                selectedTag.putInt("z", selectedLinkPos.getZ());
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
            if (clickedBe instanceof HoseConnectorBlockEntity linkBe && linkBe.getTargetPos() != null) {
                BlockPos targetPos = linkBe.getTargetPos();

                linkBe.setTargetPos(null);

                if (level.isLoaded(targetPos)) {
                    BlockEntity targetBe = level.getBlockEntity(targetPos);
                    if (targetBe instanceof HoseConnectorBlockEntity targetLinkBe) {
                        targetLinkBe.setTargetPos(null);
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
                LinkSelection.set(player, LINK_SELECTION_CHANNEL, pos);
                player.displayClientMessage(Component.translatable("vsfluidlink.message.first_pos_selected", pos.toShortString()), true);
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
            } else {
               CompoundTag firstTag = tag.getCompound("LinkPos");
               BlockPos firstPos = new BlockPos(firstTag.getInt("x"), firstTag.getInt("y"), firstTag.getInt("z"));

                if (firstPos.equals(pos)) {
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

                Long shipId1 = VSLinkUtil.getShipId(level, firstPos);
                Long shipId2 = VSLinkUtil.getShipId(level, pos);

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

                BlockEntity be1 = level.getBlockEntity(firstPos);
                BlockEntity be2 = level.getBlockEntity(pos);

                if (be1 instanceof HoseConnectorBlockEntity link1 && be2 instanceof HoseConnectorBlockEntity link2) {
                    link1.setTargetPos(pos);
                    link2.setTargetPos(firstPos);

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
        return (lvl, pos, st, be) -> {
            if (be instanceof HoseConnectorBlockEntity hoseBe) {
                HoseConnectorBlockEntity.tick(lvl, pos, st, hoseBe);
            }
        };
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
