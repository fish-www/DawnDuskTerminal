package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.CanocanItem;
import com.dawnduskterminal.item.CarminiteGhastMeatItem;
import com.dawnduskterminal.item.CarminiteStaffItem;
import com.dawnduskterminal.item.ScarletMolybdArmorItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
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

    // 生肉照腐肉那一套：吃下大概率给饥饿，已经饥饿时还会加重并附带中毒。熟肉是普通食物
    private static final FoodProperties RAW_GHAST_MEAT = new FoodProperties.Builder()
        .nutrition(2)
        .saturationModifier(0.3F)
        .build();

    private static final FoodProperties COOKED_GHAST_MEAT = new FoodProperties.Builder()
        .nutrition(8)
        .saturationModifier(0.8F)
        .build();

    public static final DeferredItem<Item> CARMINITE_GHAST_LEATHER = ITEMS.registerSimpleItem(
        "carminite_ghast_leather", new Item.Properties());

    public static final DeferredItem<Item> CARMINITED_LEATHER = ITEMS.registerSimpleItem(
        "carminited_leather", new Item.Properties());

    public static final DeferredItem<CarminiteGhastMeatItem> CARMINITE_GHAST_MEAT = ITEMS.register(
        "carminite_ghast_meat",
        () -> new CarminiteGhastMeatItem(new Item.Properties().food(RAW_GHAST_MEAT)));

    public static final DeferredItem<Item> COOKED_CARMIN_GHAST_MEAT = ITEMS.register(
        "cooked_carmin_ghast_meat",
        () -> new Item(new Item.Properties().food(COOKED_GHAST_MEAT)));

    /** 权杖的弹药，也是合成材料 */
    public static final DeferredItem<Item> CARMINITE_BALL = ITEMS.registerSimpleItem(
        "carminite_ball", new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<CarminiteStaffItem> CARMINITE_MOLYBDENUM_REACTOR_STAFF = ITEMS.register(
        "carminite_molybdenum_reactor_staff",
        () -> new CarminiteStaffItem(new Item.Properties()
            .durability(128)
            .attributes(CarminiteStaffItem.createAttributes())));

    // T2 护甲，数值在 ModArmorMaterials 里，四件穿齐时每件自己续生命恢复
    public static final DeferredItem<ScarletMolybdArmorItem> SCARLET_MOLYBD_HELMET = armor(
        "scarlet_molybd_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<ScarletMolybdArmorItem> SCARLET_MOLYBD_CHESTPLATE = armor(
        "scarlet_molybd_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ScarletMolybdArmorItem> SCARLET_MOLYBD_LEGGINGS = armor(
        "scarlet_molybd_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ScarletMolybdArmorItem> SCARLET_MOLYBD_BOOTS = armor(
        "scarlet_molybd_boots", ArmorItem.Type.BOOTS);

    public static final DeferredItem<BlockItem> CHRONO_CRUST = ITEMS.registerSimpleBlockItem(ModBlocks.CHRONO_CRUST);

    private static DeferredItem<ScarletMolybdArmorItem> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new ScarletMolybdArmorItem(
            ModArmorMaterials.SCARLET_MOLYBDENUM,
            type,
            new Item.Properties().durability(type.getDurability(37))));
    }

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
