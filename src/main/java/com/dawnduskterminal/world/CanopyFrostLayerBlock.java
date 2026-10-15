package com.dawnduskterminal.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A snow layer that never melts.
 *
 * <p>Vanilla {@code SnowLayerBlock.randomTick} is nothing but a melt: when the block light at the layer's own
 * position is above 11 it drops its loot and removes itself. That is the entire body of the method
 * ({@code SnowLayerBlock.java:117}):
 *
 * <pre>
 * if (level.getBrightness(LightLayer.BLOCK, pos) > 11) {
 *     dropResources(state, level, pos);
 *     level.removeBlock(pos, false);
 * }
 * </pre>
 *
 * <p>Canopy frost must not do that. The chrono dimension is covered in light level 15 blocks -
 * {@code portal_fluid} in every ocean, aquifer and spring is light 15, and so is {@code blazing_block} - so
 * any layer next to one of them sits in block light above 11 and would evaporate piece by piece.
 *
 * <p>Only the melt is dropped. Everything else about a snow layer (the {@code LAYERS} state, the shapes, the
 * placement rules, the block and item models) is inherited untouched, and the properties stay a verbatim copy
 * of {@code Blocks.SNOW} - including {@code randomTicks()}, so if this block ever gains a random behaviour it
 * has the hook waiting. Same split as everywhere else in this mod: copy vanilla's properties, cut vanilla's
 * behaviour in the class.
 *
 * <p>The other two frost blocks need no help here: {@code canopy_frost_block} is a plain
 * {@link net.minecraft.world.level.block.Block} (vanilla {@code SnowBlock}'s melt is an override of the same
 * {@code randomTick}, it never had one), and {@code frozen_canopy} is a
 * {@link net.minecraft.world.level.block.HalfTransparentBlock}, not an {@code IceBlock} - ice's melt lives in
 * {@code IceBlock}. See {@code gametest/CanopyFrostMeltTests} for the pin.
 */
public class CanopyFrostLayerBlock extends SnowLayerBlock {
    public CanopyFrostLayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Intentionally empty. The inherited body would delete this layer whenever the position is lit by
        // more than 11 block light; the layer is permanent scenery here.
    }
}
