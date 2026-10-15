package com.dawnduskterminal.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 永不融化的雪层
 *
 * <p>原版 {@code SnowLayerBlock.randomTick} 的全部内容就是一次融化：当本格方块光照高于 11 时掉落战利
 * 品并移除自己。整个方法体就这么长（{@code SnowLayerBlock.java:117}）：
 *
 * <pre>
 * if (level.getBrightness(LightLayer.BLOCK, pos) > 11) {
 *     dropResources(state, level, pos);
 *     level.removeBlock(pos, false);
 * }
 * </pre>
 *
 * <p>苍穹霜不能这样。晨昏线维度到处是光照 15 的方块——每个海洋 含水层 泉里的 {@code portal_fluid} 都是
 * 光照 15，{@code blazing_block} 也是——所以任何紧挨着它们的霜层都会处在高于 11 的方块光照下，一层层
 * 蒸发掉
 *
 * <p>只丢掉融化。雪层其余的一切（{@code LAYERS} 状态、形状、放置规则、方块与物品模型）都原样继承，
 * 属性保持 {@code Blocks.SNOW} 的逐字拷贝——包括 {@code randomTicks()}，这样以后真要给它加随机行为，
 * 钩子已经在那了。与本模组其它地方一样的分工：照拄原版属性，在类里砍掉原版行为
 *
 * <p>另外两种霜方块不需要在这里动手：{@code canopy_frost_block} 是普通
 * {@link net.minecraft.world.level.block.Block}（原版 {@code SnowBlock} 的融化是同一个 {@code randomTick}
 * 的覆写，它本来就没有），而 {@code frozen_canopy} 是
 * {@link net.minecraft.world.level.block.HalfTransparentBlock}，不是 {@code IceBlock}——冰的融化在
 * {@code IceBlock} 里。这条约束由 {@code gametest/CanopyFrostMeltTests} 钉住
 */
public class CanopyFrostLayerBlock extends SnowLayerBlock {
    public CanopyFrostLayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 有意留空。继承来的方法体只要本格方块光照高于 11 就会删掉这层霜；这里它是永久布景
    }
}
