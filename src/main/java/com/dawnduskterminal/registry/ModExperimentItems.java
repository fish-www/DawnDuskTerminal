package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.ThrustSpearItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 试验物品栏里那批实验品：两支长矛（试验物品 033、034）、高塔五件套（试验物品 037~041）与一件简单物品（试验物品 042）
 *
 * <p>整批试验物品的注册 ID 与显示名都用三位数序列，含已经就是三位的 {@code experiment_001}，
 * 所以这里从 {@code experiment_033} 起步，不是 {@code experiment_33}
 *
 * <p>五件工具直接挂 {@link Tiers#DIAMOND}，于是耐久 1561、挖掘速度 8、攻击加成 3、附魔值 10、
 * 挖不动的方块是 {@code INCORRECT_FOR_DIAMOND_TOOL}，与原版钻石工具逐项相同；攻击力与攻速的底数
 * 照抄 1.21.1 的 {@code Items}（剑 3/-2.4、镐 1/-2.8、斧 5/-3.0、锹 1.5/-3.0、锄 -3/0）
 *
 * <p>没有配方文件，所以不可合成，也不需要另外禁止什么
 *
 * <p>「高塔剑」那行是物品描述，不是名字：名字是「试验物品NNN」，
 * 与 {@code carminite_molybdenum_reactor_staff}（试验物品046）一致
 */
public final class ModExperimentItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    public static final DeferredItem<SwordItem> EXPERIMENT_037 = ITEMS.register("experiment_037",
        () -> new SwordItem(Tiers.DIAMOND, new Item.Properties()
            .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3.0F, -2.4F))) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                        List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                describe(tooltip, "item.dawnduskterminal.experiment_037.desc");
            }
        });

    public static final DeferredItem<PickaxeItem> EXPERIMENT_038 = ITEMS.register("experiment_038",
        () -> new PickaxeItem(Tiers.DIAMOND, new Item.Properties()
            .attributes(PickaxeItem.createAttributes(Tiers.DIAMOND, 1.0F, -2.8F))) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                        List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                describe(tooltip, "item.dawnduskterminal.experiment_038.desc");
            }
        });

    public static final DeferredItem<HoeItem> EXPERIMENT_039 = ITEMS.register("experiment_039",
        () -> new HoeItem(Tiers.DIAMOND, new Item.Properties()
            .attributes(HoeItem.createAttributes(Tiers.DIAMOND, -3.0F, 0.0F))) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                        List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                describe(tooltip, "item.dawnduskterminal.experiment_039.desc");
            }
        });

    public static final DeferredItem<ShovelItem> EXPERIMENT_040 = ITEMS.register("experiment_040",
        () -> new ShovelItem(Tiers.DIAMOND, new Item.Properties()
            .attributes(ShovelItem.createAttributes(Tiers.DIAMOND, 1.5F, -3.0F))) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                        List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                describe(tooltip, "item.dawnduskterminal.experiment_040.desc");
            }
        });

    public static final DeferredItem<AxeItem> EXPERIMENT_041 = ITEMS.register("experiment_041",
        () -> new AxeItem(Tiers.DIAMOND, new Item.Properties()
            .attributes(AxeItem.createAttributes(Tiers.DIAMOND, 5.0F, -3.0F))) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                        List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                describe(tooltip, "item.dawnduskterminal.experiment_041.desc");
            }
        });

    /** 试验物品 042：一件普通物品，没有行为也没有描述 */
    public static final DeferredItem<Item> EXPERIMENT_042 = ITEMS.registerSimpleItem(
        "experiment_042", new Item.Properties());

    /** 试验物品 033、034：按住右键架起来突刺的长矛，行为在 {@link ThrustSpearItem} */
    public static final DeferredItem<ThrustSpearItem> EXPERIMENT_033 = ITEMS.register(
        "experiment_033", () -> new ThrustSpearItem(new Item.Properties()));

    public static final DeferredItem<ThrustSpearItem> EXPERIMENT_034 = ITEMS.register(
        "experiment_034", () -> new ThrustSpearItem(new Item.Properties()));

    /** 序号上限：115 及以内 */
    private static final int LAST_NUMBER = 115;

    /**
     * 已经被占掉的序号
     *
     * <p>001 是能当合成台用的方块，033/034 是上面那两支长矛，051 是拆解台那台机器（方块在
     * {@link ModBlocks#EXPERIMENT_051}、物品在 {@link ModItems#EXPERIMENT_051}），037~042 是上面那批工具与简单物品，
     * 046 是砷铅铁钼反应权杖（注册 ID 仍是 {@code carminite_molybdenum_reactor_staff}），
     * 104 是那个能吃的东西（在 {@link ModItems#EXPERIMENT_104}），114 是能吃能摆的那一块（在
     * {@link ModItems#EXPERIMENT_114}），115 是暮色森林自带的试验物品
     *
     * <p>剩下的空号全部补成占位简单物品，贴图统一借 {@code experiment_temp.png}
     */
    private static final Set<Integer> TAKEN = Set.of(1, 33, 34, 37, 38, 39, 40, 41, 42, 46, 51, 104, 114, 115);

    private static final SortedMap<Integer, DeferredItem<Item>> PLACEHOLDERS = registerPlaceholders();

    /**
     * 115 以内的空号，按序号升序
     *
     * <p>注册名由序号补零到三位拼出来，所以这里不存在手写 ID 与序号对不上的可能
     */
    public static SortedMap<Integer, DeferredItem<Item>> placeholders() {
        return PLACEHOLDERS;
    }

    private static SortedMap<Integer, DeferredItem<Item>> registerPlaceholders() {
        SortedMap<Integer, DeferredItem<Item>> map = new TreeMap<>();
        for (int number = 1; number <= LAST_NUMBER; number++) {
            if (TAKEN.contains(number)) {
                continue;
            }
            String id = "experiment_%03d".formatted(number);
            map.put(number, ITEMS.registerSimpleItem(id, new Item.Properties()));
        }
        return Collections.unmodifiableSortedMap(map);
    }

    private static void describe(List<Component> tooltip, String key) {
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    private ModExperimentItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
