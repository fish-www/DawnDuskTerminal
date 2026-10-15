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
 * 给晨昏线维度的温度噪声加上晨昏线偏移，让群系选择跟着线的昼夜侧走
 *
 * <p>识别维度的方式与矿脉调色板 mixin 相同：只有晨昏线的噪声设置把默认流体设为 portal_fluid。标记
 * 在构造函数里捕获，因为 {@code cachedClimateSampler}（把温度交给气候采样器的地方）拿不到噪声设置
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
