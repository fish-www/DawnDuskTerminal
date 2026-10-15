package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * Damage type keys. The entries themselves are datapack-driven
 * ({@code data/dawnduskterminal/damage_type/*.json}); this only holds the keys used to build the
 * {@link net.minecraft.world.damagesource.DamageSource} in code.
 */
public final class ModDamageTypes {
    /** The "enthu_fluid" (热情的暮光) contact damage - 6 points per hit, custom death message. */
    public static final ResourceKey<DamageType> ENTHU_FLUID =
        ResourceKey.create(Registries.DAMAGE_TYPE, DawnDuskTerminal.id("enthu_fluid"));

    /** The "stability" (稳定) contact damage - 2 points per 2s while in portal_fluid. */
    public static final ResourceKey<DamageType> STABILITY =
        ResourceKey.create(Registries.DAMAGE_TYPE, DawnDuskTerminal.id("stability"));

    private ModDamageTypes() {}
}
