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
 * 「热情的暮光」(enthu_fluid) 的流体方块
 *
 * 借用 minecraft:lava 的方块属性，但不继承它的破坏行为：不点燃生物，不蔓延火焰，不清除旁边的植物
 *
 * <p>从岩浆那里留下的只有接触伤害：站在里面的生物每次受击窗口受到 6 点伤害（和岩浆的 4 点一样，
 * 遵守原版受击无敌窗口），伤害类型用 {@code enthu_fluid}，死亡信息是本模组自己的
 *
 * <p>流动调度与流体接触反应都继承自 {@link LiquidBlock}：基类方块自己排扩散 tick，并在放置与
 * 邻居变化时跑 {@code FluidInteractionRegistry}，和原版的水与岩浆一致
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
