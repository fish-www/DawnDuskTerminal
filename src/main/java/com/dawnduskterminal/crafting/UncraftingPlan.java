package com.dawnduskterminal.crafting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * 试验物品 051 一次加工要吃什么、吐什么、要多久
 *
 * <p>{@link #products()} 定长 9，按 3x3 网格的顺序摆，空格是 {@link ItemStack#EMPTY}。
 * 这个顺序就是拆解台的顺序：有形的配方按宽高铺（{@code col + row * 3}），无形配方从 0 顺着排，
 * 所以「产物与拆解台相同」在位置上也成立，不只是物品数量相同
 *
 * <p>{@link #inputCount()} 是一次吃掉几个原料。反查合成配方时是那条配方的产物数量（拆一把钻石镐吃 1 把，
 * 拆一组 4 个火把吃 4 个），拆解配方时是配方自己写的 {@code count}，走熔炼回退时是 1
 *
 * <p>{@link #cookTime()} 是这一次加工要几 tick：拆解与反查配方都取熔炉配方的常规时间，见 {@link #DEFAULT_COOK_TIME}；
 * 熔炼回退则直接拿熔炼配方自己写的时间
 *
 * <p>{@link #products()} 里的物品只读，谁要拿走都得先 {@link ItemStack#copy()}——缓存的那一份会被反复读
 */
public record UncraftingPlan(List<ItemStack> products, int inputCount, int cookTime) {
    /** 产物格数，也就是拆解台那半边 3x3 的格数 */
    public static final int PRODUCT_SLOTS = 9;

    /**
     * 拆一次的加工时间：熔炉配方的常规时间，原版烧一个东西就是 200 tick（10 秒）
     *
     * <p>拆解本身没有「配方时间」可言（暮色森林的拆解台是即时拆、按经验收费），所以取熔炉的常规值，
     * 也就是原版 {@code AbstractFurnaceBlockEntity#getTotalCookTime} 找不到配方时给的那个 200
     */
    public static final int DEFAULT_COOK_TIME = 200;

    private static final UncraftingPlan NONE = new UncraftingPlan(emptyProducts(), 0, 0);

    /** 什么都不干：没有配方、原料不够、或者产物取不到 */
    public static UncraftingPlan none() {
        return NONE;
    }

    public static List<ItemStack> emptyProducts() {
        return productsOf(new ItemStack[PRODUCT_SLOTS]);
    }

    public static List<ItemStack> productsOf(ItemStack[] slots) {
        ItemStack[] copy = Arrays.copyOf(slots, PRODUCT_SLOTS);
        for (int i = 0; i < PRODUCT_SLOTS; i++) {
            if (copy[i] == null) {
                copy[i] = ItemStack.EMPTY;
            }
        }
        return Collections.unmodifiableList(Arrays.asList(copy));
    }

    public boolean isNone() {
        return this.inputCount <= 0 || this.cookTime <= 0;
    }

    /** 有没有至少一样产物。全是空格等于没有配方 */
    public boolean producesAnything() {
        for (ItemStack product : this.products) {
            if (!product.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
