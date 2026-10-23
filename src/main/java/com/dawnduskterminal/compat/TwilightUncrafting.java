package com.dawnduskterminal.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * 暮色森林拆解台的只读桥
 *
 * <p>本模组不允许 {@code import twilightforest.*}：源码里一个暮色森林的类都不引用，缺 jar 也能编译
 * （见 {@code AGENTS.md}）。所以拆解台那两样东西——配方类型 {@code twilightforest:uncrafting} 与配方类
 * {@code twilightforest.item.recipe.UncraftingRecipe}——只能按字符串拿：配方类型走注册表
 * （{@link BuiltInRegistries#RECIPE_TYPE}，查不到就是没装），配方上的两个方法走反射
 *
 * <p>反射只碰两个方法：{@code isItemStackAnIngredient(ItemStack)} 判断「这个物品能不能拆」，
 * {@code getCount()} 取「拆一次吃掉几个」。产物不走反射——{@code UncraftingRecipe} 继承
 * {@code ShapedRecipe}，用原版的 {@link Recipe#getIngredients()} 加宽高就能摆出 3x3
 *
 * <p>同一份配置也读一遍：{@code allowShapelessUncrafting}（无形配方算不算可拆）、
 * {@code disableUncraftingRecipes} 与 {@code reverseRecipeBlacklist}（配方黑/白名单）、
 * {@code blacklistedUncraftingModIds} 与 {@code flipUncraftingModIdList}（按模组 id 的黑/白名单）。
 * 不在这里重新发明一套开关，改了暮色森林的配置，两块表的产物还是同一份。读不到时按暮色森林的默认值算：
 * 只拆有形的、两份名单都空、两个 flip 都关着（名单默认是黑名单）
 */
public final class TwilightUncrafting {
    /** 暮色森林拆解配方的类型 id */
    public static final String RECIPE_TYPE_ID = "twilightforest:uncrafting";

    private static final String RECIPE_CLASS = "twilightforest.item.recipe.UncraftingRecipe";

    private static final String CONFIG_CLASS = "twilightforest.config.TFConfig";

    private static final String FIELD_ALLOW_SHAPELESS = "allowShapelessUncrafting";

    private static final String FIELD_DISABLE_RECIPES = "disableUncraftingRecipes";

    private static final String FIELD_REVERSE_BLACKLIST = "reverseRecipeBlacklist";

    private static final String FIELD_BLACKLISTED_MOD_IDS = "blacklistedUncraftingModIds";

    private static final String FIELD_FLIP_MOD_IDS = "flipUncraftingModIdList";

    private static boolean bridged;

    @Nullable
    private static RecipeType<?> recipeType;

    @Nullable
    private static Method isItemStackAnIngredient;

    @Nullable
    private static Method getCount;

    @Nullable
    private static Class<?> configClass;

    /**
     * 拆解产物的筛选配置，字段含义与暮色森林的 {@code TFConfig} 一一对应
     *
     * <p>{@link #allows(ResourceLocation)} 就是 {@code UncraftingMenu#getRecipesFor} 里那两个连续的
     * {@code if_icmpne}：名单默认是黑名单，所以「进名单」等于「不许拆」，{@code flip} 打开后反过来
     */
    public record Config(boolean allowShapeless,
                         boolean reverseRecipeBlacklist,
                         Set<String> disabledRecipes,
                         boolean flipModIdList,
                         Set<String> blacklistedModIds) {
        public static final Config DEFAULT = new Config(false, false, Set.of(), false, Set.of());

        public boolean allows(ResourceLocation recipeId) {
            boolean recipeListed = this.disabledRecipes.contains(recipeId.toString());
            if (this.reverseRecipeBlacklist != recipeListed) {
                return false;
            }
            boolean modListed = this.blacklistedModIds.contains(recipeId.getNamespace());
            return this.flipModIdList == modListed;
        }
    }

    /** 当前的配置快照。暮色森林没装、或者字段对不上时给 {@link Config#DEFAULT} */
    public static Config config() {
        bridge();
        if (configClass == null) {
            return Config.DEFAULT;
        }
        return new Config(
            readBoolean(FIELD_ALLOW_SHAPELESS, false),
            readBoolean(FIELD_REVERSE_BLACKLIST, false),
            readStrings(FIELD_DISABLE_RECIPES),
            readBoolean(FIELD_FLIP_MOD_IDS, false),
            readStrings(FIELD_BLACKLISTED_MOD_IDS));
    }

    /** 这个配方是不是暮色森林的拆解配方。没装暮色森林时恒为 false */
    public static boolean isUncraftingRecipe(Recipe<?> recipe) {
        bridge();
        return recipeType != null && recipe.getType() == recipeType;
    }

    /** 这个物品能不能进这条拆解配方，等价于 {@code UncraftingRecipe#isItemStackAnIngredient} */
    public static boolean isIngredient(Recipe<?> recipe, ItemStack stack) {
        bridge();
        if (isItemStackAnIngredient == null || !isUncraftingRecipe(recipe)) {
            return false;
        }
        try {
            return (Boolean) isItemStackAnIngredient.invoke(recipe, stack);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** 拆一次吃掉几个，等价于 {@code UncraftingRecipe#getCount}。取不到时按 1 算 */
    public static int count(Recipe<?> recipe) {
        bridge();
        if (getCount == null || !isUncraftingRecipe(recipe)) {
            return 1;
        }
        try {
            return Math.max(1, (Integer) getCount.invoke(recipe));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1;
        }
    }

    /**
     * 一次性把反射句柄找齐，之后每次只读字段
     *
     * <p>配方类型先从注册表拿：拿不到就是没装暮色森林，后面两个方法也不用找了
     */
    private static void bridge() {
        if (bridged) {
            return;
        }
        bridged = true;
        recipeType = BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse(RECIPE_TYPE_ID));
        if (recipeType == null) {
            return;
        }
        try {
            Class<?> type = Class.forName(RECIPE_CLASS);
            isItemStackAnIngredient = type.getMethod("isItemStackAnIngredient", ItemStack.class);
            getCount = type.getMethod("getCount");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // 类在、方法名改了：当作没有拆解配方，退回反查合成配方与熔炼
            isItemStackAnIngredient = null;
            getCount = null;
        }
        try {
            configClass = Class.forName(CONFIG_CLASS);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            configClass = null;
        }
    }

    private static boolean readBoolean(String name, boolean fallback) {
        try {
            Field field = configClass.getField(name);
            return field.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private static Set<String> readStrings(String name) {
        try {
            Field field = configClass.getField(name);
            Object value = field.get(null);
            if (value instanceof List<?> list) {
                Set<String> names = new HashSet<>();
                for (Object entry : list) {
                    if (entry instanceof String text) {
                        names.add(text);
                    }
                }
                return Collections.unmodifiableSet(names);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 配置字段改名或者还没初始化：按空名单算
        }
        return Set.of();
    }

    private TwilightUncrafting() {}
}
