package com.mk2525.vsfluidlink;

import com.mk2525.vsfluidlink.content.HoseConnector.HoseConnectorRenderer;
import com.mk2525.vsfluidlink.content.MagnetHoseConnector.MagnetHoseConnectorRenderer;
import com.mk2525.vsfluidlink.content.ItemHoseConnecotor.ItemHoseConnectorRenderer;
import com.mk2525.vsfluidlink.content.ItemMagnetHoseConnector.ItemMagnetHoseConnectorRenderer;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorRenderer;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorRenderer;
import com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorRenderer;
import com.mk2525.vsfluidlink.content.MagnetChainConnector.MagnetChainConnectorRenderer;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = VsFluidLinkMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.HOSE_CONNECTOR.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.MAGNET_HOSE_CONNECTOR.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.ITEM_HOSE_CONNECTOR.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.ITEM_MAGNET_HOSE_CONNECTOR.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.ELECTRIC_WIRE_CONNECTOR.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.ELECTRIC_MAGNET_WIRE_CONNECTOR.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.CHAIN_CONNECTOR.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.MAGNET_CHAIN_CONNECTOR.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.HOSE_DECORATION.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.MAGNET_HOSE_DECORATION.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.SMALL_HOSE_DECORATION.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.SMALL_MAGNET_HOSE_DECORATION.get(), RenderType.cutout());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        LOGGER.info("Registering BlockEntityRenderers for VsFluidLink");
        event.registerBlockEntityRenderer(ModBlockEntities.HOSE_CONNECTOR.get(), HoseConnectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MAGNET_HOSE_CONNECTOR.get(), MagnetHoseConnectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ITEM_HOSE_CONNECTOR.get(), ItemHoseConnectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ITEM_MAGNET_HOSE_CONNECTOR.get(), ItemMagnetHoseConnectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ELECTRIC_WIRE_CONNECTOR.get(), ElectricWireConnectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ELECTRIC_MAGNET_WIRE_CONNECTOR.get(), ElectricMagnetWireConnectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CHAIN_CONNECTOR.get(), ChainConnectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MAGNET_CHAIN_CONNECTOR.get(), MagnetChainConnectorRenderer::new);
    }
}
