package com.dawnduskterminal.mixin;

import com.dawnduskterminal.structure.StructureExclusions;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Structure.class)
public abstract class StructureExclusionMixin {
    /**
     * The chrono dimension no longer uses a bespoke {@code BiomeSource} to recognise itself, so it is
     * identified by the noise settings its generator was built from. That is stable across the
     * chunk-generator lifecycle and already available on the generation context.
     */
    @Unique
    private static final ResourceKey<NoiseGeneratorSettings> DDT$CHRONO_SETTINGS = ResourceKey.create(
        Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("dawnduskterminal", "chrono"));

    @Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void ddt$suppressExcludedLandmarks(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (!ddt$isChrono(context)) {
            return;
        }
        Registry<Structure> registry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceLocation id = registry.getKey((Structure) (Object) this);
        if (id == null || !StructureExclusions.isExcluded(id)) {
            return;
        }
        StructureExclusions.logSuppressed(id);
        cir.setReturnValue(Optional.empty());
    }

    @Unique
    private static boolean ddt$isChrono(Structure.GenerationContext context) {
        if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            return false;
        }
        return generator.generatorSettings().is(DDT$CHRONO_SETTINGS);
    }
}
