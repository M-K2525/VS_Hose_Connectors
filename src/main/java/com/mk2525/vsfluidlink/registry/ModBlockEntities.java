package com.mk2525.vsfluidlink.registry;

import com.mk2525.vsfluidlink.VsFluidLinkMod;
import com.mk2525.vsfluidlink.content.HoseConnector.HoseConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.MagnetHoseConnector.MagnetHoseConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ItemHoseConnecotor.ItemHoseConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ItemMagnetHoseConnector.ItemMagnetHoseConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlockEntity;
import com.mk2525.vsfluidlink.content.MagnetChainConnector.MagnetChainConnectorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, VsFluidLinkMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<HoseConnectorBlockEntity>> HOSE_CONNECTOR = BLOCK_ENTITIES.register("hose_connector",
            () -> BlockEntityType.Builder.of(HoseConnectorBlockEntity::new, ModBlocks.HOSE_CONNECTOR.get()).build(null));
            
    public static final RegistryObject<BlockEntityType<MagnetHoseConnectorBlockEntity>> MAGNET_HOSE_CONNECTOR = BLOCK_ENTITIES.register("magnet_hose_connector",
            () -> BlockEntityType.Builder.of(MagnetHoseConnectorBlockEntity::new, ModBlocks.MAGNET_HOSE_CONNECTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<ItemHoseConnectorBlockEntity>> ITEM_HOSE_CONNECTOR = BLOCK_ENTITIES.register("item_hose_connector",
            () -> BlockEntityType.Builder.of(ItemHoseConnectorBlockEntity::new, ModBlocks.ITEM_HOSE_CONNECTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<ItemMagnetHoseConnectorBlockEntity>> ITEM_MAGNET_HOSE_CONNECTOR = BLOCK_ENTITIES.register("item_magnet_hose_connector",
            () -> BlockEntityType.Builder .of(ItemMagnetHoseConnectorBlockEntity::new, ModBlocks.ITEM_MAGNET_HOSE_CONNECTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<ElectricWireConnectorBlockEntity>> ELECTRIC_WIRE_CONNECTOR = BLOCK_ENTITIES.register("electric_wire_connector",
            () -> BlockEntityType.Builder.of(ElectricWireConnectorBlockEntity::new, ModBlocks.ELECTRIC_WIRE_CONNECTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<ElectricMagnetWireConnectorBlockEntity>> ELECTRIC_MAGNET_WIRE_CONNECTOR = BLOCK_ENTITIES.register("electric_magnet_wire_connector",
            () -> BlockEntityType.Builder.of(ElectricMagnetWireConnectorBlockEntity::new, ModBlocks.ELECTRIC_MAGNET_WIRE_CONNECTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<ChainConnectorBlockEntity>> BELT_CONNECTOR = BLOCK_ENTITIES.register("chain_connector",
            () -> BlockEntityType.Builder.of(ChainConnectorBlockEntity::new, ModBlocks.BELT_CONNECTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<MagnetChainConnectorBlockEntity>> MAGNET_BELT_CONNECTOR = BLOCK_ENTITIES.register("magnet_chain_connector",
            () -> BlockEntityType.Builder.of(MagnetChainConnectorBlockEntity::new, ModBlocks.MAGNET_BELT_CONNECTOR.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
