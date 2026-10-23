package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModExperimentItems;
import com.dawnduskterminal.registry.ModItems;
import java.util.List;
import java.util.SortedMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 试验物品 037~042 与 104 的规格：五件工具是钻石级，042 是普通物品，104 是两口吃完的食物
 *
 * <p>「钻石级」断言成 {@code getTier() == Tiers.DIAMOND} 而不是逐个比数值：耐久 1561、速度 8、
 * 攻击加成 3、附魔值 10、挖不动的方块 {@code INCORRECT_FOR_DIAMOND_TOOL} 全挂在那个 tier 实例上，
 * 换成自建 tier 才是会悄悄跑偏的地方
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class ExperimentItemTests {
    private static final List<Item> TOOLS = List.of(
        ModExperimentItems.EXPERIMENT_037.get(),
        ModExperimentItems.EXPERIMENT_038.get(),
        ModExperimentItems.EXPERIMENT_039.get(),
        ModExperimentItems.EXPERIMENT_040.get(),
        ModExperimentItems.EXPERIMENT_041.get());

    @GameTest(template = "ddt_empty")
    public static void toolsUseDiamondTier(GameTestHelper helper) {
        List<Class<?>> expected = List.of(SwordItem.class, PickaxeItem.class, HoeItem.class,
            ShovelItem.class, AxeItem.class);
        for (int i = 0; i < TOOLS.size(); i++) {
            Item item = TOOLS.get(i);
            String name = item.getDescriptionId();
            helper.assertTrue(expected.get(i).isInstance(item),
                name + " must be a " + expected.get(i).getSimpleName() + ", got " + item.getClass().getSimpleName());
            helper.assertTrue(item instanceof TieredItem, name + " must be a TieredItem");
            Tier tier = ((TieredItem) item).getTier();
            helper.assertTrue(tier == Tiers.DIAMOND,
                name + " must use Tiers.DIAMOND, got " + tier);
        }
        // 钉住「钻石级」这组数字，免得哪天 Tiers.DIAMOND 被换掉还装作没事
        helper.assertTrue(Tiers.DIAMOND.getUses() == 1561, "sanity: diamond tier uses moved");
        helper.assertTrue(Tiers.DIAMOND.getSpeed() == 8.0F, "sanity: diamond tier speed moved");
        helper.assertTrue(Tiers.DIAMOND.getAttackDamageBonus() == 3.0F, "sanity: diamond attack bonus moved");
        helper.assertTrue(Tiers.DIAMOND.getEnchantmentValue() == 10, "sanity: diamond enchantment value moved");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void toolsSitInVanillaToolTags(GameTestHelper helper) {
        // 原版附魔标签是从这五个物品标签派生的，缺一个这工具在附魔台上就没有附魔可给
        assertTagged(helper, ModExperimentItems.EXPERIMENT_037.get(), ItemTags.SWORDS, "swords");
        assertTagged(helper, ModExperimentItems.EXPERIMENT_038.get(), ItemTags.PICKAXES, "pickaxes");
        assertTagged(helper, ModExperimentItems.EXPERIMENT_039.get(), ItemTags.HOES, "hoes");
        assertTagged(helper, ModExperimentItems.EXPERIMENT_040.get(), ItemTags.SHOVELS, "shovels");
        assertTagged(helper, ModExperimentItems.EXPERIMENT_041.get(), ItemTags.AXES, "axes");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void experiment042IsAPlainItem(GameTestHelper helper) {
        Item item = ModExperimentItems.EXPERIMENT_042.get();
        helper.assertFalse(item instanceof TieredItem,
            "experiment_042 must stay a plain item, not a tool");
        helper.assertTrue(new ItemStack(item).getMaxStackSize() == 64,
            "experiment_042 must stack to 64, got " + new ItemStack(item).getMaxStackSize());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void experiment104IsEatenInTwoBites(GameTestHelper helper) {
        ItemStack whole = new ItemStack(ModItems.EXPERIMENT_104.get());
        ItemStack half = new ItemStack(ModItems.EXPERIMENT_104_HALF.get());
        FoodProperties firstBite = whole.get(DataComponents.FOOD);
        FoodProperties secondBite = half.get(DataComponents.FOOD);
        helper.assertTrue(firstBite != null && secondBite != null,
            "both experiment_104 and its half must be edible");
        helper.assertTrue(whole.getMaxStackSize() == 1,
            "experiment_104 must not stack, got " + whole.getMaxStackSize());
        helper.assertTrue(half.getMaxStackSize() == 1,
            "the half must not stack either, got " + half.getMaxStackSize());
        // 「一个能吃两口」全靠这一条：第一口把整块换成半块，第二口才把东西吃掉
        Item remainder = firstBite.usingConvertsTo().orElse(ItemStack.EMPTY).getItem();
        helper.assertTrue(remainder == ModItems.EXPERIMENT_104_HALF.get(),
            "the first bite must leave the half behind, got " + remainder.getDescriptionId());
        helper.assertTrue(secondBite.usingConvertsTo().isEmpty(),
            "the last bite must leave nothing behind, got " + secondBite.usingConvertsTo());
        // 一口 10 点饥饿、0.6 饱和度系数（饱食度 10 × 0.6 × 2 = 12.0，等于两块烤马铃薯），
        // 两口合计 20 点饥饿、饱食度 24.0
        helper.assertTrue(firstBite.nutrition() == 10,
            "the first bite must be 10 hunger, got " + firstBite.nutrition());
        helper.assertTrue(secondBite.nutrition() == 10,
            "the second bite must be 10 hunger, got " + secondBite.nutrition());
        helper.assertTrue(Math.abs(firstBite.saturation() - 12.0F) < 0.01F,
            "the first bite must be 12 saturation, got " + firstBite.saturation());
        int hunger = firstBite.nutrition() + secondBite.nutrition();
        helper.assertTrue(hunger == 20, "both bites must add up to 20 hunger, got " + hunger);
        float saturation = firstBite.saturation() + secondBite.saturation();
        helper.assertTrue(Math.abs(saturation - 24.0F) < 0.01F,
            "both bites must add up to 24 saturation, got " + saturation);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void placeholdersFillEveryGapUpTo115(GameTestHelper helper) {
        SortedMap<Integer, DeferredItem<Item>> placeholders = ModExperimentItems.placeholders();
        // 1..115 共 115 个号，被占的是 001 方块、033/034 两支长矛、037~042 六件、046 权杖、051 拆解台、
        // 104 那个食物、114 那块蛋糕、115 暮色森林那件
        helper.assertTrue(placeholders.size() == 101,
            "placeholders must cover the 101 free numbers of 1..115, got " + placeholders.size());
        for (int number : new int[] {2, 32, 35, 43, 45, 47, 113}) {
            helper.assertTrue(placeholders.containsKey(number),
                "number " + number + " must have a placeholder item");
        }
        for (int number : new int[] {1, 33, 34, 37, 38, 39, 40, 41, 42, 46, 51, 104, 114, 115}) {
            helper.assertFalse(placeholders.containsKey(number),
                "number " + number + " is taken and must not get a placeholder item");
        }
        // 注册名必须就是补零三位的序号，不然补出来的是个对不上号的空壳
        placeholders.forEach((number, holder) -> helper.assertTrue(
            holder.getId().getPath().equals("experiment_%03d".formatted(number)),
            "placeholder for " + number + " registered as " + holder.getId()));
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void spearItemsUseBowAnimation(GameTestHelper helper) {
        for (Item spear : List.of(ModExperimentItems.EXPERIMENT_033.get(),
            ModExperimentItems.EXPERIMENT_034.get())) {
            ItemStack stack = new ItemStack(spear);
            // 拉弓动画走原版 UseAnim.BOW：第一人称的拉满过程与第三人称的双臂姿势都由此而来，
            // 前提是模型带上 item/bow 那套 thirdperson 变换，见两个 model json
            helper.assertTrue(stack.getUseAnimation() == UseAnim.BOW,
                spear.getDescriptionId() + " must animate like a drawn bow, got " + stack.getUseAnimation());
            helper.assertTrue(stack.getUseDuration(null) == 72000,
                spear.getDescriptionId() + " must stay usable as long as a bow, got " + stack.getUseDuration(null));
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void spearPrickDamagesWithoutKnockback(GameTestHelper helper) {
        // 原版甜浆果丛那个伤害类型：普通伤害，且在 #minecraft:no_knockback 里
        DamageSource prick = helper.getLevel().damageSources().sweetBerryBush();
        helper.assertTrue(prick.is(DamageTypeTags.NO_KNOCKBACK),
            "minecraft:sweet_berry_bush must sit in #minecraft:no_knockback, otherwise every prick shoves the target away");
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        zombie.setDeltaMovement(Vec3.ZERO);
        float healthBefore = zombie.getHealth();
        helper.assertTrue(zombie.hurt(prick, 1.0F), "the spear prick must go through");
        helper.assertTrue(zombie.getHealth() < healthBefore,
            "the zombie must lose health, before " + healthBefore + " after " + zombie.getHealth());
        helper.assertTrue(zombie.getDeltaMovement().lengthSqr() < 1.0E-6,
            "the prick must not knock the zombie back, got " + zombie.getDeltaMovement());
        helper.succeed();
    }

    private static void assertTagged(GameTestHelper helper, Item item, TagKey<Item> tag, String tagName) {
        helper.assertTrue(new ItemStack(item).is(tag),
            item.getDescriptionId() + " must be in #minecraft:" + tagName
                + ", otherwise the enchanting table offers it nothing");
    }

    private ExperimentItemTests() {}
}
