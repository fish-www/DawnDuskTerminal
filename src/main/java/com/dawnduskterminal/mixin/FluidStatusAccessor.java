package com.dawnduskterminal.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the fluid type stored in an {@link Aquifer.FluidStatus}.
 *
 * <p>{@code fluidType} is package-private in vanilla, so the lava-aquifer palette mixin cannot read
 * it from the {@code com.dawnduskterminal.mixin} package. This accessor lifts it out so
 * {@link LavaAquiferPaletteMixin} can tell whether an aquifer cell reached the chrono dimension -
 * whose global fluid picker only ever returns portal_fluid / enthu_fluid.
 */
@Mixin(Aquifer.FluidStatus.class)
public interface FluidStatusAccessor {
    @Accessor("fluidType")
    BlockState ddt$fluidType();
}
