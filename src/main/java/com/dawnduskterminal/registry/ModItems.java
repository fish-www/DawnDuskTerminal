package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.CanocanItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    public static final DeferredItem<Item> HYDRA_TROPHY = ITEMS.registerSimpleItem(
        "hydra_trophy",
        new Item.Properties().rarity(Rarity.RARE).fireResistant().stacksTo(1));

    public static final DeferredItem<Item> UR_GHAST_TROPHY = ITEMS.registerSimpleItem(
        "ur_ghast_trophy",
        new Item.Properties().rarity(Rarity.RARE).fireResistant().stacksTo(1));

    public static final DeferredItem<Item> SNOW_QUEEN_TROPHY = ITEMS.registerSimpleItem(
        "snow_queen_trophy",
        new Item.Properties().rarity(Rarity.RARE).fireResistant().stacksTo(1));

    public static final DeferredItem<CanocanItem> CANOCAN = ITEMS.register(
        "canocan",
        () -> new CanocanItem(new Item.Properties()));
    
    public static final DeferredItem<BucketItem> PORTAL_BUCKET = ITEMS.register(
        "portal_bucket",
        () -> new BucketItem(ModFluids.PORTAL_FLUID.get(),
            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(64)));

    public static final DeferredItem<Item> ENCASED_DUST = ITEMS.registerSimpleItem(
        "encased_dust",
        new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(1));

    public static final DeferredItem<Item> RAW_SCARLET_MOLYBDENUM = ITEMS.registerSimpleItem(
        "rawscarletmolybdenum", new Item.Properties());

    public static final DeferredItem<Item> GARNET = ITEMS.registerSimpleItem(
        "garnet", new Item.Properties());

    public static final DeferredItem<Item> SKY_SLICES = ITEMS.registerSimpleItem(
        "sky_slices", new Item.Properties());

    public static final DeferredItem<Item> OCHRE = ITEMS.registerSimpleItem(
        "ochre", new Item.Properties());

    public static final DeferredItem<Item> CANOPY_FROST_BALL = ITEMS.registerSimpleItem(
        "canopy_frost_ball", new Item.Properties());

    public static final DeferredItem<Item> ILLUSTRIOUS_POWDER = ITEMS.registerSimpleItem(
        "illustrious_powder", new Item.Properties());

    public static final DeferredItem<Item> CARID = ITEMS.registerSimpleItem(
        "carid", new Item.Properties());

    public static final DeferredItem<Item> AOI = ITEMS.registerSimpleItem(
        "aoi", new Item.Properties());

    public static final DeferredItem<Item> CANOPY_INGOT = ITEMS.registerSimpleItem(
        "canopy_ingot", new Item.Properties());

    public static final DeferredItem<Item> DIRAC = ITEMS.registerSimpleItem(
        "dirac", new Item.Properties());

    public static final DeferredItem<Item> MIXED_CARMINITE = ITEMS.registerSimpleItem(
        "mixed_carminite", new Item.Properties());

    public static final DeferredItem<Item> CARMINITE_INGOT = ITEMS.registerSimpleItem(
        "carminite_ingot", new Item.Properties());

    public static final DeferredItem<Item> SCARLET_MOLYBDENUM_INGOT = ITEMS.registerSimpleItem(
        "scarlet_molybdenum_ingot", new Item.Properties());

    public static final DeferredItem<Item> HUI_INGOT = ITEMS.registerSimpleItem(
        "hui_ingot", new Item.Properties());

    public static final DeferredItem<Item> DIRAC_INGOT = ITEMS.registerSimpleItem(
        "dirac_ingot", new Item.Properties());

    public static final DeferredItem<BlockItem> CHRONO_CRUST = ITEMS.registerSimpleBlockItem(ModBlocks.CHRONO_CRUST);

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
