package com.dawnduskterminal.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link Aquifer.FluidStatus} 里存的流体类型
 *
 * <p>{@code fluidType} 在原版是包级私有，熔岩含水层调色板 mixin 在
 * {@code com.dawnduskterminal.mixin} 包里读不到它。这个 accessor 把它取出来，让
 * {@link LavaAquiferPaletteMixin} 能判断某个含水层格子是否落到了晨昏线维度：该维度的全局流体选择
 * 器只会返回 portal_fluid 或 enthu_fluid
 */
@Mixin(Aquifer.FluidStatus.class)
public interface FluidStatusAccessor {
    @Accessor("fluidType")
    BlockState ddt$fluidType();
}
