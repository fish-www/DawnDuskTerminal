package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * 伤害类型键。条目本身由数据包驱动（{@code data/dawnduskterminal/damage_type/*.json}），这里只放代码里
 * 构建 {@link net.minecraft.world.damagesource.DamageSource} 用到的键
 */
public final class ModDamageTypes {
    /** 「热情的暮光」(enthu_fluid) 的接触伤害，每次 6 点，自定义死亡信息 */
    public static final ResourceKey<DamageType> ENTHU_FLUID =
        ResourceKey.create(Registries.DAMAGE_TYPE, DawnDuskTerminal.id("enthu_fluid"));

    /** 「稳定」(stability) 的接触伤害，待在传送门流体里每 2 秒 2 点 */
    public static final ResourceKey<DamageType> STABILITY =
        ResourceKey.create(Registries.DAMAGE_TYPE, DawnDuskTerminal.id("stability"));

    private ModDamageTypes() {}
}
