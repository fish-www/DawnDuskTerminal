package com.dawnduskterminal.world;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * 把温度改写成晨昏线 {@code param} 的分段函数，让群系按极昼 昼区 热带 温带 寒带 夜区 极夜七段分布
 *
 * <p>包装点在 {@code NoiseChunk.cachedClimateSampler}（{@code NoiseChunkTemperatureMixin}），因此只有区块
 * 落盘的群系带这个映射；结构判定用的 {@code RandomState.sampler()} 不带，需要 {@code HollowTreeSurfaceBiomeMixin}
 * 那条复核
 *
 * <p>{@code param} 是 {@link ChronoLine#param(double, double)}：方块坐标投影到线的法向上，除以
 * {@code maxDistance} 后钳到 {@code [-1, 1]}。六个断点 {@link #BREAKPOINTS} 把 {@code param} 切成七段，
 * 每段线性映射到原版气候表的一段温度（{@link #BAND_TEMPERATURE}），段与段的端点温度接得上，所以整体连续。
 * 于是每一带只对应原版的一档温度，热带的温度上界够不到针叶林那一档，寒带也够不到雪原那一档
 *
 * <p>叠在映射之上的是原版温度噪声乘 {@link #SPREAD}：它的作用只是让分带边界弯折，而不是决定分带。
 * 带内温度已经被映射钉死在一档里，噪声再大也只在档内晃，只有边界两侧各有约 {@code SPREAD} 宽的过渡
 *
 * <p>{@code |param| >= 0.8} 的两个极带继续往外推：{@code param} 走到 {@code ±1} 时温度到
 * {@link #POLAR_TEMPERATURE}。多出来的这一段正好是原版气候表没覆盖的地盘，{@code PaletteBiomeSource} 的
 * {@code extra} 在那儿放了极昼极夜的群系，两边的断点必须对齐，否则极带群系永远选不到
 */
public final class ChronoTemperature {
    /** 极夜侧边界，再往外温度开始越过原版值域 */
    public static final double POLAR_NIGHT = -0.8D;
    /** 夜区与寒带的边界 */
    public static final double NIGHT_ZONE = -0.6D;
    /** 寒带与温带的边界 */
    public static final double COLD_ZONE = -0.3D;
    /** 温带与热带的边界 */
    public static final double TROPIC_ZONE = 0.3D;
    /** 热带与昼区的边界 */
    public static final double DAY_ZONE = 0.6D;
    /** 极昼侧边界，再往外温度开始越过原版值域 */
    public static final double POLAR_DAY = 0.8D;

    /**
     * 极带到线端的温度，{@code param} 被 {@link ChronoLine} 钳在 {@code ±1}，所以温度的上界就是这里
     *
     * <p>气候表的条款只收到 {@code ±1}（{@code Climate.Parameter.CODEC} 也只认 {@code -2 ~ 2}），
     * 超出去这一段正是自建极带群系的地盘，写成一整段而不再是原来的 {@code ±1} 常量
     */
    public static final double POLAR_TEMPERATURE = 2.0D;

    /** 分带断点，与 {@link #BAND_TEMPERATURE} 一一对应 */
    private static final double[] BREAKPOINTS = {
        POLAR_NIGHT, NIGHT_ZONE, COLD_ZONE, TROPIC_ZONE, DAY_ZONE, POLAR_DAY
    };

    /**
     * 断点处的温度，取自原版气候表的温度档边界
     *
     * <p>{@code -1.00 ~ -0.45} 冻结档（雪原 冰川），{@code -0.45 ~ -0.15} 寒带，{@code -0.15 ~ 0.20} 温带，
     * {@code 0.20 ~ 0.55} 热带，{@code 0.55 ~ 1.00} 热档（昼区）
     */
    private static final double[] BAND_TEMPERATURE = {
        -1.0D, -0.45D, -0.15D, 0.20D, 0.55D, 1.0D
    };

    /** 噪声在映射之后叠加的比例：只负责让分带边界弯折，不改变每一带对应的温度档 */
    public static final double SPREAD = 0.08D;

    private ChronoTemperature() {}

    /**
     * {@code param} 到温度的纯函数，不带噪声
     *
     * <p>抽出来是为了能在游戏测试里直接断言断点：温度档一旦错位，某个带里就会冒出别的带的群系
     */
    public static double bandTemperature(double param) {
        // 极带继续按同样的斜率往外推，跨过 ±1 就进了 extra 那几条的地盘
        if (param >= POLAR_DAY) {
            double ratio = Mth.clamp((param - POLAR_DAY) / (1.0D - POLAR_DAY), 0.0D, 1.0D);
            return Mth.lerp(ratio, BAND_TEMPERATURE[BAND_TEMPERATURE.length - 1], POLAR_TEMPERATURE);
        }
        if (param <= POLAR_NIGHT) {
            double ratio = Mth.clamp((POLAR_NIGHT - param) / (POLAR_NIGHT + 1.0D), 0.0D, 1.0D);
            return Mth.lerp(ratio, BAND_TEMPERATURE[0], -POLAR_TEMPERATURE);
        }
        for (int i = 1; i < BREAKPOINTS.length; i++) {
            if (param < BREAKPOINTS[i]) {
                double ratio = (param - BREAKPOINTS[i - 1]) / (BREAKPOINTS[i] - BREAKPOINTS[i - 1]);
                return Mth.lerp(ratio, BAND_TEMPERATURE[i - 1], BAND_TEMPERATURE[i]);
            }
        }
        return BAND_TEMPERATURE[BAND_TEMPERATURE.length - 1];
    }

    /**
     * 包住一个温度 {@link DensityFunction}，让所有采样结果都改成 {@code param} 的分段映射
     *
     * <p>{@code mapAll} 必须递归进 delegate：{@code NoiseChunk} 用 {@code .mapAll(this::wrap)} 把
     * router 上的函数重新映射成区块内的噪声，好把温度的噪声 holder 解析成真正的 {@code NormalNoise}；
     * 包装器不递归的话噪声解析不了，每次群系查询都会得到错误结果
     */
    public static DensityFunction wrap(DensityFunction temperature) {
        return new Wrapped(temperature);
    }

    private static final class Wrapped implements DensityFunction.SimpleFunction {
        private final DensityFunction delegate;

        Wrapped(DensityFunction delegate) {
            this.delegate = delegate;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            double param = ChronoLineState.serverLine().param(context.blockX(), context.blockZ());
            double band = bandTemperature(param);
            // 极带不叠噪声：噪声一压就可能掉回原版那一行，极带边界会变成一条毛边，
            // 而 extra 里那几条的跨度是从 ±1 起算的
            if (band <= -1.0D || band >= 1.0D) {
                return band;
            }
            return band + this.delegate.compute(context) * SPREAD;
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return new Wrapped(this.delegate.mapAll(visitor));
        }

        @Override
        public double minValue() {
            return -POLAR_TEMPERATURE;
        }

        @Override
        public double maxValue() {
            return POLAR_TEMPERATURE;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException(
                "ChronoTemperature is a runtime wrapper and is not serializable");
        }
    }
}
