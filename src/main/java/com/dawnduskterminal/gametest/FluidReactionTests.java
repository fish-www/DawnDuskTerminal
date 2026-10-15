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
 * 流体接触反应，通过 {@code FluidInteractionRegistry} 注册（原版水加岩浆生成圆石走的就是这个钩子）
 *
 * 模组流体是发动方：接触时它自己被替换成产物，邻居流体不动，所以一次接触只产出一个方块
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

    /** enthu_fluid 加水生成极光方块，取不到暮色森林时退化为炽热方块 */
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

    /** 流动的水与水源反应完全一致（不区分源与流动） */
    @GameTest(template = "ddt_empty")
    public static void portalFluidWithFlowingWater(GameTestHelper helper) {
        helper.setBlock(PORTAL, ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(PORTAL.east(), Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 5));
        helper.assertBlockPresent(ModTerrainBlocks.FRAGILE_CANOPY.get(), PORTAL);
        helper.succeed();
    }

    /** 流动的传送门流体和源方块一样会触发反应 */
    @GameTest(template = "ddt_empty")
    public static void flowingPortalFluidWithWater(GameTestHelper helper) {
        helper.setBlock(PORTAL, ModBlocks.PORTAL_FLUID.get().defaultBlockState().setValue(LiquidBlock.LEVEL, 5));
        helper.setBlock(PORTAL.east(), Blocks.WATER);
        helper.assertBlockPresent(ModTerrainBlocks.FRAGILE_CANOPY.get(), PORTAL);
        helper.succeed();
    }
}
