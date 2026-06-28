package com.mk2525.vsfluidlink;

import com.mk2525.vsfluidlink.content.MagnetHoseConnector.MagnetHoseConnectorBlock;
import com.mk2525.vsfluidlink.content.HoseConnector.HoseConnectorBlock;
import com.mk2525.vsfluidlink.registry.ModBlockEntities;
import com.mk2525.vsfluidlink.registry.ModBlocks;
import com.mk2525.vsfluidlink.registry.ModCreativeModeTabs;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.function.ToDoubleFunction;

@Mod(VsFluidLinkMod.MOD_ID)
public class VsFluidLinkMod {
    public static final String MOD_ID = "vsfluidlink";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VsFluidLinkMod(IEventBus modEventBus, ModContainer modContainer) {
        ModCreativeModeTabs.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        
        modContainer.registerConfig(ModConfig.Type.SERVER, VsFluidLinkConfig.SERVER_SPEC);
        
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);

    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.HOSE_CONNECTOR.get(),
                (be, context) -> context == null || context == HoseConnectorBlock.getPipeSide(be.getBlockState()) ? be.getTank() : null
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.MAGNET_HOSE_CONNECTOR.get(),
                (be, context) -> {
                    if (context == null) {
                        return be.getTank();
                    }
                    Direction pipeSide = be.getBlockState().getValue(MagnetHoseConnectorBlock.FACING).getOpposite();
                    return context == pipeSide ? be.getTank() : null;
                }
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ITEM_HOSE_CONNECTOR.get(),
                (be, context) -> be.getInventory()
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ITEM_MAGNET_HOSE_CONNECTOR.get(),
                (be, context) -> be.getInventory()
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ELECTRIC_WIRE_CONNECTOR.get(),
                (be, context) -> be.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ELECTRIC_MAGNET_WIRE_CONNECTOR.get(),
                (be, context) -> be.getEnergyStorage()
        );
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerStressValues();
        });
    }

    private void registerStressValues() {
        try {
            Class<?> valuesClass = null;
            try {
                valuesClass = Class.forName("com.simibubi.create.content.kinetics.BlockStressValues");
            } catch (ClassNotFoundException e) {
                LOGGER.warn("Create BlockStressValues class not found");
                return;
            }

            Method registerProvider = valuesClass.getMethod("registerProvider", String.class, ToDoubleFunction.class);
            
            registerProvider.invoke(null, MOD_ID, (ToDoubleFunction<Block>) block -> {
                if (block == ModBlocks.ITEM_HOSE_CONNECTOR.get()) return 2.0;
                if (block == ModBlocks.ITEM_MAGNET_HOSE_CONNECTOR.get()) return 2.0;
                if (block == ModBlocks.CHAIN_CONNECTOR.get()) return 0.0;
                if (block == ModBlocks.MAGNET_CHAIN_CONNECTOR.get()) return 0.0;
                return 0.0; 
            });
            
            LOGGER.info("Registered stress values provider for VsFluidLink blocks");

        } catch (Exception e) {
            LOGGER.error("Failed to register stress values provider", e);
            
            // フォールバック: setDefaultImpactを試す
            try {
                Class<?> defaultsClass = Class.forName("com.simibubi.create.infrastructure.config.BlockStressDefaults");
                Method setDefaultImpact = defaultsClass.getMethod("setDefaultImpact", ResourceLocation.class, double.class);
                
                setDefaultImpact.invoke(null, ResourceLocation.fromNamespaceAndPath(MOD_ID, "item_hose_connector"), 2.0);
                setDefaultImpact.invoke(null, ResourceLocation.fromNamespaceAndPath(MOD_ID, "item_magnet_hose_connector"), 2.0);
                setDefaultImpact.invoke(null, ResourceLocation.fromNamespaceAndPath(MOD_ID, "chain_connector"), 0.0);
                setDefaultImpact.invoke(null, ResourceLocation.fromNamespaceAndPath(MOD_ID, "magnet_chain_connector"), 0.0);
                
                LOGGER.info("Registered stress values via setDefaultImpact");
            } catch (Exception e2) {
                LOGGER.error("Failed to register stress values via setDefaultImpact", e2);
            }
        }
    }
}
