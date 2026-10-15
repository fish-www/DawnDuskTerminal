package com.dawnduskterminal.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.stream.Stream;

/**
 * Matches any potion (drinkable, splash or lingering) whose combined effects include
 * {@link MobEffects#POISON}. This lets the mixed-carminite recipe accept any poison potion rather
 * than only the exact base {@code minecraft:poison} variant.
 */
public final class PoisonPotionIngredient implements ICustomIngredient {
    public static final PoisonPotionIngredient INSTANCE = new PoisonPotionIngredient();
    public static final MapCodec<PoisonPotionIngredient> CODEC = MapCodec.unit(INSTANCE);
    public static final IngredientType<PoisonPotionIngredient> TYPE = new IngredientType<>(CODEC);

    private PoisonPotionIngredient() {}

    @Override
    public boolean test(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (var effect : contents.getAllEffects()) {
            if (effect.getEffect().value() == MobEffects.POISON.value()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(
            PotionContents.createItemStack(Items.POTION, Potions.POISON),
            PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON),
            PotionContents.createItemStack(Items.LINGERING_POTION, Potions.POISON));
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PoisonPotionIngredient;
    }

    @Override
    public int hashCode() {
        return PoisonPotionIngredient.class.hashCode();
    }
}
