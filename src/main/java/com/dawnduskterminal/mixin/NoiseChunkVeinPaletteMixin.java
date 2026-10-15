package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.world.ChronoVeinPalette;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps the chunk's finished block rule so the large ore veins stop placing vanilla granite and tuff.
 *
 * <p>Vanilla builds that rule in the {@code NoiseChunk} constructor and folds
 * {@code OreVeinifier.create(...)} into it; {@code OreVeinifier.VeinType} hardcodes
 * {@code GRANITE} / {@code TUFF} as the vein fillers, and no datapack can reach them. Wrapping the
 * composed rule covers them without touching the parts the datapack already handles.
 *
 * <p>The dimension is identified by its noise settings: only the chrono settings use portal_fluid as
 * their default fluid.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkVeinPaletteMixin {
    @Shadow
    @Final
    @Mutable
    private NoiseChunk.BlockStateFiller blockStateRule;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ddt$paletteVeinFillers(int cellCountXZ, RandomState randomState, int blockX, int blockZ,
                                        NoiseSettings noiseSettings, DensityFunctions.BeardifierOrMarker beardifier,
                                        NoiseGeneratorSettings settings, Aquifer.FluidPicker fluidPicker,
                                        Blender blender, CallbackInfo ci) {
        if (!settings.defaultFluid().is(ModBlocks.PORTAL_FLUID.get())) {
            return;
        }
        this.blockStateRule = ChronoVeinPalette.wrap(this.blockStateRule);
    }
}
