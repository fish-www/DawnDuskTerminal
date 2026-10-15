package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the chrono pillars cave (dawnduskterminal:overworld/caves/pillars &lt; 0) dry.
 *
 * <p>The noise settings already push {@code fluid_level_floodedness} to -1 inside the cave, which
 * suppresses the regular aquifer. That signal never reaches ocean and river flooding: vanilla
 * {@code Aquifer.NoiseBasedAquifer.computeFluid} returns the ocean/river fluid early from its surface
 * sampling loop, long before {@code computeSurfaceLevel} reads the floodedness. So this mixin
 * intercepts {@code computeSubstance} - the single entry point every carved block passes through -
 * and refuses to place any fluid where the floodedness marker ({@code <= -1}) shows the pillars cave,
 * leaving those cells as air. The pillars function itself is left untouched.
 *
 * <p>Returning {@link BlockState} {@code null} is NOT "air": the chunk filler treats null as "leave the
 * default block", i.e. solid stone. Air must be returned explicitly as
 * {@code Blocks.AIR.defaultBlockState()}, otherwise the cave fills back in.
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class PillarsFluidBlockMixin {
    @Shadow
    private DensityFunction fluidLevelFloodednessNoise;

    @Shadow
    private Aquifer.FluidPicker globalFluidPicker;

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void ddt$blockPillarsFluid(DensityFunction.FunctionContext context, double density,
                                       CallbackInfoReturnable<BlockState> cir) {
        if (density > 0.0) {
            return; // solid terrain - vanilla already returns null, nothing to dry out
        }
        if (!ddt$isChrono(context)) {
            return;
        }
        DensityFunction.SinglePointContext single = new DensityFunction.SinglePointContext(
            context.blockX(), context.blockY(), context.blockZ());
        if (this.fluidLevelFloodednessNoise.compute(single) <= -1.0) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Unique
    private boolean ddt$isChrono(DensityFunction.FunctionContext context) {
        Aquifer.FluidStatus status = this.globalFluidPicker.computeFluid(
            context.blockX(), context.blockY(), context.blockZ());
        BlockState fluidType = ((FluidStatusAccessor) (Object) status).ddt$fluidType();
        return fluidType.is(ModBlocks.PORTAL_FLUID.get()) || fluidType.is(ModScorchingTwilight.BLOCK.get());
    }
}
