package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModPaintingVariants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * 恶魂画 #1：3 格宽 2 格高，只认 {@code dawnduskterminal:painting_ghast_1} 这一个变体
 *
 * <p>和 {@code minecraft:painting} 是两条互不相干的实体类型，互换的三种路子都堵死了：
 * <ul>
 *   <li>原版画物品只从 {@code #minecraft:painting_variant/placeable} 里挑变体（见
 *       {@code Painting#create}），我们的变体不在那个标签里，原版物品放不出这幅画</li>
 *   <li>我们的物品只 {@code new} 这一个实体类型，放不出原版的任何一幅画</li>
 *   <li>存档 NBT 与 {@code /summon} 这条侧门由下面的 {@link #setVariant} 与
 *       {@code PaintingVariantGuardMixin} 两头堵：本实体强制用自己的变体，原版画实体不许用</li>
 * </ul>
 *
 * <p>敲碎时掉的是 {@link ModItems#PAINTING_GHAST_1}，不是一个原版画物品
 */
public class GhastPaintingEntity extends Painting {
    public GhastPaintingEntity(EntityType<? extends Painting> type, Level level) {
        super(type, level);
        this.setVariant(ModPaintingVariants.holder(level.registryAccess()));
    }

    /**
     * 摆放用。原版 {@code Painting} 的带坐标构造写死了 {@code EntityType.PAINTING}，
     * 没法拿模组实体类型走那条路，所以照 {@code HangingEntity} 的写法自己接：
     * {@code pos} 是 {@code BlockAttachedEntity} 的 protected 字段，
     * {@code setDirection} 会以它为锚算包围盒并把实体挪到画面上
     */
    public GhastPaintingEntity(EntityType<? extends Painting> type, Level level, BlockPos pos, Direction direction) {
        this(type, level);
        this.pos = pos;
        this.setDirection(direction);
    }

    /**
     * 变体固定成这一幅：构造、读档、数据同步带来的任何值都会被这里换掉，
     * 于是「这幅画只对应唯一物品」在实体层面也成立
     */
    @Override
    public void setVariant(Holder<PaintingVariant> variant) {
        super.setVariant(ModPaintingVariants.holder(this.registryAccess()));
    }

    @Override
    public void dropItem(Entity breaker) {
        if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (breaker instanceof Player player && player.hasInfiniteMaterials()) {
                return;
            }

            this.spawnAtLocation(ModItems.PAINTING_GHAST_1.get());
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.PAINTING_GHAST_1.get());
    }
}
