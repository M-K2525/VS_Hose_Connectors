package com.mk2525.vsfluidlink.registry;

import com.mk2525.vsfluidlink.VsFluidLinkMod;
import com.mk2525.vsfluidlink.content.HoseConnector.HoseConnectorBlock;
import com.mk2525.vsfluidlink.content.MagnetHoseConnector.MagnetHoseConnectorBlock;
import com.mk2525.vsfluidlink.content.ItemHoseConnecotor.ItemHoseConnectorBlock;
import com.mk2525.vsfluidlink.content.ItemMagnetHoseConnector.ItemMagnetHoseConnectorBlock;
import com.mk2525.vsfluidlink.content.ElectricWireConnector.ElectricWireConnectorBlock;
import com.mk2525.vsfluidlink.content.ElectricMagnetWireConnector.ElectricMagnetWireConnectorBlock;
import com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlock;
import com.mk2525.vsfluidlink.content.MagnetChainConnector.MagnetChainConnectorBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;

import java.util.List;
import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, VsFluidLinkMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, VsFluidLinkMod.MOD_ID);

    public static final DeferredHolder<Block, HoseConnectorBlock> HOSE_CONNECTOR = registerBlock("hose_connector",
            () -> new HoseConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.hose_connector.tooltip");
            
    public static final DeferredHolder<Block, MagnetHoseConnectorBlock> MAGNET_HOSE_CONNECTOR = registerBlock("magnet_hose_connector",
            () -> new MagnetHoseConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.magnet_hose_connector.tooltip");

    public static final DeferredHolder<Block, ItemHoseConnectorBlock> ITEM_HOSE_CONNECTOR = registerBlock("item_hose_connector",
            () -> new ItemHoseConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.item_hose_connector.tooltip");

    public static final DeferredHolder<Block, ItemMagnetHoseConnectorBlock> ITEM_MAGNET_HOSE_CONNECTOR = registerBlock("item_magnet_hose_connector",
            () -> new ItemMagnetHoseConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.item_magnet_hose_connector.tooltip");

    public static final DeferredHolder<Block, ElectricWireConnectorBlock> ELECTRIC_WIRE_CONNECTOR = registerBlock("electric_wire_connector",
            () -> new ElectricWireConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.electric_wire_connector.tooltip");

    public static final DeferredHolder<Block, ElectricMagnetWireConnectorBlock> ELECTRIC_MAGNET_WIRE_CONNECTOR = registerBlock("electric_magnet_wire_connector",
            () -> new ElectricMagnetWireConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.electric_magnet_wire_connector.tooltip");

    public static final DeferredHolder<Block, ChainConnectorBlock> CHAIN_CONNECTOR = registerBlock("chain_connector",
            () -> new ChainConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.chain_connector.tooltip");

    public static final DeferredHolder<Block, MagnetChainConnectorBlock> MAGNET_CHAIN_CONNECTOR = registerBlock("magnet_chain_connector",
            () -> new MagnetChainConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.magnet_chain_connector.tooltip");

    // 補完用の装飾ブロック
    public static final DeferredHolder<Block, Block> HOSE_DECORATION = BLOCKS.register("hose_decoration",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().air()));
    public static final DeferredHolder<Block, Block> MAGNET_HOSE_DECORATION = BLOCKS.register("magnet_hose_decoration",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().air()));
    public static final DeferredHolder<Block, Block> SMALL_HOSE_DECORATION = BLOCKS.register("small_hose_decoration",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().air()));
    public static final DeferredHolder<Block, Block> SMALL_MAGNET_HOSE_DECORATION = BLOCKS.register("small_magnet_hose_decoration",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().air()));

    private static <T extends Block> DeferredHolder<Block, T> registerBlock(String name, Supplier<T> block, String tooltipKey) {
        DeferredHolder<Block, T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn, tooltipKey);
        return toReturn;
    }

    private static <T extends Block> DeferredHolder<Item, BlockItem> registerBlockItem(String name, DeferredHolder<Block, T> block, String tooltipKey) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable(tooltipKey));
                super.appendHoverText(stack, context, tooltip, flag);
            }
        });
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
