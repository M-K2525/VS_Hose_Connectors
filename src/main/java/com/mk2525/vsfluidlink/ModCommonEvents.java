package com.mk2525.vsfluidlink;

import com.mk2525.vsfluidlink.util.LinkSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VsFluidLinkMod.MOD_ID)
public class ModCommonEvents {
    private static final String[] LINK_SELECTION_CHANNELS = {
            "hose_connector",
            "electric_wire_connector",
            "chain_connector",
            "item_hose_connector"
    };

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (tryClearSelection(event.getEntity(), event.getLevel(), event.getPos(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        BlockPos soundPos = BlockPos.containing(event.getEntity().position());
        if (tryClearSelection(event.getEntity(), event.getLevel(), soundPos, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static boolean tryClearSelection(Player player, Level level, BlockPos soundPos, InteractionHand hand) {
        if (level.isClientSide || hand != InteractionHand.MAIN_HAND) {
            return false;
        }

        ItemStack heldItem = player.getItemInHand(hand);
        if (!heldItem.getItem().getDescriptionId().contains("wrench") || !player.isShiftKeyDown()) {
            return false;
        }

        boolean cleared = false;
        for (String channel : LINK_SELECTION_CHANNELS) {
            if (LinkSelection.get(player, channel) != null) {
                LinkSelection.clear(player, channel);
                cleared = true;
            }
        }

        if (!cleared) {
            return false;
        }

        player.displayClientMessage(Component.translatable("vsfluidlink.message.selection_cleared"), true);
        level.playSound(null, soundPos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
        return true;
    }
}
