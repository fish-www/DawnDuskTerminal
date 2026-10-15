package com.dawnduskterminal.mixin;

import com.dawnduskterminal.client.ChronoFoliageColors;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps {@code BiomeColors.FOLIAGE_COLOR_RESOLVER} so the chrono dimension's ported TF biomes
 * (jungle = enchanted forest, dark_forest = dark forest centre) get TF's foliage colour.
 *
 * <p>The wrapper keeps the original resolver intact and only changes the result for the two chrono
 * biome ids, so TF's own ASM patch (and every other biome) is left untouched.
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsFoliageMixin {
    @Shadow
    @Final
    @Mutable
    public static ColorResolver FOLIAGE_COLOR_RESOLVER;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void ddt$wrapFoliageResolver(CallbackInfo ci) {
        ColorResolver original = FOLIAGE_COLOR_RESOLVER;
        FOLIAGE_COLOR_RESOLVER = (biome, x, z) ->
            ChronoFoliageColors.resolve(original.getColor(biome, x, z), biome, x, z);
    }
}
