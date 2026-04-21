package com.mk2525.vsfluidlink.registry;

import com.mk2525.vsfluidlink.VsFluidLinkMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VsFluidLinkMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, ? extends CreativeModeTab> VS_FLUID_LINK_TAB = CREATIVE_MODE_TABS.register("vs_fluid_link_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.HOSE_CONNECTOR.get()))
                    .title(Component.translatable("creativetab.vsfluidlink_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModBlocks.HOSE_CONNECTOR.get());
                        pOutput.accept(ModBlocks.MAGNET_HOSE_CONNECTOR.get());
                        pOutput.accept(ModBlocks.ITEM_HOSE_CONNECTOR.get());
                        pOutput.accept(ModBlocks.ITEM_MAGNET_HOSE_CONNECTOR.get());
                        pOutput.accept(ModBlocks.ELECTRIC_WIRE_CONNECTOR.get());
                        pOutput.accept(ModBlocks.ELECTRIC_MAGNET_WIRE_CONNECTOR.get());
                        pOutput.accept(ModBlocks.CHAIN_CONNECTOR.get());
                        pOutput.accept(ModBlocks.MAGNET_CHAIN_CONNECTOR.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
