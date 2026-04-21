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
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VsFluidLinkMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoseConnectorBlockEntity>> HOSE_CONNECTOR = BLOCK_ENTITIES.register("hose_connector",
            () -> BlockEntityType.Builder.of(HoseConnectorBlockEntity::new, ModBlocks.HOSE_CONNECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagnetHoseConnectorBlockEntity>> MAGNET_HOSE_CONNECTOR = BLOCK_ENTITIES.register("magnet_hose_connector",
            () -> BlockEntityType.Builder.of(MagnetHoseConnectorBlockEntity::new, ModBlocks.MAGNET_HOSE_CONNECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemHoseConnectorBlockEntity>> ITEM_HOSE_CONNECTOR = BLOCK_ENTITIES.register("item_hose_connector",
            () -> BlockEntityType.Builder.of(ItemHoseConnectorBlockEntity::new, ModBlocks.ITEM_HOSE_CONNECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemMagnetHoseConnectorBlockEntity>> ITEM_MAGNET_HOSE_CONNECTOR = BLOCK_ENTITIES.register("item_magnet_hose_connector",
            () -> BlockEntityType.Builder.of(ItemMagnetHoseConnectorBlockEntity::new, ModBlocks.ITEM_MAGNET_HOSE_CONNECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricWireConnectorBlockEntity>> ELECTRIC_WIRE_CONNECTOR = BLOCK_ENTITIES.register("electric_wire_connector",
            () -> BlockEntityType.Builder.of(ElectricWireConnectorBlockEntity::new, ModBlocks.ELECTRIC_WIRE_CONNECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricMagnetWireConnectorBlockEntity>> ELECTRIC_MAGNET_WIRE_CONNECTOR = BLOCK_ENTITIES.register("electric_magnet_wire_connector",
            () -> BlockEntityType.Builder.of(ElectricMagnetWireConnectorBlockEntity::new, ModBlocks.ELECTRIC_MAGNET_WIRE_CONNECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChainConnectorBlockEntity>> CHAIN_CONNECTOR = BLOCK_ENTITIES.register("chain_connector",
            () -> BlockEntityType.Builder.of(ChainConnectorBlockEntity::new, ModBlocks.CHAIN_CONNECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagnetChainConnectorBlockEntity>> MAGNET_CHAIN_CONNECTOR = BLOCK_ENTITIES.register("magnet_chain_connector",
            () -> BlockEntityType.Builder.of(MagnetChainConnectorBlockEntity::new, ModBlocks.MAGNET_CHAIN_CONNECTOR.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
