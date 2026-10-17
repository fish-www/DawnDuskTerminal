package com.dawnduskterminal.item;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 绯色钼护甲（T2）
 *
 * <p>四件穿齐时持续给生命恢复 I。实现方式是每个部件在自己的 {@link #inventoryTick} 里检查整套，
 * 而不是挂一个全局的实体刻事件：检查本身就是四次槽位读取，放在这里最省事也最好读
 *
 * <p>每 10 tick 续一次 60 tick 的效果，留够了余量，中途脱掉一件最多 0.5 秒就断
 */
public class ScarletMolybdArmorItem extends ArmorItem {
    private static final int REFRESH_INTERVAL = 10;
    private static final int EFFECT_TICKS = 60;

    public ScarletMolybdArmorItem(Holder<ArmorMaterial> material, ArmorItem.Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        if (player.tickCount % REFRESH_INTERVAL != 0 || !hasFullSet(player)) {
            return;
        }
        // ambient 为 true，粒子少一点；showIcon 保留，玩家该看到自己的状态
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EFFECT_TICKS, 0, true, true));
    }

    /** 四个槽位都是本套部件才算穿齐 */
    public static boolean hasFullSet(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[] {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(player.getItemBySlot(slot).getItem() instanceof ScarletMolybdArmorItem)) {
                return false;
            }
        }
        return true;
    }
}
