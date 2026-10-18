package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModTrunkPlacers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/**
 * 天门松的树干：一根直筒树干，再沿树干整段高度撒分枝。
 *
 * <p>原版 {@code cherry_trunk_placer} 最多只长三根枝、而且两根枝贴着树顶，
 * 暮色森林的 {@code branching_trunk_placer} 虽然能长 4 根以上，但分枝起点是
 * 「树顶下固定距离」逐格往上叠的连排，覆盖不到树干中下部。参考原型文竹是分层平铺的羽片，
 * 所以这里自己算高度：分枝在 {@code branch_band} 给出的高度带里按层均匀撒开，
 * 每根枝带一个顶点，交给 {@code foliage_placer} 长一团叶。
 *
 * <p>枝的方向是任意角度：先按方位角算出圆周上的落点，再走单格轴向的斜线（每格只动 x 或 z 一个轴），
 * 所以 45 度、30 度这类斜枝也能长出来，不会全是正交的十字。叶团的水平尺寸靠
 * {@code radius_offsets.branch} 逐枝抖动，树顶那团单独用 {@code radius_offsets.crown} 放大。
 */
public class SkygateBranchTrunkPlacer extends TrunkPlacer {
    /** 分枝高度带占树干高度的比例：从 {@code min} 到 {@code max} */
    public record BranchBand(float min, float max) {
        public static final Codec<BranchBand> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(0.0F, 1.0F).fieldOf("min").forGetter(BranchBand::min),
            Codec.floatRange(0.0F, 1.0F).fieldOf("max").forGetter(BranchBand::max)
        ).apply(instance, BranchBand::new));
    }

    /** 叶团水平尺寸偏移：枝顶逐枝抖动用 {@code branch}，树干顶那团封顶用 {@code crown} */
    public record RadiusOffsets(IntProvider branch, int crown) {
        public static final Codec<RadiusOffsets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            IntProvider.codec(-4, 4).fieldOf("branch").forGetter(RadiusOffsets::branch),
            Codec.intRange(-4, 4).fieldOf("crown").forGetter(RadiusOffsets::crown)
        ).apply(instance, RadiusOffsets::new));
    }

    public static final MapCodec<SkygateBranchTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance)
            .and(
                instance.group(
                    IntProvider.codec(1, 16).fieldOf("branch_count").forGetter(placer -> placer.branchCount),
                    IntProvider.codec(1, 16).fieldOf("branch_length").forGetter(placer -> placer.branchLength),
                    BranchBand.CODEC.fieldOf("branch_band").forGetter(placer -> placer.branchBand),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("branch_rise_chance").forGetter(placer -> placer.branchRiseChance),
                    RadiusOffsets.CODEC.fieldOf("radius_offsets").forGetter(placer -> placer.radiusOffsets)
                )
            )
            .apply(instance, SkygateBranchTrunkPlacer::new)
    );
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final IntProvider branchCount;
    private final IntProvider branchLength;
    private final BranchBand branchBand;
    private final float branchRiseChance;
    private final RadiusOffsets radiusOffsets;

    public SkygateBranchTrunkPlacer(
        int baseHeight,
        int heightRandA,
        int heightRandB,
        IntProvider branchCount,
        IntProvider branchLength,
        BranchBand branchBand,
        float branchRiseChance,
        RadiusOffsets radiusOffsets
    ) {
        super(baseHeight, heightRandA, heightRandB);
        this.branchCount = branchCount;
        this.branchLength = branchLength;
        this.branchBand = branchBand;
        this.branchRiseChance = branchRiseChance;
        this.radiusOffsets = radiusOffsets;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return ModTrunkPlacers.SKYGATE_BRANCHING.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> setter,
        RandomSource random,
        int height,
        BlockPos origin,
        TreeConfiguration config
    ) {
        setDirtAt(level, setter, random, origin.below(), config);

        for (int y = 0; y < height; y++) {
            placeLog(level, setter, random, origin.above(y), config);
        }

        List<FoliagePlacer.FoliageAttachment> attachments = new ArrayList<>();
        // 树冠：树干正上方补一团，比枝顶那些更大，用来封顶
        attachments.add(new FoliagePlacer.FoliageAttachment(origin.above(height), this.radiusOffsets.crown(), false));

        int topLimit = Math.max(2, height - 2);
        int lowest = Mth.clamp(Math.round(height * this.branchBand.min()), 2, topLimit);
        int highest = Mth.clamp(Math.round(height * this.branchBand.max()), lowest, topLimit);
        int highestLeafY = origin.getY() + height - 1;
        int count = this.branchCount.sample(random);
        float baseAngle = random.nextFloat() * TWO_PI;
        int previousY = lowest - 2;

        for (int i = 0; i < count; i++) {
            // 按层均分再抖动，保证分枝铺满整段高度而不是挤成一团
            float t = count > 1 ? (i + (random.nextFloat() - 0.5F) * 0.4F) / (float) (count - 1) : 0.5F;
            int y = lowest + Math.round((highest - lowest) * t);
            if (y < previousY + 2) {
                y = previousY + 2;
            }

            y = Mth.clamp(y, 2, topLimit);
            previousY = y;

            // 方位角均分再抖动：落点取在圆周上，斜着长出来的枝也就有了各种角度
            float angle = baseAngle + TWO_PI * (i + random.nextFloat() * 0.6F) / count;
            int length = this.branchLength.sample(random);
            int reachX = (int) Math.round(length * Math.cos(angle));
            int reachZ = (int) Math.round(length * Math.sin(angle));
            if (reachX == 0 && reachZ == 0) {
                reachX = length;
            }

            this.growBranch(level, setter, random, config, attachments, origin.above(y), reachX, reachZ,
                this.radiusOffsets.branch().sample(random), highestLeafY);
        }

        return attachments;
    }

    private void growBranch(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> setter,
        RandomSource random,
        TreeConfiguration config,
        List<FoliagePlacer.FoliageAttachment> attachments,
        BlockPos start,
        int reachX,
        int reachZ,
        int radiusOffset,
        int highestLeafY
    ) {
        Function<BlockState, BlockState> alongX = axisState(Direction.Axis.X);
        Function<BlockState, BlockState> alongZ = axisState(Direction.Axis.Z);
        Function<BlockState, BlockState> alongY = axisState(Direction.Axis.Y);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int steps = Math.max(Math.abs(reachX), Math.abs(reachZ));
        int x = 0;
        int z = 0;
        int y = start.getY();

        // 单格轴向斜线：每一格只动 x 或 z，连线不会出现斜向的断口
        for (int step = 1; step <= steps; step++) {
            int targetX = (int) Math.round((double) reachX * step / steps);
            int targetZ = (int) Math.round((double) reachZ * step / steps);

            while (x != targetX) {
                y = this.riseAt(random, start, x, z, y, highestLeafY, cursor, level, setter, config, alongY);
                x += Integer.signum(targetX - x);
                placeLog(level, setter, random, cursor.set(start.getX() + x, y, start.getZ() + z), config, alongX);
            }

            while (z != targetZ) {
                y = this.riseAt(random, start, x, z, y, highestLeafY, cursor, level, setter, config, alongY);
                z += Integer.signum(targetZ - z);
                placeLog(level, setter, random, cursor.set(start.getX() + x, y, start.getZ() + z), config, alongZ);
            }
        }

        attachments.add(new FoliagePlacer.FoliageAttachment(cursor.immutable().above(), radiusOffset, false));
    }

    /** 抬高一格时先在原位补一根竖木，枝才有连着的台阶而不是斜向断开；高度不超过树干顶 */
    private int riseAt(
        RandomSource random,
        BlockPos start,
        int x,
        int z,
        int y,
        int highestLeafY,
        BlockPos.MutableBlockPos cursor,
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> setter,
        TreeConfiguration config,
        Function<BlockState, BlockState> alongY
    ) {
        if (random.nextFloat() >= this.branchRiseChance || y + 1 > highestLeafY) {
            return y;
        }

        placeLog(level, setter, random, cursor.set(start.getX() + x, y + 1, start.getZ() + z), config, alongY);
        return y + 1;
    }

    private static Function<BlockState, BlockState> axisState(Direction.Axis axis) {
        return state -> state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, axis) : state;
    }
}
