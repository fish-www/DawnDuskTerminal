package com.dawnduskterminal.item;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.CarminiteBallEntity;
import com.dawnduskterminal.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * 砷铅铁钼反应权杖
 *
 * <p>近战按单手武器算（伤害加 7、攻速减 3，也就是每秒 1 次），右键发射一枚
 * {@link CarminiteBallEntity}，消耗 1 点耐久并进入 1 秒冷却
 *
 * <p>源 mod 的发条逻辑挂在物品的 {@code use} 里但拆成一个巨大的过程方法，里面还带着附魔等级、
 * 生存模式判断和实体搜索。这里只保留武器本身该有的部分，附魔加成不做：GeckoLib 之外的附魔
 * 加成要逐条读原版附魔，收益不值得现在做
 */
public class CarminiteStaffItem extends Item {
    private static final double ATTACK_DAMAGE = 7.0D;
    private static final double ATTACK_SPEED = -3.0D;
    private static final int COOLDOWN_TICKS = 20;
    private static final float VELOCITY = 1.5F;
    private static final float INACCURACY = 1.0F;

    public CarminiteStaffItem(Properties properties) {
        super(properties);
    }

    /** 单手武器的属性表，1.21.1 通过 {@link Item.Properties#attributes} 挂到物品上 */
    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(DawnDuskTerminal.id("staff_attack_damage"), ATTACK_DAMAGE,
                    AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                new AttributeModifier(DawnDuskTerminal.id("staff_attack_speed"), ATTACK_SPEED,
                    AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            CarminiteBallEntity ball = new CarminiteBallEntity(level, player, stack);
            ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, VELOCITY, INACCURACY);
            level.addFreshEntity(ball);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.CARMINITE_STAFF_SHOOT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
