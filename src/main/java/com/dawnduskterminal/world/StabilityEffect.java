package com.dawnduskterminal.world;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 稳定（Stability）：一个纯标记效果。接触不稳定流体（portal_fluid）时，传送与否、以及受伤
 * （每 2 秒 2 点、到达音效）都由 {@code PortalBlock#entityInside} 统一判定，本效果只决定方向：
 * 玩家有「稳定」→ 不传送；非玩家实体有「稳定」→ 传送。侧边栏贴图（
 * textures/mob_effect/stability.png）由后续提供。
 */
public class StabilityEffect extends MobEffect {
    public StabilityEffect() {
        super(MobEffectCategory.NEUTRAL, 0x7F159E);
    }
}
