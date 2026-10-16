package com.dawnduskterminal.gametest;

import com.dawnduskterminal.item.CarminiteStaffItem;
import com.dawnduskterminal.registry.ModArmorMaterials;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 迁移过来的装备只能在这里验证到「数值与注册」，行为（发射 命中 掉血）靠实机
 *
 * <p>护甲等级是任务里写死的要求：T1 对标钻石，也就是四件合计 20 点护甲、2 点韧性。这个数写成断言，
 * 以后调数值时会立刻发现
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class EquipmentTests {
    /** 钻石护甲四件合计的护甲点数 */
    private static final int DIAMOND_DEFENSE = 20;
    /** 钻石护甲的韧性 */
    private static final float DIAMOND_TOUGHNESS = 2.0F;
    /** 钻石护甲的耐久倍率 */
    private static final int DIAMOND_DURABILITY_FACTOR = 33;

    private static final ArmorItem.Type[] SLOTS = {
        ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS,
    };

    @GameTest(template = "ddt_empty")
    public static void scarletMolybdArmorMatchesDiamond(GameTestHelper helper) {
        ArmorMaterial material = ModArmorMaterials.SCARLET_MOLYBDENUM.value();
        int defense = 0;
        for (ArmorItem.Type type : SLOTS) {
            defense += material.getDefense(type);
        }
        helper.assertTrue(defense == DIAMOND_DEFENSE,
            "T1 armor must total " + DIAMOND_DEFENSE + " defense, got " + defense);
        helper.assertTrue(material.toughness() == DIAMOND_TOUGHNESS,
            "T1 armor toughness must be " + DIAMOND_TOUGHNESS + ", got " + material.toughness());
        helper.assertTrue(material.enchantmentValue() == 10,
            "T1 armor enchantment value must be 10, got " + material.enchantmentValue());

        ArmorItem helmet = ModItems.SCARLET_MOLYBD_HELMET.get();
        int expected = ArmorItem.Type.HELMET.getDurability(DIAMOND_DURABILITY_FACTOR);
        int actual = new ItemStack(helmet).getMaxDamage();
        helper.assertTrue(actual == expected,
            "helmet durability must be " + expected + ", got " + actual);

        // 四个部件必须是同一个材质，否则穿齐时属性会缺一块
        helper.assertTrue(ModItems.SCARLET_MOLYBD_CHESTPLATE.get().getMaterial().value() == material,
            "chestplate must use the scarlet molybdenum material");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void carminiteEquipmentIsRegistered(GameTestHelper helper) {
        helper.assertTrue(ModEntities.CARMINITE_BALL.get().getCategory() == MobCategory.MISC,
            "the carminite ball must be a MISC entity");
        helper.assertTrue(edible(ModItems.COOKED_CARMIN_GHAST_MEAT.get()),
            "cooked carminite ghast meat must be edible");
        helper.assertTrue(edible(ModItems.CARMINITE_GHAST_MEAT.get()),
            "raw carminite ghast meat must be edible");
        helper.assertTrue(ModItems.CARMINITE_MOLYBDENUM_REACTOR_STAFF.get() instanceof CarminiteStaffItem,
            "the reactor staff must be registered as a staff");
        helper.succeed();
    }

    private static boolean edible(Item item) {
        return new ItemStack(item).get(DataComponents.FOOD) != null;
    }
}
