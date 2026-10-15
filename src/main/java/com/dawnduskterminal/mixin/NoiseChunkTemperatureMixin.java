package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.world.ChronoTemperature;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shifts the chrono dimension's temperature noise by the terminator offset so biome selection tracks
 * the day/night side of the chrono line.
 *
 * <p>The dimension is identified the same way the vein-palette mixin does: only the chrono noise
 * settings use portal_fluid as their default fluid. The flag is captured in the constructor because
 * {@code cachedClimateSampler} - where the temperature is handed to the climate sampler - does not
 * receive the noise settings.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkTemperatureMixin {
    @Unique
    private boolean ddt$isChrono;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ddt$flagChrono(int cellCountXZ, RandomState randomState, int blockX, int blockZ,
                                NoiseSettings noiseSettings, DensityFunctions.BeardifierOrMarker beardifier,
                                NoiseGeneratorSettings settings, Aquifer.FluidPicker fluidPicker,
                                Blender blender, CallbackInfo ci) {
        this.ddt$isChrono = settings.defaultFluid().is(ModBlocks.PORTAL_FLUID.get());
    }

    @Redirect(
        method = "cachedClimateSampler",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;temperature()Lnet/minecraft/world/level/levelgen/DensityFunction;"
        )
    )
    private DensityFunction ddt$offsetTemperature(NoiseRouter router) {
        return this.ddt$isChrono ? ChronoTemperature.wrap(router.temperature()) : router.temperature();
    }
}
