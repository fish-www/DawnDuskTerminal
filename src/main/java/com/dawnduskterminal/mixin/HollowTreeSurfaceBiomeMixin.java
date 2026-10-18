package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.ChronoDimensionProbe;
import com.dawnduskterminal.world.ChronoTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 让暮色森林的树洞结构按「区块实际落盘的群系」判定，而不是按没有温度偏移的那个采样器
 *
 * <p>{@code NoiseChunkTemperatureMixin} 只在 {@code NoiseChunk.cachedClimateSampler} 里给温度加晨昏线偏移，
 * 而区块的群系正是用那个采样器写进去的（{@code NoiseBasedChunkGenerator.createBiomes}）。结构判定读的是
 * {@code context.randomState().sampler()}，它由未包装的温度构建，于是两边给出不同群系：结构看到草原，
 * 地表实际是红沙漠或雪原，巨树就长在沙漠中间
 *
 * <p>把包装整体搬到 {@code RandomState} 可以根治，但那样 {@code NoiseChunk} 会为包装器再建一层噪声插值，
 * 实测单刻慢到触发看门狗，所以退一步：只在结构上复算一次
 *
 * <p>注入必须写 {@code cancellable = true}，否则 {@code setReturnValue} 会在世界生成线程里抛
 * {@code CancellationException}
 */
@Mixin(Structure.class)
public abstract class HollowTreeSurfaceBiomeMixin {
    private static final ResourceLocation HOLLOW_TREE = ResourceLocation.parse("twilightforest:hollow_tree");

    /** 世界生成时的气候采样器：在 {@code RandomState} 那份之上把温度包成带晨昏线偏移的版本 */
    private static Climate.Sampler shifted(Climate.Sampler base) {
        return new Climate.Sampler(
            ChronoTemperature.wrap(base.temperature()),
            base.humidity(),
            base.continentalness(),
            base.erosion(),
            base.depth(),
            base.weirdness(),
            base.spawnTarget());
    }

    @Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
    private void ddt$recheckHollowTreeBiome(Structure.GenerationContext context,
                                            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (!ChronoDimensionProbe.isChrono(context)) {
            return;
        }
        Optional<Structure.GenerationStub> stub = cir.getReturnValue();
        if (stub.isEmpty()) {
            return;
        }
        Structure self = (Structure) (Object) this;
        Registry<Structure> registry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        if (!HOLLOW_TREE.equals(registry.getKey(self))) {
            return;
        }
        BlockPos pos = stub.get().position();
        Holder<Biome> placed = context.biomeSource().getNoiseBiome(
            QuartPos.fromBlock(pos.getX()),
            QuartPos.fromBlock(pos.getY()),
            QuartPos.fromBlock(pos.getZ()),
            shifted(context.randomState().sampler()));
        if (!self.biomes().contains(placed)) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
