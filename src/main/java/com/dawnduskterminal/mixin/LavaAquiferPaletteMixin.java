package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The deep "lava aquifer" is a second hardcoded {@code Blocks.LAVA} no datapack can reach.
 *
 * <p>Vanilla {@code Aquifer.NoiseBasedAquifer.computeFluidType} upgrades aquifer cells whose fluid
 * surface level is at or below {@code -10} to {@code Blocks.LAVA} whenever the {@code aquifer_lava}
 * noise exceeds 0.3. {@link LavaBandPaletteMixin} only replaces the {@code y < -54} band produced
 * by {@code NoiseBasedChunkGenerator.createFluidPicker}; this second spot would still emit vanilla
 * {@code minecraft:lava} inside the chrono dimension, so it is swapped to enthu_fluid here.
 *
 * <p>The dimension is identified by the fluid type that reached this method: in the chrono dimension
 * the global fluid picker (already patched by {@link LavaBandPaletteMixin}) only ever returns
 * portal_fluid or enthu_fluid, never vanilla water/lava. Any other fluid falls through to vanilla
 * untouched. The vanilla guard {@code status.fluidType != Blocks.LAVA} becomes "not already
 * enthu_fluid", so the deep band - which already is enthu_fluid - is not re-upgraded.
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class LavaAquiferPaletteMixin {
    @Shadow
    private DensityFunction lavaNoise;

    @Inject(method = "computeFluidType", at = @At("HEAD"), cancellable = true)
    private void ddt$lavaAquiferPalette(int x, int y, int z, Aquifer.FluidStatus status, int fluidLevel,
                                        CallbackInfoReturnable<BlockState> cir) {
        BlockState globalType = ((FluidStatusAccessor) (Object) status).ddt$fluidType();
        if (!globalType.is(ModBlocks.PORTAL_FLUID.get()) && !globalType.is(ModScorchingTwilight.BLOCK.get())) {
            return; // not the chrono dimension -> vanilla behaviour
        }
        BlockState blockstate = globalType;
        if (fluidLevel <= -10 && fluidLevel != DimensionType.WAY_BELOW_MIN_Y
                && !globalType.is(ModScorchingTwilight.BLOCK.get())) {
            double d0 = this.lavaNoise.compute(new DensityFunction.SinglePointContext(
                Mth.floorDiv(x, 64), Mth.floorDiv(y, 40), Mth.floorDiv(z, 64)));
            if (Math.abs(d0) > 0.3) {
                blockstate = ModScorchingTwilight.BLOCK.get().defaultBlockState();
            }
        }
        cir.setReturnValue(blockstate);
    }
}
