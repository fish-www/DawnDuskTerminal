package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fluid contact reactions, registered through {@code FluidInteractionRegistry} (the same hook as
 * vanilla water + lava -> cobblestone). The mod fluid is the source: it is replaced by the result
 * block on contact, and the neighbour fluid is left in place, so one contact produces one block.
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class FluidReactionTests {
    private static final BlockPos PORTAL = new BlockPos(2, 1, 2);

    @GameTest(template = "ddt_empty")
    public static void portalFluidWithWater(GameTestHelper helper) {
        helper.setBlock(PORTAL, ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(PORTAL.east(), Blocks.WATER);
        helper.assertBlockPresent(ModTerrainBlocks.FRAGILE_CANOPY.get(), PORTAL);
        helper.assertBlockPresent(Blocks.WATER, PORTAL.east());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portalFluidWithLava(GameTestHelper helper) {
        helper.setBlock(PORTAL, ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(PORTAL.east(), Blocks.LAVA);
        helper.assertBlockPresent(ModTerrainBlocks.CHAOS_STONE.get(), PORTAL);
        helper.assertBlockPresent(Blocks.LAVA, PORTAL.east());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portalFluidWithScorchingTwilight(GameTestHelper helper) {
        helper.setBlock(PORTAL, ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(PORTAL.east(), ModScorchingTwilight.BLOCK.get());
        helper.assertBlockPresent(ModTerrainBlocks.CANOPY_STONE.get(), PORTAL);
        helper.assertBlockPresent(ModScorchingTwilight.BLOCK.get(), PORTAL.east());
        helper.succeed();
    }

    /** enhtu_fluid + water -> aurora block (blazing_block when Twilight Forest is absent). */
    @GameTest(template = "ddt_empty")
    public static void scorchingTwilightWithWater(GameTestHelper helper) {
        helper.setBlock(PORTAL, ModScorchingTwilight.BLOCK.get());
        helper.setBlock(PORTAL.east(), Blocks.WATER);
        helper.assertBlockPresent(ModTerrainBlocks.BLAZING_BLOCK.get(), PORTAL);
        helper.assertBlockPresent(Blocks.WATER, PORTAL.east());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void scorchingTwilightWithLava(GameTestHelper helper) {
        helper.setBlock(PORTAL, ModScorchingTwilight.BLOCK.get());
        helper.setBlock(PORTAL.east(), Blocks.LAVA);
        helper.assertBlockPresent(ModTerrainBlocks.BLAZING_BLOCK.get(), PORTAL);
        helper.assertBlockPresent(Blocks.LAVA, PORTAL.east());
        helper.succeed();
    }

    /** Flowing water reacts exactly like a water source (source vs flowing is not distinguished). */
    @GameTest(template = "ddt_empty")
    public static void portalFluidWithFlowingWater(GameTestHelper helper) {
        helper.setBlock(PORTAL, ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(PORTAL.east(), Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 5));
        helper.assertBlockPresent(ModTerrainBlocks.FRAGILE_CANOPY.get(), PORTAL);
        helper.succeed();
    }

    /** A flowing portal fluid block triggers reactions just like a source block. */
    @GameTest(template = "ddt_empty")
    public static void flowingPortalFluidWithWater(GameTestHelper helper) {
        helper.setBlock(PORTAL, ModBlocks.PORTAL_FLUID.get().defaultBlockState().setValue(LiquidBlock.LEVEL, 5));
        helper.setBlock(PORTAL.east(), Blocks.WATER);
        helper.assertBlockPresent(ModTerrainBlocks.FRAGILE_CANOPY.get(), PORTAL);
        helper.succeed();
    }
}
