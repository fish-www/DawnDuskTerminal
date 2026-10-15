package com.dawnduskterminal.world;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Adds the terminator offset to the chrono dimension's temperature noise, so biome selection sees a
 * warmer day side and a colder night side.
 *
 * <p>The offset is {@link ChronoLineState#serverLine()}'s {@link ChronoLine#param(double, double)}:
 * the projection of the block position onto the line's normal, scaled by {@code maxDistance} and
 * clamped to {@code [-1, 1]}, then multiplied by {@link #AMPLITUDE}. That gives 0 at the origin,
 * &plusmn;{@value #AMPLITUDE} at ~{@code maxDistance} blocks along the normal, and no change along the
 * direction perpendicular to the normal - exactly the terminator gradient the requirement asks for.
 */
public final class ChronoTemperature {
    private static final double AMPLITUDE = 1.5;

    private ChronoTemperature() {}

    public static double offset(int x, int z) {
        return ChronoLineState.serverLine().param(x, z) * AMPLITUDE;
    }

    /**
     * Wraps a temperature {@link DensityFunction} so every {@link #compute} result is shifted by the
     * terminator offset.
     *
     * <p>{@code mapAll} must recurse into the delegate: the caller in {@code NoiseChunk} chains this
     * wrapper with {@code .mapAll(this::wrap)} to resolve the temperature's noise holder into a real
     * {@code NormalNoise}, and a non-recursing wrapper would leave the noise unresolved and every
     * biome lookup would come back wrong.
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
