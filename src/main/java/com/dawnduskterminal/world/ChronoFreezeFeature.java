package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The chrono palette's replacement for {@code minecraft:freeze_top_layer}.
 *
 * <p>Vanilla's version is code, not data: {@code SnowAndFreezeFeature} writes
 * {@code Blocks.SNOW.defaultBlockState()} and {@code Blocks.ICE.defaultBlockState()} literally, so no
 * amount of datapack work can stop a cold biome from growing vanilla snow. Our biome copies point
 * their feature lists here instead.
 *
 * <p>One deliberate difference: vanilla asks {@code Biome#shouldFreeze}, which tests the water fluid
 * tag. This dimension's water is portal_fluid and is not in that tag, so the check is made against our
 * own fluid as well - otherwise the palette's ice would simply never appear.
 */
public class ChronoFreezeFeature extends Feature<NoneFeatureConfiguration> {
    public ChronoFreezeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                top.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                below.set(top).move(Direction.DOWN, 1);
                Biome biome = level.getBiome(top).value();

                // Vanilla's shouldFreeze has two parts: a temperature/precipitation gate, then a test
                // for the water fluid tag. The gate is applied here for our fluid instead of being
                // bypassed - without it every fluid surface in every biome, warm ones included,
                // freezes over.
                boolean freezes = biome.shouldFreeze(level, below, false)
                    || (biome.coldEnoughToSnow(below)
                        && level.getBlockState(below).is(ModBlocks.PORTAL_FLUID.get()));
                if (freezes) {
                    level.setBlock(below, ModTerrainBlocks.FROZEN_CANOPY.get().defaultBlockState(), 2);
                }

                if (biome.shouldSnow(level, top)) {
                    level.setBlock(top, ModTerrainBlocks.CANOPY_FROST.get().defaultBlockState(), 2);
                    BlockState under = level.getBlockState(below);
                    if (under.hasProperty(SnowyDirtBlock.SNOWY)) {
                        level.setBlock(below, under.setValue(SnowyDirtBlock.SNOWY, Boolean.TRUE), 2);
                    }
                }
            }
        }
        return true;
    }
}
