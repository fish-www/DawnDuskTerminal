package com.dawnduskterminal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The chrono dimension re-enables the vanilla mineshaft, but its default placement spans up to sea
 * level. Here the NORMAL (non-mesa) mineshaft is confined to y -64..-10 by replacing the vanilla
 * {@code moveBelowSeaLevel} call with {@code moveInsideHeights} for the chrono dimension only.
 */
@Mixin(MineshaftStructure.class)
public abstract class MineshaftHeightMixin {
    @Unique
    private static final ResourceKey<NoiseGeneratorSettings> DDT$CHRONO_SETTINGS = ResourceKey.create(
        Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("dawnduskterminal", "chrono"));

    @Unique
    private static boolean ddt$isChrono(Structure.GenerationContext context) {
        if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            return false;
        }
        return generator.generatorSettings().is(DDT$CHRONO_SETTINGS);
    }

    @Redirect(
        method = "generatePiecesAndAdjust(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;)I",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;moveBelowSeaLevel(IILnet/minecraft/util/RandomSource;I)I"
        )
    )
    @SuppressWarnings("deprecation")
    private int ddt$restrictMineshaftY(StructurePiecesBuilder builder, int seaLevel, int minY, RandomSource random, int height,
                                       @Local(argsOnly = true) Structure.GenerationContext context) {
        if (ddt$isChrono(context)) {
            int oldMaxY = builder.getBoundingBox().maxY();
            builder.moveInsideHeights(random, -64, -10);
            return builder.getBoundingBox().maxY() - oldMaxY;
        }
        return builder.moveBelowSeaLevel(seaLevel, minY, random, height);
    }
}
