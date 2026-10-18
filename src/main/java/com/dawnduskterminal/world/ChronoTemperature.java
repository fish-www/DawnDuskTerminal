package com.dawnduskterminal.world;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * 给晨昏线维度的温度噪声加上晨昏线偏移，让群系选择看到更暖的昼侧与更冷的夜侧
 *
 * <p>包装点在 {@code NoiseChunk.cachedClimateSampler}（{@code NoiseChunkTemperatureMixin}），因此只有区块
 * 落盘的群系带偏移；结构判定用的 {@code RandomState.sampler()} 不带，需要 {@code HollowTreeSurfaceBiomeMixin}
 * 那条复核
 *
 * <p>偏移量就是 {@link ChronoLineState#serverLine()} 的 {@link ChronoLine#param(double, double)}：把方块
 * 坐标投影到线的法向上，除以 {@code maxDistance} 后钳到 {@code [-1, 1]}，再乘 {@link #AMPLITUDE}。于是
 * 原点处为 0，沿法向约 {@code maxDistance} 格处为 &plusmn;{@value #AMPLITUDE}，垂直于法向的方向完全
 * 不变，正好是晨昏线该有的渐变
 */
public final class ChronoTemperature {
    private static final double AMPLITUDE = 1.5;

    private ChronoTemperature() {}

    public static double offset(int x, int z) {
        return ChronoLineState.serverLine().param(x, z) * AMPLITUDE;
    }

    /**
     * 包住一个温度 {@link DensityFunction}，让所有 {@link #compute} 结果都加上晨昏线偏移
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
            return this.delegate.compute(context) + offset(context.blockX(), context.blockZ());
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return new Wrapped(this.delegate.mapAll(visitor));
        }

        @Override
        public double minValue() {
            return this.delegate.minValue() - AMPLITUDE;
        }

        @Override
        public double maxValue() {
            return this.delegate.maxValue() + AMPLITUDE;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException(
                "ChronoTemperature is a runtime wrapper and is not serializable");
        }
    }
}
