package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.ChronoDimensionProbe;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 晨昏线维度放行了原版废弃矿井，但它的默认落点能一直顶到海平面
 *
 * 这里只对晨昏线维度把原版 {@code moveBelowSeaLevel} 换成 {@code moveInsideHeights}，把普通（非平顶
 * 山）矿井压在 y -64 到 -10
 */
@Mixin(MineshaftStructure.class)
public abstract class MineshaftHeightMixin {
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
        if (ChronoDimensionProbe.isChrono(context)) {
            int oldMaxY = builder.getBoundingBox().maxY();
            builder.moveInsideHeights(random, -64, -10);
            return builder.getBoundingBox().maxY() - oldMaxY;
        }
        return builder.moveBelowSeaLevel(seaLevel, minY, random, height);
    }
}
