package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.DiracOreBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModChronoOres {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DawnDuskTerminal.MOD_ID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    /**
     * 与原版矿石一一对应的调色板矿石。列表里的字符串就是注册名，方块与物品一一配对
     *
     * <p>它们目前不由世界生成铺出，见 README 的待定项。将来接线时按名字从 {@link #ORES} 取，不必再
     * 一个个写字段
     */
    private static final List<String> ORE_NAMES = List.of(
        "chrono_ore_coal_lower",
        "chrono_ore_coal_upper",
        "chrono_ore_iron_small",
        "chrono_ore_iron_middle",
        "chrono_ore_iron_upper",
        "chrono_ore_copper",
        "chrono_ore_copper_large",
        "chrono_ore_gold",
        "chrono_ore_gold_lower",
        "chrono_ore_redstone",
        "chrono_ore_redstone_lower",
        "chrono_ore_lapis",
        "chrono_ore_lapis_buried",
        "chrono_ore_diamond",
        "chrono_ore_diamond_buried",
        "chrono_ore_diamond_large",
        "chrono_ore_diamond_medium",
        "chrono_ore_emerald",
        "chrono_ore_granite_upper",
        "chrono_ore_granite_lower",
        "chrono_ore_diorite_upper",
        "chrono_ore_diorite_lower",
        "chrono_ore_andesite_upper",
        "chrono_ore_andesite_lower",
        "chrono_ore_tuff",
        "chrono_ore_gravel",
        "chrono_ore_clay",
        "chrono_ore_infested",
        "chrono_ore_magma"
    );

    public static final Map<String, DeferredBlock<Block>> ORES = registerOres();

    public static final Map<String, DeferredItem<BlockItem>> ORE_ITEMS = registerOreItems();

    public static final DeferredBlock<Block> DEEP_SCARLET = BLOCKS.registerSimpleBlock(
        "charnockite_scarlet_molybdenum_ore", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .requiresCorrectToolForDrops()
            .strength(5F, 9.0F)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> CARID_ORE = BLOCKS.registerSimpleBlock(
        "carid_ore", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .requiresCorrectToolForDrops()
            .strength(3.0F, 9.0F)
            .sound(SoundType.STONE));

    public static final DeferredBlock<DiracOreBlock> DIRAC_ORE = BLOCKS.registerBlock(
        "dirac_ore", DiracOreBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE)
            .requiresCorrectToolForDrops()
            .strength(6.0F, 9.0F)
            .sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> DEEP_SCARLET_ITEM = ITEMS.registerSimpleBlockItem(DEEP_SCARLET);
    public static final DeferredItem<BlockItem> CARID_ORE_ITEM = ITEMS.registerSimpleBlockItem(CARID_ORE);
    public static final DeferredItem<BlockItem> DIRAC_ORE_ITEM = ITEMS.registerSimpleBlockItem(DIRAC_ORE);

    private static Map<String, DeferredBlock<Block>> registerOres() {
        Map<String, DeferredBlock<Block>> ores = new LinkedHashMap<>();
        for (String name : ORE_NAMES) {
            ores.put(name, BLOCKS.registerSimpleBlock(name, ore()));
        }
        return Map.copyOf(ores);
    }

    private static Map<String, DeferredItem<BlockItem>> registerOreItems() {
        Map<String, DeferredItem<BlockItem>> items = new LinkedHashMap<>();
        for (Map.Entry<String, DeferredBlock<Block>> ore : ORES.entrySet()) {
            items.put(ore.getKey(), ITEMS.registerSimpleBlockItem(ore.getValue()));
        }
        return Map.copyOf(items);
    }

    /** 调色板矿石的共同属性，与原版矿石一致：硬度 3、爆炸抗性 3、石头音效 */
    private static BlockBehaviour.Properties ore() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(3.0F, 3.0F)
            .sound(SoundType.STONE);
    }

    private ModChronoOres() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
