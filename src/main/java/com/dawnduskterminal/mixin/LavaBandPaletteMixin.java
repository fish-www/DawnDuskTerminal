package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The one piece of the chrono palette no datapack can reach.
 *
 * <p>{@code NoiseBasedChunkGenerator.createFluidPicker} hardcodes {@code Blocks.LAVA} for everything
 * below {@code min(-54, seaLevel)}; that band is not derived from {@code default_fluid}, so the noise
 * settings cannot change it. Everything above it already comes from {@code settings.defaultFluid()},
 * which the chrono settings set to portal_fluid - so the palette is complete there.
 *
 * <p>The settings identify the dimension here (their {@code default_fluid} being portal_fluid), as
 * there is no level in scope. The handler takes over the whole method rather than nudging one
 * argument, because an argument injector cannot see the enclosing method's parameters; if vanilla
 * ever changes this picker the {@code HEAD} injection still applies and this copy is what runs, so it
 * is the one place to keep an eye on when updating Minecraft.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class LavaBandPaletteMixin {
    @Inject(method = "createFluidPicker", at = @At("HEAD"), cancellable = true)
    private static void ddt$paletteLavaBand(NoiseGeneratorSettings settings,
                                            CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
        if (!settings.defaultFluid().is(ModBlocks.PORTAL_FLUID.get())) {
            return;
        }
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54,
            ModScorchingTwilight.BLOCK.get().defaultBlockState());
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus fluid = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        cir.setReturnValue((x, y, z) -> y < Math.min(-54, seaLevel) ? lava : fluid);
    }
}
