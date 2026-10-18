package com.dawnduskterminal.gametest;

import com.dawnduskterminal.world.SkygateBranchTrunkPlacer;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.CherryFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.treedecorators.LeaveVineDecorator;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 天门松的形状约束：二十到三十格高、分枝铺在树干整段高度上、叶团是压扁的原版樱花叶团、允许挂藤
 *
 * <p>树是浮空种的，跟其它测试结构在竖直方向上错开，不会互相干扰
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class SkygatePineTests {
    private static final ResourceLocation SKYGATE_PINE =
        ResourceLocation.fromNamespaceAndPath("dawnduskterminal", "tree/skygate_pine");
    private static final int PLACEMENT_LIFT = 30;

    private static ConfiguredFeature<?, ?> feature(GameTestHelper helper) {
        Registry<ConfiguredFeature<?, ?>> registry = helper.getLevel().getServer().registryAccess()
            .registryOrThrow(Registries.CONFIGURED_FEATURE);
        ConfiguredFeature<?, ?> feature = registry.get(SKYGATE_PINE);
        helper.assertTrue(feature != null, "configured feature " + SKYGATE_PINE + " missing");
        helper.assertTrue(feature.config() instanceof TreeConfiguration, "skygate_pine must be a minecraft:tree feature");
        return feature;
    }

    /** JSON 必须真的解到自定义树干与压扁的樱花叶团，并带上挂藤装饰器 */
    @GameTest(template = "ddt_empty")
    public static void treeConfigUsesTheSkygateShapes(GameTestHelper helper) {
        TreeConfiguration config = (TreeConfiguration) feature(helper).config();
        helper.assertTrue(config.trunkPlacer instanceof SkygateBranchTrunkPlacer,
            "the trunk placer must be dawnduskterminal:skygate_branching, got " + config.trunkPlacer);
        helper.assertTrue(config.foliagePlacer instanceof CherryFoliagePlacer,
            "foliage must be shaped by minecraft:cherry_foliage_placer, got " + config.foliagePlacer);
        boolean hasVines = config.decorators.stream().anyMatch(decorator -> decorator instanceof LeaveVineDecorator);
        helper.assertTrue(hasVines, "skygate_pine must be allowed to grow vines, decorators were " + config.decorators);
        helper.succeed();
    }

    /** 种一棵，量树高与分枝高度分布 */
    @GameTest(template = "ddt_empty")
    public static void treeGrowsTwentyToThirtyWithBranchesAlongTheTrunk(GameTestHelper helper) {
        ConfiguredFeature<?, ?> feature = feature(helper);
        TreeConfiguration config = (TreeConfiguration) feature.config();
        BlockState log = config.trunkProvider.getState(RandomSource.create(0L), BlockPos.ZERO);

        BlockPos trunkBase = helper.absolutePos(BlockPos.ZERO).above(PLACEMENT_LIFT);
        helper.getLevel().setBlock(trunkBase.below(), Blocks.DIRT.defaultBlockState(), 3);
        boolean placed = feature.place(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
            RandomSource.create(0x5C6A7EL), trunkBase);
        helper.assertTrue(placed, "the skygate pine failed to place in open air");

        int height = 0;
        while (height < 80 && helper.getLevel().getBlockState(trunkBase.above(height)).is(log.getBlock())) {
            height++;
        }

        helper.assertTrue(height >= 20 && height <= 30, "skygate pine must be 20~30 blocks tall, got " + height);

        int trunkHeight = height;
        Set<Integer> branchHeights = new HashSet<>();
        for (int dy = 0; dy <= trunkHeight; dy++) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }

                    if (helper.getLevel().getBlockState(trunkBase.offset(dx, dy, dz)).is(log.getBlock())) {
                        branchHeights.add(dy);
                    }
                }
            }
        }

        helper.assertTrue(branchHeights.size() >= 4,
            "branches must reach at least four different trunk heights, got " + branchHeights);
        helper.assertTrue(branchHeights.stream().anyMatch(dy -> dy <= trunkHeight / 2),
            "branches must reach the lower half of the trunk, got " + branchHeights);
        helper.assertTrue(branchHeights.stream().anyMatch(dy -> dy >= trunkHeight * 2 / 3),
            "branches must reach the upper third of the trunk, got " + branchHeights);
        helper.succeed();
    }
}
