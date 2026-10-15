package com.dawnduskterminal.block;

import com.dawnduskterminal.registry.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * The liquid block for "enthu_fluid" (热情的暮光). It borrows minecraft:lava's block properties but
 * not its destructive behaviour: it does not ignite entities, does not spread fire, and does not
 * clear neighbouring plants.
 *
 * <p>What it <em>does</em> take from lava is the contact hazard: entities standing in it take 6
 * points of damage per hit (respecting the vanilla hurt invulnerability window, exactly like lava's
 * 4 points), using the {@code enhtu_fluid} damage type so the death message is the mod's own.
 *
 * <p>Flow scheduling and the fluid contact reactions are inherited from {@link LiquidBlock}: the base
 * block schedules its own spread tick and runs {@code FluidInteractionRegistry} on place / neighbour
 * change, exactly like vanilla water and lava.
 */
public class ScorchingTwilightBlock extends LiquidBlock {
    public ScorchingTwilightBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide && entity instanceof LivingEntity living) {
            living.hurt(living.damageSources().source(ModDamageTypes.ENTHU_FLUID), 6.0F);
        }
    }
}
