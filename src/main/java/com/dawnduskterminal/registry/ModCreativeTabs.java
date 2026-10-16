package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dawnduskterminal.main"))
            .icon(() -> new ItemStack(ModItems.ENCASED_DUST.get()))
            .displayItems((params, output) -> {
                accept(output, ModItems.HYDRA_TROPHY.get());
                accept(output, ModItems.UR_GHAST_TROPHY.get());
                accept(output, ModItems.SNOW_QUEEN_TROPHY.get());
                accept(output, ModItems.ENCASED_DUST.get());
                accept(output, ModItems.CANOCAN.get());
                accept(output, ModItems.PORTAL_BUCKET.get());
                accept(output, ModScorchingTwilight.BUCKET.get());
                accept(output, ModItems.CHRONO_CRUST.get());
                accept(output, ModTerrainBlocks.ROSY_GRASS_ITEM.get());
                accept(output, ModTerrainBlocks.ROSY_DIRT_ITEM.get());
                accept(output, ModTerrainBlocks.CANOPY_STONE_ITEM.get());
                accept(output, ModTerrainBlocks.OCHRE_ITEM.get());
                accept(output, ModTerrainBlocks.GREENSCHIST_ITEM.get());
                accept(output, ModTerrainBlocks.ECLOGITE_ITEM.get());
                accept(output, ModTerrainBlocks.CHARNOCKITE_ITEM.get());
                accept(output, ModTerrainBlocks.TWILIGHT_STONE_ITEM.get());
                accept(output, ModTerrainBlocks.ROAD_SAND_ITEM.get());
                accept(output, ModTerrainBlocks.UNCONSCIOUS_SAND_ITEM.get());
                accept(output, ModTerrainBlocks.SILTSTONE_ITEM.get());
                accept(output, ModTerrainBlocks.FROZEN_CANOPY_ITEM.get());
                accept(output, ModTerrainBlocks.FRAGILE_CANOPY_ITEM.get());
                accept(output, ModTerrainBlocks.CANOPY_FROST_ITEM.get());
                accept(output, ModTerrainBlocks.CANOPY_FROST_BLOCK_ITEM.get());
                accept(output, ModTerrainBlocks.CHAOS_STONE_ITEM.get());
                accept(output, ModTerrainBlocks.BLAZING_BLOCK_ITEM.get());
                accept(output, ModTerrainBlocks.MOUNTED_TOWERWOOD_ITEM.get());
                accept(output, ModTerrainBlocks.ENCASED_TOWERBEAM_ITEM.get());
                accept(output, ModTerrainBlocks.SKYGATE_PINE_LOG_ITEM.get());
                accept(output, ModTerrainBlocks.SKYGATE_PINE_LEAVES_ITEM.get());
                accept(output, ModTerrainBlocks.SKYGATE_PINE_SAPLING_ITEM.get());
                accept(output, ModTerrainBlocks.SCARLET_MOLYBDENUM_ORE_ITEM.get());
                accept(output, ModChronoOres.DEEP_SCARLET_ITEM.get());
                accept(output, ModChronoOres.CARID_ORE_ITEM.get());
                accept(output, ModChronoOres.DIRAC_ORE_ITEM.get());
                accept(output, ModItems.RAW_SCARLET_MOLYBDENUM.get());
                accept(output, ModItems.GARNET.get());
                accept(output, ModItems.SKY_SLICES.get());
                accept(output, ModItems.OCHRE.get());
                accept(output, ModItems.CANOPY_FROST_BALL.get());
                accept(output, ModItems.ILLUSTRIOUS_POWDER.get());
                accept(output, ModItems.CARID.get());
                accept(output, ModItems.AOI.get());
                accept(output, ModItems.CANOPY_INGOT.get());
                accept(output, ModItems.DIRAC.get());
                accept(output, ModItems.MIXED_CARMINITE.get());
                accept(output, ModItems.CARMINITE_INGOT.get());
                accept(output, ModItems.SCARLET_MOLYBDENUM_INGOT.get());
                accept(output, ModItems.HUI_INGOT.get());
                accept(output, ModItems.DIRAC_INGOT.get());
                accept(output, ModItems.CARMINITE_GHAST_LEATHER.get());
                accept(output, ModItems.CARMINITED_LEATHER.get());
                accept(output, ModItems.CARMINITE_GHAST_MEAT.get());
                accept(output, ModItems.COOKED_CARMIN_GHAST_MEAT.get());
                accept(output, ModItems.CARMINITE_BALL.get());
                accept(output, ModItems.CARMINITE_MOLYBDENUM_REACTOR_STAFF.get());
                accept(output, ModItems.SCARLET_MOLYBD_HELMET.get());
                accept(output, ModItems.SCARLET_MOLYBD_CHESTPLATE.get());
                accept(output, ModItems.SCARLET_MOLYBD_LEGGINGS.get());
                accept(output, ModItems.SCARLET_MOLYBD_BOOTS.get());
                ModEquipment.sets().values().forEach(set -> set.items().forEach(item -> accept(output, item.get())));
                acceptForeign(output, "twilightforest:aurora_block");
            })
            .build());

    private static void acceptForeign(CreativeModeTab.Output output, String id) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        if (block == Blocks.AIR) {
            return;
        }
        output.accept(block);
    }

    private static void accept(CreativeModeTab.Output output, ItemLike item) {
        if (item == null || item.asItem() == Items.AIR) {
            DawnDuskTerminal.LOGGER.warn("DawnDuskTerminal creative entry skipped, no item is registered for {}", item);
            return;
        }
        output.accept(item);
    }

    private ModCreativeTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
