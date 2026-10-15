package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins the one vanilla snow behaviour canopy frost is not allowed to have: melting itself away when the block
 * light at its position is above 11 ({@code SnowLayerBlock.randomTick}).
 *
 * <p>The test runs the same call the server makes for a randomly picked block
 * ({@link GameTestHelper#randomTick}) on both a canopy frost layer and a vanilla snow layer standing in the
 * same block light. The vanilla one is the control: if it does not melt either, the light never arrived and the
 * test would prove nothing, so that direction is asserted too.
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class CanopyFrostMeltTests {
    /** The vanilla melt condition is "block light strictly above 11". */
    private static final int MELTING_LIGHT = 11;

    private static final BlockPos FROST = new BlockPos(2, 2, 2);
    private static final BlockPos VANILLA_SNOW = new BlockPos(4, 2, 2);
    /** blazing_block is light level 15, one block below each layer. */
    private static final BlockPos FROST_LIGHT = new BlockPos(2, 1, 2);
    private static final BlockPos VANILLA_LIGHT = new BlockPos(4, 1, 2);

    @GameTest(template = "ddt_empty")
    public static void canopyFrostSurvivesTheLightThatMeltsVanillaSnow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // setBlockAndUpdate (= flag 3), because the light engine only hears about a light source that is placed
        // with neighbour/light updates; GameTestHelper.setBlock uses flag 2 and would leave the light at 0.
        level.setBlockAndUpdate(helper.absolutePos(FROST_LIGHT),
            ModTerrainBlocks.BLAZING_BLOCK.get().defaultBlockState());
        level.setBlockAndUpdate(helper.absolutePos(VANILLA_LIGHT),
            ModTerrainBlocks.BLAZING_BLOCK.get().defaultBlockState());
        helper.setBlock(FROST, ModTerrainBlocks.CANOPY_FROST.get());
        helper.setBlock(VANILLA_SNOW, Blocks.SNOW);

        // The light engine runs on the server tick, so the block light is only there a few ticks later.
        helper.runAfterDelay(10, () -> {
            BlockPos frostPos = helper.absolutePos(FROST);
            BlockPos snowPos = helper.absolutePos(VANILLA_SNOW);
            int frostLight = level.getBrightness(LightLayer.BLOCK, frostPos);
            int snowLight = level.getBrightness(LightLayer.BLOCK, snowPos);
            helper.assertTrue(frostLight > MELTING_LIGHT && snowLight > MELTING_LIGHT,
                "precondition: both layers must sit in block light > " + MELTING_LIGHT + ", got "
                    + frostLight + " and " + snowLight);

            helper.randomTick(VANILLA_SNOW);
            helper.assertFalse(level.getBlockState(snowPos).is(Blocks.SNOW),
                "control: vanilla snow must melt in this light, otherwise the assertion below proves nothing");

            helper.randomTick(FROST);
            helper.assertTrue(level.getBlockState(frostPos).is(ModTerrainBlocks.CANOPY_FROST.get()),
                "canopy frost must not melt in bright light");
            helper.succeed();
        });
    }
}
