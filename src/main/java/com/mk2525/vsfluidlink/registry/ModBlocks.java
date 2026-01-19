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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, VsFluidLinkMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, VsFluidLinkMod.MOD_ID);

    public static final RegistryObject<Block> HOSE_CONNECTOR = registerBlock("hose_connector",
            () -> new HoseConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.hose_connector.tooltip");
            
    public static final RegistryObject<Block> MAGNET_HOSE_CONNECTOR = registerBlock("magnet_hose_connector",
            () -> new MagnetHoseConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.magnet_hose_connector.tooltip");

    public static final RegistryObject<Block> ITEM_HOSE_CONNECTOR = registerBlock("item_hose_connector",
            () -> new ItemHoseConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.item_hose_connector.tooltip");

    public static final RegistryObject<Block> ITEM_MAGNET_HOSE_CONNECTOR = registerBlock("item_magnet_hose_connector",
            () -> new ItemMagnetHoseConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.item_magnet_hose_connector.tooltip");

    public static final RegistryObject<Block> ELECTRIC_WIRE_CONNECTOR = registerBlock("electric_wire_connector",
            () -> new ElectricWireConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.electric_wire_connector.tooltip");

    public static final RegistryObject<Block> ELECTRIC_MAGNET_WIRE_CONNECTOR = registerBlock("electric_magnet_wire_connector",
            () -> new ElectricMagnetWireConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.electric_magnet_wire_connector.tooltip");

    public static final RegistryObject<Block> BELT_CONNECTOR = registerBlock("chain_connector",
            () -> new ChainConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.chain_connector.tooltip");

    public static final RegistryObject<Block> MAGNET_BELT_CONNECTOR = registerBlock("magnet_chain_connector",
            () -> new MagnetChainConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(3.0f).noOcclusion()),
            "item.vsfluidlink.magnet_chain_connector.tooltip");

    // 装飾用のダミーブロックを登録
    public static final RegistryObject<Block> HOSE_DECORATION = BLOCKS.register("hose_decoration",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().air()));
    public static final RegistryObject<Block> MAGNET_HOSE_DECORATION = BLOCKS.register("magnet_hose_decoration",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().air()));
    public static final RegistryObject<Block> SMALL_HOSE_DECORATION = BLOCKS.register("small_hose_decoration",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().air()));
    public static final RegistryObject<Block> SMALL_MAGNET_HOSE_DECORATION = BLOCKS.register("small_magnet_hose_decoration",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().air()));

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block, String tooltipKey) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn, tooltipKey);
        return toReturn;
    }

    private static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block, String tooltipKey) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable(tooltipKey));
                super.appendHoverText(stack, level, tooltip, flag);
            }
        });
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
