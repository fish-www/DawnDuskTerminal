package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * 护甲材质
 *
 * <p>每个材质只声明数值与贴图层，具体物品由 {@link ModItems} 注册。贴图层按
 * {@code textures/models/armor/<路径>_layer_1.png}（外层，头 胸 脚）与
 * {@code _layer_2.png}（内层，腿）解析，两条路径都由 {@link ArmorMaterial.Layer} 拼出来
 */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, DawnDuskTerminal.MOD_ID);

    /**
     * T1 绯色钼。数值对标钻石：护甲 20 点、韧性 2、附魔值 10、耐久倍率 33（
     * {@link ArmorItem.Type#getDurability(int)} 用的是原版每槽基础耐久 11 16 15 13）
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SCARLET_MOLYBDENUM =
        ARMOR_MATERIALS.register("scarlet_molybdenum", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 3,
                ArmorItem.Type.CHESTPLATE, 8,
                ArmorItem.Type.LEGGINGS, 6,
                ArmorItem.Type.BOOTS, 3),
            10,
            SoundEvents.ARMOR_EQUIP_DIAMOND,
            () -> Ingredient.of(ModItems.SCARLET_MOLYBDENUM_INGOT.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("scarlet_molybdenum"))),
            2.0F,
            0.0F));

    /**
     * 天门松原木，对标铁：护甲 15 点、韧性 0、附魔值 9、耐久倍率 15
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SKYGATE_PINE =
        ARMOR_MATERIALS.register("skygate_pine", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 2,
                ArmorItem.Type.CHESTPLATE, 6,
                ArmorItem.Type.LEGGINGS, 5,
                ArmorItem.Type.BOOTS, 2),
            9,
            SoundEvents.ARMOR_EQUIP_IRON,
            () -> Ingredient.of(ModTerrainBlocks.SKYGATE_PINE_LOG.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("skygate_pine"))),
            0.0F,
            0.0F));

    /** 苍穹锭，对标钻石 */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CANOPY =
        ARMOR_MATERIALS.register("canopy", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 3,
                ArmorItem.Type.CHESTPLATE, 8,
                ArmorItem.Type.LEGGINGS, 6,
                ArmorItem.Type.BOOTS, 3),
            10,
            SoundEvents.ARMOR_EQUIP_DIAMOND,
            () -> Ingredient.of(ModItems.CANOPY_INGOT.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("canopy"))),
            2.0F,
            0.0F));

    /** 砷铅铁锭，对标钻石 */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CARMINITE =
        ARMOR_MATERIALS.register("carminite", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 3,
                ArmorItem.Type.CHESTPLATE, 8,
                ArmorItem.Type.LEGGINGS, 6,
                ArmorItem.Type.BOOTS, 3),
            10,
            SoundEvents.ARMOR_EQUIP_DIAMOND,
            () -> Ingredient.of(ModItems.CARMINITE_INGOT.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("carminite"))),
            2.0F,
            0.0F));

    private ModArmorMaterials() {}

    public static void register(IEventBus bus) {
        ARMOR_MATERIALS.register(bus);
    }
}
