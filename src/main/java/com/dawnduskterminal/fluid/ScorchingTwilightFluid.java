package com.dawnduskterminal.fluid;

import com.dawnduskterminal.registry.ModScorchingTwilight;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * The "enthu_fluid" (热情的暮光) fluid. It reuses minecraft:lava's *properties* but deliberately drops
 * every lava-like *behaviour*:
 *
 * <ul>
 *   <li><b>no burning entities</b> - {@code ScorchingTwilightBlock.entityInside} no longer calls
 *       {@code lavaHurt()}. (Note vanilla only ignites entities inside {@code NeoForgeMod.LAVA_TYPE},
 *       and this fluid has its own {@link FluidType}, so there is no second burn path.)</li>
 *   <li><b>no igniting blocks</b> - vanilla's fire-spreading {@code LavaFluid.randomTick} is not
 *       overridden here, so the base {@code Fluid.randomTick} no-op applies. Random ticking is still
 *       enabled ({@link #isRandomlyTicking()}) because the liquid block relies on it to run
 *       {@code FluidReactions}.</li>
 *   <li><b>no lava particles</b> - no {@code ParticleTypes.LAVA} / {@code LAVA_POP} /
 *       {@code LAVA_AMBIENT} animation, no {@code DRIPPING_LAVA} drip particle, and no
 *       {@code LevelEvent.LAVA_FIZZ} block-destroy effect.</li>
 * </ul>
 */
public abstract class ScorchingTwilightFluid extends FlowingFluid {
    public static final float MIN_LEVEL_CUTOFF = 0.44444445F;

    @Override
    public FluidType getFluidType() {
        return ModScorchingTwilight.TYPE.value();
    }

    @Override
    public Fluid getFlowing() {
        return ModScorchingTwilight.FLOWING.get();
    }

    @Override
    public Fluid getSource() {
        return ModScorchingTwilight.SOURCE.get();
    }

    @Override
    public Item getBucket() {
        return ModScorchingTwilight.BUCKET.get();
    }

    @Override
    @Nullable
    public ParticleOptions getDripParticle() {
        return null;
    }

    /**
     * Plays lava's ambient soundscape near the player without the lava particles. This is the audio
     * half of {@code LavaFluid#animateTick}, which the fluid deliberately drops (see the class doc).
     */
    @Override
    public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).isAir() && !level.getBlockState(above).isSolidRender(level, above)) {
            if (random.nextInt(100) == 0) {
                double x = (double) pos.getX() + random.nextDouble();
                double y = (double) pos.getY() + 1.0;
                double z = (double) pos.getZ() + random.nextDouble();
                level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
            if (random.nextInt(200) == 0) {
                level.playLocalSound((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(),
                    SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
        }
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        // Intentionally empty. Vanilla lava raises LevelEvent.LAVA_FIZZ (1501) here, which the client
        // turns into the lava-extinguish sound plus smoke particles - both are lava effects this fluid
        // must not produce. FlowingFluid declares this method abstract, so it cannot simply be dropped.
    }

    @Override
    public int getSlopeFindDistance(LevelReader level) {
        return level.dimensionType().ultraWarm() ? 4 : 2;
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return ModScorchingTwilight.BLOCK.get().defaultBlockState()
            .setValue(LiquidBlock.LEVEL, Integer.valueOf(getLegacyLevel(state)));
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == ModScorchingTwilight.SOURCE.get() || fluid == ModScorchingTwilight.FLOWING.get();
    }

    @Override
    public int getDropOff(LevelReader level) {
        return level.dimensionType().ultraWarm() ? 1 : 2;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid,
                                     Direction direction) {
        return state.getHeight(level, pos) >= MIN_LEVEL_CUTOFF && fluid.is(FluidTags.WATER);
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return level.dimensionType().ultraWarm() ? 10 : 30;
    }

    @Override
    public int getSpreadDelay(Level level, BlockPos pos, FluidState oldState, FluidState newState) {
        int delay = this.getTickDelay(level);
        if (!oldState.isEmpty() && !newState.isEmpty()
            && !oldState.getValue(FALLING) && !newState.getValue(FALLING)
            && newState.getHeight(level, pos) > oldState.getHeight(level, pos)
            && level.getRandom().nextInt(4) != 0) {
            delay *= 4;
        }
        return delay;
    }

    @Override
    protected boolean canConvertToSource(Level level) {
        return level.getGameRules().getBoolean(GameRules.RULE_LAVA_SOURCE_CONVERSION);
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_LAVA);
    }

    public static class Source extends ScorchingTwilightFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    public static class Flowing extends ScorchingTwilightFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
